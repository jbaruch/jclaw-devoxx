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

`./gradlew build` passes **49 tests**, including native graph execution, the full
typed review loop, human retry constraints, approval gates, CLI schema parsing,
raw receipt validation, real stdio MCP fixtures, exact-target resolution, memory
persistence/reopening, skill discovery/file scope and trace metadata. These tests
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

Full terminal/projector rehearsal, cross-framework parity and the Port epilogue
remain pending. Complete the app first, then derive step branches by removing
features. Human approval, mock delivery and final ingestion still sit outside the
native agent trace; neither that coverage nor equivalent CLI token pricing is claimed.
