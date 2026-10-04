#!/usr/bin/env python3
"""Build Viktor's demo-only snapshot from tracked working files; exclude local state."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/handoff/viktor-demo-handoff.zip"
PREFIX = "viktor-demo-handoff/"
ROOT_FILES = {
    "HANDOFF-LC4J.md", "build.gradle.kts", "settings.gradle.kts",
    "gradle.properties", "gradlew", "gradlew.bat",
}
DIRECTORIES = (
    "domain/", "mocks/", "tui/", "gradle/", "validation/",
    "skills/corporate-speak/", "memory/documents/", "app/src/",
)


def main():
    tracked = subprocess.check_output(
        ["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    files = {}
    for name in tracked:
        if not name or not (name in ROOT_FILES or name.startswith(DIRECTORIES)):
            continue
        if Path(name).name in {"PortBridge.kt", "PortBridgeTest.kt"}:
            continue
        if "build" in Path(name).parts or ".gradle" in Path(name).parts:
            raise RuntimeError("Unexpected generated tracked file: " + name)
        files[name] = (ROOT / name).read_bytes()
    if len([n for n in files if n.startswith("memory/documents/")]) != 3:
        raise RuntimeError("The handoff must contain exactly the three committed seed facts")
    for required in (
        "domain/src/main/resources/jclaw/jev/questions.json",
        "domain/src/main/resources/jclaw/workflow/policy.json",
        "validation/langchain4j/run.py",
    ):
        if required not in files:
            raise RuntimeError("Stage the current source before packaging: " + required)
    reference_head = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    changed = subprocess.run(
        ["git", "diff", "--quiet", "HEAD", "--", *files], cwd=ROOT).returncode
    if changed not in (0, 1):
        raise RuntimeError("Could not verify the handoff's source revision")
    settings = files["settings.gradle.kts"].decode()
    files["settings.gradle.kts"] = settings.replace(
        'include(":domain", ":mocks", ":tui", ":app")',
        'include(":domain", ":mocks", ":tui")').encode()
    for name in ("calendar-mcp.jar", "organizer-mcp.jar"):
        files["mock-jars/" + name] = (ROOT / "mocks/build/libs" / name).read_bytes()
    files["START-HERE.md"] = b"""# Viktor's LangChain4j demo handoff

Read HANDOFF-LC4J.md first. Build only the LangChain4j Agentic demo in your own
repository; Baruch owns the slides, narrative, shownotes and Port finale.

The source and downloadable release are at https://github.com/jbaruch/jclaw-devoxx.
MANIFEST.json identifies the exact reference commit and source hashes.
Share the fixtures, Jev questions/policy and workflow
policy: six shared refinements, seven candidates; Judge and Human are approvers
in the same Refine -> Judge -> Human loop. Identify runs once.

With JDK 21, run ./gradlew :mocks:mcpJars :tui:compileKotlin --console=plain.
The root build includes domain, mocks and tui. app/ contains Koog source/test
references; it is not configured as a runnable app here. Built stdio mock jars
are under mock-jars/. Exactly three fictional sent-history seeds are included.

See validation/jev/README.md for the frozen decisions and raw admission evidence.
See validation/langchain4j/README.md for the released native DecisionModel probe.
It compiles independently with ./gradlew -p validation/langchain4j classes.
Running its live holdout needs your own TYPESAFE_API_KEY in the environment or
a local .env. No credentials, rehearsal history or Port code is bundled.

Finish and validate the complete app before deriving seven round branches.
MANIFEST.json also reports whether the snapshot includes uncommitted source.
"""
    manifest = {
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "scope": "Viktor LangChain4j Agentic demo only",
        "referenceRepository": "https://github.com/jbaruch/jclaw-devoxx",
        "referenceHead": reference_head,
        "snapshotIncludesUncommittedChanges": changed == 1,
        "runtime": "JDK 21",
        "layoutNote": "Root Gradle includes domain, mocks and tui; app is reference source only.",
        "excluded": ["Port resources", "slides and narrative", "credentials",
                     "local runtime history", "vector cache", "agent policy/plugin files"],
        "files": [{"path": name, "bytes": len(data),
                   "sha256": hashlib.sha256(data).hexdigest()}
                  for name, data in sorted(files.items())],
    }
    files["MANIFEST.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(PREFIX + name)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if name == "gradlew" else 0o100644) << 16
            archive.writestr(info, data)
    checksum = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    OUTPUT.with_suffix(OUTPUT.suffix + ".sha256").write_text(
        f"{checksum}  {OUTPUT.name}\n")
    print(f"Created {OUTPUT}: {len(files)} files, {OUTPUT.stat().st_size / 2**20:.1f} MiB")


if __name__ == "__main__":
    main()
