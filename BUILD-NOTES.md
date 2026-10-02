# Koog skill review and Devoxx build evidence

## Two skill consumers

The coding agent used the installed Koog authoring guidance to build j-claw.
Running j-claw discovers the independent corporate-speak runtime skill. These
are the two halves of the round-4 meta-moment.

## Sources used

- Initial build used jbaruch/koog **0.5.1**, with author-strategy,
  use-agent-skills and domain-model-subtask-pipeline guidance.
- After the speaker published the update, installed **0.6.0** and reviewed the
  changed module, strategy, history-scope, memory, skills, CLI and telemetry guidance.
  The manifest keeps the repository's floating `latest` convention; this record
  preserves the concrete guidance version reviewed.
- [Koog 1.3.0 tagged source](https://github.com/JetBrains/koog/tree/1.3.0),
  commit `3acc88cf8ce70b87d8afbd3cf184844a50aa504e`.
- Native API signatures and deterministic graph tests, rather than copying
  uncompiled plugin examples.
- [Plugin refresh issue #31](https://github.com/jbaruch/koog-plugin/issues/31)
  records the original corrections. It is now closed by
  [PR #32](https://github.com/jbaruch/koog-plugin/pull/32), included in 0.6.0.

The initial review found stale imports and configuration examples, outdated memory
guidance, incomplete CLI credential assumptions and stale model profiles. Tagged
source took precedence where guidance disagreed. Version 0.6.0 corrects those
areas and agrees with the implementation choices checked here. The live checks
remain necessary evidence beyond reading a skill.

## Resulting changes

- Upgrade Koog runtime and beta modules to 1.3.0 / 1.3.0-beta.
- Use native built-in Gemini profiles rather than cloning an older profile.
- Carry the current typed request through each critic invocation.
- Keep automatic review separate from human confirmation and delivery.
- Bind a raw validated MCP receipt to the exact approved target/message and attempt.
- Write sent history only after confirmation, using the actual request's target.
- Restrict native runtime file tools to the configured skills root, including
  absolute, parent-traversal and symlink cases.
- Clean up already-started MCP processes if later startup fails.
- Replace the IdeaConf branch-switching/reset launcher with explicit Devoxx modes.
- Add NativeWorkflow.kt's native task/verification example with a per-run bounded
  retry budget. `./jclaw graph native` renders its real topology without model calls.
- Resolve the recipient from the selected calendar event before drafting. A live
  model abbreviated the name to Dana; canonical resolution now preserves the exact
  organizer across review, human approval and the validated receipt.
- Share the application's receipt-to-memory path between stdout and the TUI.
- Remove a machine-specific Gradle JDK path; the macOS launcher selects JDK 21.

## Verification boundary

The app test suite passes **58 tests**, including native graph execution, the full
typed review loop, human retry constraints, approval gates, CLI schema parsing,
raw receipt validation, real stdio MCP fixtures, exact-target resolution, memory
persistence/reopening, skill discovery/file scope, terminal-column wrapping, trace metadata
and the Port action boundary. `:app:test :app:installDist` passes on JDK 21. These tests
use fixture responses and local mock processes, without paid model calls.

## Live stdout rehearsal — October 1, 2026

Google's gemini-3.7-flash API, Claude Code 2.1.286 with Claude.ai subscription auth,
and Codex CLI 0.159.3 with ChatGPT login all ran successfully. The CLI transports
strip inherited API billing credentials. CLI model versions still require agreement
with Viktor before making paired comparisons; CLI brands are not exact model IDs.

| Run | Observed result | Elapsed |
|---|---|---|
| Workflow | First candidate approved; reviewed proposal only; no human prompt or delivery | 40.1 s |
| Guardrails retry | Human held the first plan and requested an asynchronous alternative; three reviews rejected it, then blocked; “send” could not override | 101.8 s |
| Approved send and skills | Exact approved message delivered to the mock with a matching receipt; one memory record written; follow-up listed/read corporate-speak and rewrote without sending again | 46.8 s |

These are distinct executed paths, not benchmark comparisons or timing guarantees.
The initial workflow's 38 observations arrived in Langfuse. The guardrails trace
records the latest human constraints in all three retry critic inputs and
application prompts, with review attempts 1, 2 and 3.

Redacted run logs and API evidence are kept in ignored local logs/. Local keys,
vectors and generated sent-history documents are also excluded from Git; only
the three fictional seed documents are committed.

## TamboUI stage dashboard and terminal checks

The dashboard uses native Toolkit panels, persistent scroll lists, Markdown,
tabs, an animated spinner, bracketed paste, mouse capture and focused borders.
Wide terminals show conversation/current candidate beside timed trace/evidence;
compact terminals keep the current message readable. F1–F4 open full inspection
views. Text rewraps on resize using terminal display widths. The current candidate,
critic verdict, human gate, validated receipt and memory-write status come from
application events; no percentage progress or inferred delivery is displayed.

Actual JLine/TamboUI PTY runs checked 150×48, 90×28 and 90×24 layouts, F1–F4 switching,
Tab/Shift+Tab, mouse focus, candidate PageDown/Home/wheel scrolling, preserved input
across view changes and resize during
the human decision. A labelled provider-free `./jclaw preview` exercises fixture
events without model, MCP or memory calls; it is not execution evidence.

The first real TUI run retrieved the earlier rehearsal's sent-history document,
exhausted two refinements and stayed blocked (77 s on the dashboard), with no
delivery or new fact. A second run used an isolated copy of the three shared seed
documents: the first candidate was approved, the recipient remained canonical
through resize, the mock receipt was validated and exactly one fact was written.
That run reached the human gate 28.7 s after startup and delivery at 36.0 s,
including inspection interactions. These are observed paths, not benchmarks.
A separate live TUI rewrite visibly read the corporate-speak skill, completed
as a chat reply and showed no attempted delivery or new sent-history write.

Native terminal-output captures remain in ignored logs/. Physical projector
readability, full-show rehearsal, cross-framework parity and the live Port epilogue
remain pending. Complete the app first, then derive step branches by removing
features. Human approval, mock delivery and final ingestion still sit outside the
native agent trace; neither that coverage nor equivalent CLI token pricing is claimed.

## Port closing package

The local bridge uses Ktor 3.4 and the same running calendar/organizer MCP mocks,
canonical recipient lookup, review decision, exact-candidate envelope and raw
receipt validator as the app. Seven Port tests cover context correction, invalid
verdicts, exhausted refinement, modified/wrong/expired approvals, unsuccessful
receipts, confirmed replay after restart and HTTP credential separation.

The packaged bridge also passed MCP JavaScript reference SDK **1.31.0** over
Streamable HTTP: initialize, list tools, calendar and organizer calls. Exactly two
read tools were available. This check caught a blocking server startup and null
optional fields incompatible with the reference SDK; startup now suspends and
MCP results use the SDK's `McpJson` serializer. The final packaged check also passed
clean SIGTERM shutdown and occupied-port startup failure after mock-process cleanup;
the shutdown callback closes resources without calling `System.exit` inside a JVM
shutdown hook. The check is reproducible with
`port/reference-client/probe.mjs`.

The generated native Port graph has 45 nodes and 57 connections; local validation
checks typed output, bounded paths, explicit tool access, native INPUT outlets,
exact-candidate delivery and receipt-gated history. Two refinements and one human
replacement are expanded into a finite DAG. This differs from the JVM's ongoing
conversation. Configured Port APIs also differ from the subscription CLI stages.

The workflow, blueprints, fictional fixture seeds, two skills and MCP connector
are reviewable under `port/preview/`. No Port organization was modified. Target
schema acceptance, connected tool cache, model availability and native INPUT/run
behavior require the user's target instance. See `port/README.md` for setup and
the five-minute closing run.
