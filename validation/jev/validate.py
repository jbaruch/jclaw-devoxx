#!/usr/bin/env python3
"""Validate live Jev decisions against the demo's read-only MCP calendar.

No drafting, sending, history writes, alternate model or offline inference fallback.
"""
import argparse
import concurrent.futures
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import select
import shlex
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
ENDPOINT = "https://api.typesafe.ai/v1/systemone"
SENTINELS = {"NO_MATCH", "AMBIGUOUS"}


def load(name):
    directory = HERE if name == "cases.json" else ROOT / "domain/src/main/resources/jclaw/jev"
    return json.loads((directory / name).read_text())


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def credential():
    for name in ("TYPESAFE_API_KEY", "JEV_API_KEY"):
        if os.environ.get(name):
            return os.environ[name]
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text().splitlines():
            words = shlex.split(line, comments=True)
            if words and words[0] == "export":
                words = words[1:]
            if len(words) == 1 and "=" in words[0]:
                name, value = words[0].split("=", 1)
                if name in ("TYPESAFE_API_KEY", "JEV_API_KEY") and value:
                    return value
    raise RuntimeError("Live validation needs TYPESAFE_API_KEY in the environment or demo .env")


def calendar():
    """Read the same packaged stdio MCP server used on stage; no write tools."""
    java = "java"
    java_home = Path("/usr/libexec/java_home")
    if java_home.exists():
        home = subprocess.check_output([str(java_home), "-F", "-v", "21"], text=True).strip()
        java = str(Path(home) / "bin/java")
    jar = ROOT / "mocks/build/libs/calendar-mcp.jar"
    if not jar.exists():
        raise RuntimeError("Build the mock first: ./gradlew :mocks:mcpJars")
    proc = subprocess.Popen([java, "-jar", str(jar)], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0)
    buffer = bytearray()

    def send(value):
        proc.stdin.write((json.dumps(value) + "\n").encode())
        proc.stdin.flush()

    def receive(identifier):
        end = time.monotonic() + 20
        while time.monotonic() < end:
            if b"\n" not in buffer:
                readable, _, _ = select.select([proc.stdout], [], [], max(0, end - time.monotonic()))
                if not readable:
                    break
                chunk = os.read(proc.stdout.fileno(), 65536)
                if not chunk:
                    raise RuntimeError("Calendar MCP closed before its response")
                buffer.extend(chunk)
            while b"\n" in buffer:
                line, _, remainder = buffer.partition(b"\n")
                buffer[:] = remainder
                response = json.loads(line)
                if response.get("id") == identifier:
                    if "error" in response:
                        raise RuntimeError("Calendar MCP returned a protocol error")
                    return response["result"]
        raise RuntimeError("Calendar MCP timed out")

    try:
        send({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
            "protocolVersion": "2025-11-25", "capabilities": {},
            "clientInfo": {"name": "jclaw-jev-validation", "version": "1"}}})
        receive(1)
        send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        send({"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {
            "name": "getCalendar", "arguments": {}}})
        result = receive(2)
        if result.get("isError"):
            raise RuntimeError("Calendar read failed")
        texts = [c["text"] for c in result["content"] if c.get("type") == "text"]
        if len(texts) != 1:
            raise RuntimeError("Calendar response is ambiguous")
        events = json.loads(texts[0])
        assert len(events) == 4 and len({e["id"] for e in events}) == 4
        return events
    finally:
        proc.kill()
        proc.wait(timeout=5)
        proc.stdin.close()
        proc.stdout.close()


def labelled_events(events):
    """Code computes weekday/date labels; Jev does no date arithmetic."""
    return [dict(e, dateLabel=dt.datetime.fromisoformat(e["start"]).strftime("%A %B %d %Y %H:%M"))
            for e in events]


def request(case, events, questions, policy):
    current = [e for e in case.get("calendar", events) if e["id"] not in case.get("omitEventIds", [])]
    current = current + case.get("extraEvents", [])
    state = {"userMessage": case["message"], "conversation": case.get("conversation", []),
             "calendar": labelled_events(current),
             "scenario": "Fictional rehearsal. Today is Friday October 2, 2026; the next Tuesday is October 6, 2026."}
    criteria = {e["id"]: {k: v for k, v in e.items() if k != "id"} for e in state["calendar"]}
    if SENTINELS & criteria.keys():
        raise ValueError("Calendar ID collides with a reserved decision option")
    criteria.update({"NO_MATCH": "ZERO records match ALL specified details. This includes an explicit organizer or date/weekday absent from matching records, no identified obligation, or no request for a decline plan. A matching title does not override a conflicting organizer or day.",
                     "AMBIGUOUS": "Two or more records match ALL specified details equally; insufficient evidence to choose one; or multiple targets are requested."})
    return {"model": policy["model"], "state": state,
            "questions": dict(questions, event=dict(questions["event"], criteria=criteria))}


def valid_choice(answer, options):
    if not isinstance(answer, dict) or answer.get("type") != "choice" or answer.get("choice") not in options:
        raise ValueError("Missing or invalid Choice answer")
    probabilities = answer.get("probabilities", {})
    values = list(probabilities.values())
    confidence = answer.get("confidence")
    if set(probabilities) != set(options) or not all(type(v) in (int, float) and math.isfinite(v) and 0 <= v <= 1 for v in values):
        raise ValueError("Invalid Choice probability distribution")
    if abs(sum(values) - 1) > 0.02 or type(confidence) not in (int, float) or not math.isfinite(confidence) or not 0 <= confidence <= 1:
        raise ValueError("Invalid Choice confidence or probability total")
    if probabilities[answer["choice"]] + 0.001 < max(values):
        raise ValueError("Choice is not a maximum-probability option")
    return answer


def decide(response, payload, policy):
    answers = response["answers"]
    intent = valid_choice(answers.get("intent"), payload["questions"]["intent"]["criteria"])
    if intent["confidence"] < policy["intentConfidenceMin"]:
        return {"path": "ASK", "eventId": None}
    if intent["choice"] == "CHAT":
        # The speculative event answer is unused, including its confidence.
        return {"path": "CHAT", "eventId": None}
    event = valid_choice(answers.get("event"), payload["questions"]["event"]["criteria"])
    if event["choice"] in SENTINELS or event["confidence"] < policy["eventConfidenceMin"]:
        return {"path": "ASK", "eventId": None}
    return {"path": "DECLINE", "eventId": event["choice"]}


def call(payload, key, context):
    req = urllib.request.Request(ENDPOINT, json.dumps(payload).encode(), {
        "Authorization": "Bearer " + key, "Content-Type": "application/json"})
    began = time.monotonic()
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=20, context=context) as response:
                result = json.load(response)
            if result.get("model") != payload["model"]:
                raise ValueError("The response does not identify the pinned live Jev model")
            return result, round((time.monotonic() - began) * 1000), attempt
        except urllib.error.HTTPError as error:
            if error.code not in (429, 529) or attempt == 2:
                raise RuntimeError("TypeSafe HTTP " + str(error.code)) from None
            time.sleep(min(5, 2 ** attempt))
    raise RuntimeError("TypeSafe did not return an answer")


def boundary_checks():
    """Local policy checks cannot count as live model validation."""
    policy = load("policy.json")
    intent_options = load("questions.json")["intent"]["criteria"]
    event_options = {"real-event": "Actual record", "NO_MATCH": "Absent", "AMBIGUOUS": "Ambiguous"}
    payload = {"questions": {"intent": {"criteria": intent_options}, "event": {"criteria": event_options}}}

    def answer(chosen, options):
        return {"type": "choice", "choice": chosen, "confidence": 1,
                "probabilities": {k: int(k == chosen) for k in options}}

    response = {"answers": {"intent": answer("CHAT", intent_options)}}
    assert decide(response, payload, policy)["path"] == "CHAT"
    response["answers"]["intent"] = answer("EXCUSE_REQUEST", intent_options)
    for sentinel in SENTINELS:
        response["answers"]["event"] = answer(sentinel, event_options)
        assert decide(response, payload, policy)["path"] == "ASK"
    response["answers"]["event"] = answer("real-event", event_options)
    assert decide(response, payload, policy) == {"path": "DECLINE", "eventId": "real-event"}
    response["answers"]["event"]["confidence"] = 0.1
    assert decide(response, payload, policy)["path"] == "ASK"
    for invalid in ({}, {"type": "choice", "choice": "invented"},
                    dict(answer("real-event", event_options), confidence=float("nan"))):
        response["answers"]["event"] = invalid
        try:
            decide(response, payload, policy)
        except ValueError:
            pass
        else:
            raise AssertionError("Malformed answer accepted")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Check real MCP and policy locally; no inference")
    parser.add_argument("--split", choices=("development", "holdout", "all"), default="all")
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--output", type=Path, default=ROOT / "build/jev-validation/report.json")
    args = parser.parse_args()
    if not 1 <= args.repeat <= 5:
        parser.error("--repeat must be between 1 and 5")
    boundary_checks()
    events = calendar()
    questions, policy = load("questions.json"), load("policy.json")
    cases = load("cases.json")
    assert len({c["id"] for c in cases}) == len(cases)
    selected = [c for c in cases if args.split == "all" or c["split"] == args.split]
    payloads = [(case, request(case, events, questions, policy)) for case in selected]
    base = {"model": policy["model"], "endpoint": ENDPOINT,
            "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "split": args.split, "repeat": args.repeat, "caseCount": len(selected),
            "contractSha256": digest({"questions": questions, "policy": policy}),
            "casesSha256": digest(cases), "calendarSha256": digest(events),
            "calendarSource": "Actual calendar-mcp.jar getCalendar; read-only",
            "policyBoundaryChecks": "PASS"}
    if args.dry_run:
        for case, payload in payloads:
            assert case["expected"]["path"] in {"CHAT", "DECLINE", "ASK"}
            if case["expected"]["path"] == "DECLINE":
                assert case["expected"]["eventId"] in payload["questions"]["event"]["criteria"]
        print(json.dumps(dict(base, status="PREPARED", live=False), indent=2))
        return 0
    key = credential()
    try:
        import certifi
        context = ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        context = ssl.create_default_context()

    def evaluate(item):
        case, payload, repetition = item
        row = {"id": case["id"], "split": case["split"], "repetition": repetition,
               "expected": case["expected"], "request": payload}
        try:
            response, latency, retries = call(payload, key, context)
            observed = decide(response, payload, policy)
            return dict(row, response=response, latencyMs=latency, retries=retries,
                        observed=observed, passed=observed == case["expected"])
        except (ValueError, KeyError, RuntimeError, urllib.error.URLError, TimeoutError):
            # Do not print exceptions containing credential-bearing request objects.
            return dict(row, error="Live service or response contract failed", passed=False)

    jobs = [(c, p, repetition) for repetition in range(1, args.repeat + 1) for c, p in payloads]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        rows = list(executor.map(evaluate, jobs))
    for row in rows:
        print(("PASS" if row["passed"] else "FAIL") + " " + row["id"] + " " +
              json.dumps(row.get("observed", {"error": row["error"]}) if "error" in row else row["observed"]))
    passed = sum(row["passed"] for row in rows)
    errors = sum("error" in row for row in rows)
    latencies = sorted(row["latencyMs"] for row in rows if "latencyMs" in row)
    result = dict(base, live=True, status="PASS" if passed == len(rows) else "FAIL", passed=passed,
                  total=len(rows), errors=errors, results=rows,
                  medianLatencyMs=latencies[len(latencies)//2] if latencies else None,
                  inputTokens=sum(r.get("response", {}).get("usage", {}).get("input_tokens", 0) for r in rows))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k != "results"}, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, AssertionError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
