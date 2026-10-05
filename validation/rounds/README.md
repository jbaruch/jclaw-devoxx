# Seven-round live Koog audit — October 4, 2026

Every round ran with the configured real providers. The final critic policy produced
usable results, including the four-record history that defeated the previous Judge.
[results.json](results.json) contains timings, typed review handoffs, actual backend
trace IDs/counts, receipts and independently read literal-message comparisons.
[transcripts](transcripts) are executed fictional rehearsal output, not invented
example responses. Human inputs were operated by the coding agent.

| Round / case | Observed outcome | Scenario elapsed |
|---|---|---:|
| 1 · chatbot | Text draft; follow-up honestly cannot send; no tools/history | 19.29 s / two turns |
| 2 · tools | Calendar + organizer reads; explicit send; matching receipt; no durable history | 35.17 s / two turns |
| 3 · memory | Seed reasons retrieved; follow-up sends; exact message stored | 68.00 s / two turns |
| 4 · skills | Restart preserves round-3 conversation; actual skill reads; rewrites at 11 and 4; no send | 51.85 s / two turns |
| 5 · workflow | One Judge rejection/refinement, approved proposal; no Human/send | 82.17 s |
| 6 · Hold | Judge approval → Human Hold; no send or new fact | 79.76 s |
| 6 · mixed critics | One Judge and one Human rejection, two refinements; three Judge/two Human visits; one Identify; exact receipt and new fact | 112.14 s |
| 7 · restart recall | Jev CHAT; exact round-6 stored message quoted; no send | 15.84 s |
| 7 · four-record history | Fresh reason accepted on first review; human approval, exact delivery/store | 78.78 s |
| 6 · wrong-candidate receipt | Prepared receipt fault after real reviews; delivery unconfirmed, zero new facts | 60.50 s |
| Critic unavailable | Live draft + injected unavailable Codex; blocked before Human/action | 34.92 s |

The launcher elapsed values include process startup, retrieval/embedding work and
scripted human inputs. They are not per-model latency or framework benchmarks.
Native UI review waits are longer and must not be compared with them.

Models: `gemini-3.7-flash`, `jev-1.13.0`, configured `claude-opus-4-6` and
`gpt-6.1-sol` with low reasoning. Jev's backend observation carries its actual
response model, complete questions/distributions, confidence, margins, latency
and reported usage. CLI metadata describes configured models; no CLI token price
is fabricated. Human is a native graph node. Delivery and final history ingestion
remain application operations outside the agent trace.

## What was fixed

The earlier Judge rejected plausible candidates for speculative organizer reactions,
required proof of ordinary talk preparation, and required avoided-reason notes inside
the outbound message. A fresh regression caught that last defect during this audit;
the app already displays those notes separately. The final criterion accepts a usable
CREDIBLE proposal in rounds 5–7 and rejects concrete blockers with minimal repair.
The three live regressions accepted the fresh talk-preparation proposal and rejected
both a burned customer-escalation reason and an internal note inside the email.
Invalid/unavailable review still blocks; Human cannot override it.

Six refinements / seven candidates remains the shared safety limit. Deterministic
mixed-approver tests exercise success at candidate seven and rejection at the limit.
This audit's successful provider run used two refinements; it did not force six.
The preceding seven-rejection failure is not counted as a successful rehearsal.

## Reproduce the teaching sequence

In the full [reference repository](https://github.com/jbaruch/jclaw-devoxx), with
JDK 21, Python 3, local `.env` keys and subscription CLI login:

```bash
./jclaw doctor
./jclaw prepare-demo
./jclaw demo 1
# Quit between rounds; use demo 2 through demo 7.
# Append plain for stdout.
```

See [RUNBOOK.md](../../RUNBOOK.md) for exact follow-ups and expected evidence.
The handoff ZIP's app folder is reference source only; run these commands from the
full repo. `prepare-demo` creates a new named fixture session, preserving every
personal and earlier rehearsal record. Rounds 3/4 share durable conversation and
history, round 5 has independent seeds, and rounds 6/7 share confirmed sends.
Quit round 3 before starting round 4. Repeated launches retain newly sent facts.

The four-record check copied personal rehearsal history into an isolated snapshot;
it did not change personal memory. This matters: the October 2 successful runs used
three seeds and did not cover the later fourth burned reason. That older proof was
previously described too broadly. This audit covers that gap explicitly.

72 app tests pass, including native capability registries, real stdio send/receipt,
round-3→4 file-backed ChatMemory and existing approval/delivery boundaries. Launcher
integration checks cover overlapping builds, arguments, exit codes and forwarding
INT/TERM/HUP before private runtime cleanup. Provider branch tests and live quality
checks establish different things; neither substitutes for the other.

Native TamboUI paste, human rejection through Refine → Judge → Human, confirmed
delivery and literal memory were inspected in round 6. “Mock” is absent from product
labels. A subsequent round-7 native exit rehearsal checks telemetry shutdown; its
result is recorded separately when backend arrival is confirmed.

No full paired LangChain4j show, physical projector or three-hour timing rehearsal
is claimed. Future model judgments can differ. Timebox provider work at two minutes
and use these explicitly labelled prepared transcripts/traces if provider availability
prevents progress. Do not reset history or manufacture approval during recovery.

The native exit follow-up now passed: round 7 recalled the exact native round-6
message in 11 seconds, made no action/history write and exited to the restored
terminal. Backend trace `cac56d1211d5dd052b101838b6e4f472` contains the actual
Jev CHAT response, Gemini call and quoted literal record. A JVM shutdown hook
now closes the agent and flushes when Ctrl+C arrives as SIGINT rather than a
Toolkit key event; normal UI exit shares that once-only cleanup. The original
pre-fix round-6 trace is not claimed to have arrived.
