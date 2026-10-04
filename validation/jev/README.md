# Jev admission check

The live admission check passed on October 2, 2026. Jev is now the default
workflow decider. Gemini remains an explicitly selected comparison mode.

The evaluator reads the real packaged calendar MCP server, computes date labels in
code, and sends one TypeSafe request per case with two independent Choice questions:
intent and target event. Both implementations can reuse the JSON questions and
policy. It never drafts, sends, updates memory, or substitutes an offline model.

```sh
python3 validation/jev/validate.py --dry-run
python3 validation/jev/validate.py --split development
python3 validation/jev/validate.py --split holdout
python3 validation/jev/validate.py --repeat 2
```

Set `TYPESAFE_API_KEY` in the ignored demo `.env` or environment. The evaluator
reads the value without executing `.env` or printing credentials. Python's TLS
verification remains enabled; an installed `certifi` bundle is used when available.

The 32 cases are split equally between development and holdout. They cover the
exact opening request, paraphrases, dates, distracting calendar records, quoted
editing instructions, skill requests, follow-ups, missing targets and ambiguity.
Modified calendars are explicitly synthetic test variants of the actual fixture.
One selected event is required; requests for several targets go to clarification.

The shared `domain/src/main/resources/jclaw/jev/policy.json` pins Jev 1.13.0
and validated 0.60 confidence floors. `questions.json` lives beside it. Each holdout run
records contract, case and calendar hashes. If questions or thresholds are tuned
after seeing holdout failures, those cases are no longer unseen validation: add a
fresh holdout before treating the candidate as admitted.

Admission requires every expected application path and target to match, with zero
service/contract errors. In particular, no ordinary chat, missing-event or ambiguous
request may start a decline. Uncertainty goes to clarification. Confidence is a
distribution statistic; the admission check measures behavior on these cases, not
general accuracy or calibration.

Reports under ignored `build/jev-validation/` contain exact requests, raw answers,
actual model identity, latency, usage, policy outcomes and pass/fail results. A dry
run reports `PREPARED`, `live: false`; it cannot count as model validation.

Primary references: [API](https://docs.typesafe.ai/api),
[Choice](https://docs.typesafe.ai/primitives/choice),
[parallel speculative questions](https://docs.typesafe.ai/patterns/fan-out),
[confidence](https://docs.typesafe.ai/confidence),
[known model limitations](https://docs.typesafe.ai/model-jaggedness/jev-1.13).

## Recorded evidence

| Run | Result | Median latency | Meaning |
|---|---|---|---|
| [First development pass](results/development-01.json) | 14/16 | 228ms | Incorrect organizer/day near-matches; retained failure evidence |
| [Revised development](results/development-02.json) | 16/16 | 202ms | All explicit details must match a record |
| [Untouched holdout, two passes](results/holdout-01.json) | 32/32 | 187ms | Frozen questions/policy; 16 scenarios repeated twice; zero errors |
| [Released native LC4J adapter](results/langchain4j-native.json) | 16/16 | 162ms | Same holdout scenarios; native beta31 mapping checked separately |

These small fictional fixtures measure the selected application paths, not general
model accuracy, calibration or cross-framework speed. The first failure informed
the questions; no holdout tuning occurred. Native LC4J accepts string descriptions,
so the probe serializes structured instructions/options as JSON text. This is a
semantic mapping, not byte-identical wire input; see its actual source.

`./jclaw jev` separately verified the real JVM/Ktor client and calendar MCP.
The deterministic JVM test replays all 48 admitted raw responses and requires
request equality and the same policy outcomes as the evaluator. Native graph
tests cover both critics sharing a budget without repeating Jev/Identify.

[Langfuse backend receipt](results/langfuse-jev.json) confirms the real Jev
observation under `subgraph routeAndIdentify`: `jev-1.13.0`, 351ms call,
1,913 input/144 output tokens, exact structured IO and full decision metadata.
Calendar/context assembly remain application spans. That complete workflow run
blocked after its then-configured two refinements; it performed no send/history
write. Error-path tests verify no invented response model or usage on failure.


[Current six-refinement full-app check](results/live-six-refinements.json) ran
real providers in stdout and TamboUI. Stdout delivered candidate four after three
human rejections; TamboUI delivered candidate three after one Judge and one Human
rejection. Both kept one Jev/Identify pass and wrote one exact confirmed message.
A skill rewrite performed no send; a restarted TamboUI process recalled the saved
message. Backend traces confirm native Jev and Human observations. Feedback and
approvals were agent-operated rehearsal inputs, and the seed snapshots were isolated.
The full paired show and expanded native Port human-loop rehearsal remain pending.
