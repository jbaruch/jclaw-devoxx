# Handoff to Viktor's agent: the LangChain4j demo

Build Viktor Gamov's **LangChain4j Agentic** implementation of j-claw for
**Codepocalypse Now: LangChain4j vs JetBrains Koog**, Devoxx Belgium 2026.
Your deliverable is a runnable demo with behavior on par with Baruch's Koog side.
Baruch's team owns the slides, narrative, shownotes and Port finale; those require
no work from you. Work in Viktor's demo repository and preserve his existing work.

Inspect that repository, its agent instructions and its current implementation.
Reuse what works, implement the missing behavior, and validate the result. Use
idiomatic LangChain4j constructs and verify chosen APIs against the documentation
and source for the version you pin. Match the shared task, contracts, observable
behavior and action boundaries; a translation of Koog's class names is unnecessary.

## Start here

The Koog reference repository is <https://github.com/jbaruch/jclaw-devoxx>.
This handoff is dated **2026-10-04**. Download the public
[viktor-demo-handoff.zip](https://github.com/jbaruch/jclaw-devoxx/releases/latest/download/viktor-demo-handoff.zip)
and its [SHA-256 checksum](https://github.com/jbaruch/jclaw-devoxx/releases/latest/download/viktor-demo-handoff.zip.sha256).
The source is also available in this repository. The ZIP manifest records the
exact reference commit and each bundled file's hash. Start with `START-HERE.md`
after extracting it; no access to Baruch's machine is required.

The bundle contains shared fixtures, domain types, corporate-speak, TamboUI,
built mock MCP jars, the frozen Jev contract and validation records, a runnable
native LangChain4j decision probe, and current Koog source/test references. Its Gradle settings
include only `domain`, `mocks` and `tui`; `app/` is reference source, not a runnable
application in the bundle. Credentials, runtime memory, Port resources and
presentation assets are excluded.

**Finish and validate the complete LangChain4j application on `main` first.**
Wait for Baruch to run and review the complete demo, then derive seven round
branches by removing features from that complete build.
Document their commands and capability boundaries. Do not maintain seven diverging
partial implementations. The Koog step branches are still pending; `./jclaw demo 1` through `demo 7`
already expose every round from the complete app. Match those capability modes
without requiring branches to rehearse. This handoff is the behavior reference.

## The exact shared task

Paste this unchanged on both sides:

> Get me out of the Basic AI Proficiency Training on Tuesday, run by Dana from
> People Ops. Don't reuse an excuse I've already used on her - tell me which
> ones you're avoiding.

j-claw remains a general-purpose assistant. A rewrite or question is ordinary
chat, rather than a reason to restart the decline workflow. “Rewrite in
corporate-speak” uses the draft already in conversation.

Use [Scenario.kt](domain/src/main/kotlin/jclaw/domain/Scenario.kt) and
[Store.kt](mocks/src/main/kotlin/jclaw/mocks/Store.kt):

| Fact | Shared value |
|---|---|
| Selected event ID | `basic-ai-proficiency-2026` |
| Title | `Basic AI Proficiency Training (Mandatory)` |
| Start | `2026-10-06T15:00:00+02:00`, fictional Tuesday October 6 |
| Exact organizer | `Dana from People Ops` |
| Sensitivity | `TOUCHY` |
| Known attendees | Dana, the user's skip-level, the whole platform team |
| User context | Baruch builds AI agents professionally and presents a public talk about building them that afternoon |
| Seeded sent flavors | `CALENDAR_CONFLICT`, `FAMILY_OBLIGATION`, `CUSTOMER_ESCALATION` |

The fixture is separate from the real conference calendar. Calendar lists three
previously declined sessions without their reasons. Those reasons come from the
**three committed files** in [memory/documents](memory/documents). Retrieve these
seed documents as evidence, rather than hardcoding the reasons into the model
prompt. Each framework may index them in its own durable store.

Start paired rehearsals from the same explicitly chosen memory snapshot. Preserve
confirmed sends thereafter. Do not reset history on launch or branch change.
Baruch's working memory has additional rehearsal facts; those are not bundled.
Proposed alternatives and previously sent flavors remain separate facts.

## Shared MCP processes

Use the supplied `mock-jars/calendar-mcp.jar` and `mock-jars/organizer-mcp.jar`
with JDK 21. Configure each as a separate stdio MCP child process:

```bash
java -jar mock-jars/calendar-mcp.jar
java -jar mock-jars/organizer-mcp.jar
```

Stdout is protocol traffic; route stderr to the visible tool trace. To rebuild
from the bundled shared project, use JDK 21 and:

```bash
./gradlew :mocks:mcpJars --console=plain
```

| Server | Tool | Contract |
|---|---|---|
| Calendar | `getCalendar` | No arguments; returns four actual fixture events |
| Calendar | `createCalendarEvent` | `title`, `startIso`, optional `durationMin`; mock mutation |
| Organizer | `getOrganizerSensitivity` | `name`; returns the organizer's sensitivity |
| Organizer | `sendDecline` | Five required fields below; returns a typed receipt |

The planning workflow uses the two read tools. Draft/refine and Judge have no
external-action authority. The application owns delivery after exact-candidate
approval. All recipients and sends are mock; no real messaging integration is needed.

## Seven competitive rounds

This is a three-hour live showdown with deeper explanations, not a laptop lab.
These allocations cover both implementations, explanations and audience interaction.

| Round | Budget | Required visible behavior |
|---|---|---|
| 1 · Chatbot | 12 min | Draft text; explain why text alone cannot prove an action occurred |
| 2 · Tools / MCP | 25 min | Actual calendar/organizer calls and results; expose the missing reasons for earlier declines |
| 3 · Memory | 15 min | Same-session follow-up, retrieval of prior sent reasons, controlled restart preserving durable history |
| 4 · Skills | 10 min | Runtime discovery/read of corporate-speak; rewrite the current draft at eleven, then tone it down |
| 5 · Workflows | 35 min | Jev decisions + code Identify → Draft → Judge, automatic bounded refinement; stop at reviewed proposal or Blocked |
| 6 · Guardrails | 25 min | Entire human critic sequence: Hold, rejection/refinement, fresh Judge/human review, exact approval and mock delivery |
| 7 · Observability | 20 min | Reconstruct actual stages, critic inputs/verdicts, feedback, retries, duration and action/history evidence |

Round 5 has **no human prompt or send**. All human rejection and approval belongs
to round 6. Koog runs first in odd rounds; LangChain4j first in even rounds. Use
the same input for the paired execution. Read the actual candidate and verdict:
a first draft may pass, refine or block. Do not force a winning excuse or script
a moral conclusion. A recorded recovery is labelled as a rehearsal.

The coding-agent Koog-skill meta-moment is Baruch's responsibility. Your skills
round demonstrates the running assistant applying the shared corporate-speak skill.

## Typed contracts and model roles

[Model.kt](domain/src/main/kotlin/jclaw/domain/Model.kt) and
[Delivery.kt](domain/src/main/kotlin/jclaw/domain/Delivery.kt) define the wire fields
and enums. Mirror them as Java records/POJOs or reuse the shared types. Their Koog
annotations are reference metadata, not a requirement for your agent to use Koog.

| Type | Fields / meaning |
|---|---|
| `DeclineRequest` | `eventId`, `organizerName`, `recentlyUsedFlavors`, `knownAttendees`, current `userInstruction`, `previouslyProposedFlavors` |
| `DeclineDeployment` | `flavor`, nullable `fakeCalendarEventId`, literal `messageToOrganizer`, `hallwayScript` |
| `DeclineReview` | Current request together with the exact candidate plan |
| `DeclineCritique` | `tier`, `approved`, substantive `feedback` |
| `DeclineSend` | Application-owned `callId`, `candidateId`, `eventId`, `organizerName`, literal `message` |
| `DeclineReceipt` | `delivered`, matching call/candidate/event/organizer fields, `deliveredAt` |

Resolve the canonical organizer from the selected calendar event before drafting.
A model echo of “Dana” must not change the target. Drafting creates no supporting
calendar event; `fakeCalendarEventId` is null in this workflow. Keep reasoning and
avoided-excuse explanations outside the literal outbound message and hallway script.

The Koog demo uses Jev API for intent/event decisions, code for canonical request
assembly, Gemini API for chat, Claude's subscription CLI for draft/refine, and
Codex's subscription CLI for Judge. Jev is pinned to `jev-1.13.0`; Gemini is selected by
`JCLAW_FLASH` (default profile 3.7, actual `gemini-3.7-flash`). The reviewed pins
are `claude-opus-4-6` for Draft/Refine and `gpt-6.1-sol` with low reasoning for
Judge; environment overrides are explicit. CLI brand names alone are insufficient. Record your
actual framework/model/CLI versions, authentication and transport. Agree any
differences with Viktor/Baruch before comparing behavior or cost. Preserve the
roles and disclose any API-versus-subscription-CLI differences. Use LangChain4j
Agentic for orchestration; label custom transport adapters accurately.

## Automatic quality criterion

Judge evaluates a usable, reasonably plausible proposal in rounds 5–7. CREDIBLE
is sufficient; it need not be airtight. The absence of guaranteed organizer consent
is not a blocker for a respectful request. Ordinary preparation for the supplied
public-talk context is a permissible inference. Optional wording improvements are
advice, not rejection. Reject only concrete contradictions, wrong targets, reused
or explicitly excluded reasons, violated constraints, internal notes/placeholders,
unperformed permissions, insults, invented unrelated emergencies or plainly
implausible text. Give at most two actionable blockers and the smallest repair.

The application visibly explains previously sent/proposed flavors separately from
the literal email and hallway text. Judge must not require those internal notes
inside the outbound message to satisfy “tell me which ones you're avoiding.”
The same standard applies without Human; it does not defer automatic correctness
to the human. Invalid or unavailable review fails closed.

See `CliCritic.kt`, the shared review descriptions and the actual accept/reuse/note
regressions in [validation/rounds](validation/rounds/README.md). They caught a real
moving-goalpost defect; deterministic branch tests alone cannot validate judgment.

## The human is critic two

Implement the same request-scoped loop:

```text
Identify once → Draft → Judge
                         ├─ approve (round 5) → reviewed proposal, stop
                         ├─ approve (rounds 6–7) → Human
                         ├─ reject with budget → Refine → Judge
                         └─ invalid / unavailable / exhausted → Block

Human ├─ approve exact current candidate → application send → validate receipt → save history
      ├─ reject with feedback and budget → SAME Refine → Judge → Human
      ├─ reject at limit → Block
      └─ Hold → no action
```

There are **six refinements total per request**, shared by model and human
rejections: seven candidates including the initial draft. Start the counter at zero;
increment only when Refine runs. At six refinements, either approver can still
approve; a rejection blocks.

Human rejection preserves the event, organizer, run and retry count. Append its
feedback to the current instruction and carry the updated request through Refine
and Judge. **Do not restart Identify, create a new top-level request or reset the
budget.** Every revised candidate needs fresh Judge and human approval. A human
cannot override a rejected or unavailable Judge. Hold ends with no action.

For a suitable candidate, use this human feedback in rehearsal:

> Make the email shorter and more direct. Keep the proficiency reason; remove
> the Tuesday-afternoon reference.

A style rewrite later in ordinary chat cannot silently replace an approved
candidate or send new text. A genuinely separate user request gets its own budget;
keep counters in request state, not mutable workflow-wide state.

## Delivery and memory proof

`sendDecline` requires `eventId`, `organizerName`, `message`, `callId` and
`candidateId`. The application creates the identifiers. Match
[the reference delivery code](app/src/main/kotlin/jclaw/Delivery.kt):

1. For each of event ID, organizer and exact message, append its UTF-8 byte length,
   a colon and its literal value, in that order.
2. SHA-256 that concatenation as lowercase hex to obtain `candidateId`.
3. Generate a new `callId` for the send attempt.
4. Validate the raw MCP outcome: no tool error; one unambiguous typed receipt;
   exact call/candidate/event/organizer match; `delivered=true`; valid offset timestamp.
5. Announce confirmed delivery, then persist the exact outbound message and target.
   Announce memory success only after that write finishes.

A matching `delivered=false` confirms refusal. Error, absent/malformed receipt,
wrong identity or invalid success timestamp leaves delivery unconfirmed. Neither
creates a sent fact. Uncertainty requires inspection before retry. If delivery
succeeded but memory saving failed, report both facts accurately. The hash binds
candidate text; it is not authentication or delivery idempotency.

Set `JCLAW_MOCK_DELIVERY` **on the organizer child process** for these shared
fixtures: `success`, `refused`, `error`, `malformed`, `wrong-event`, `wrong-call`,
`wrong-candidate`. Draft, Judge approval and human approval are not delivery proof.

## Runtime skill and TamboUI

Use [corporate-speak/SKILL.md](skills/corporate-speak/SKILL.md) unchanged. Discover
metadata at startup and visibly load the relevant body through scoped read-only
tools before applying it. Default to eleven, then ask “tone it down to 4.” Preserve
facts and commitments. Neither rewrite sends or writes history. Verify file reads
cannot escape the skill root through absolute paths, parent traversal or symlinks.

Use [the shared TamboUI module](tui/README.md) for the stage demo. Its class is
`com.jbaruch.jclaw.tui.JclawTui`; the module has no Koog dependency. Keep J-Claw
a general-purpose assistant: Conversation, Workspace, Activity and Evidence are
generic panes. Use `work(content)` for ordinary answers/rewrites, and send actual
stage events to `traceStage`/`stage`. The ribbon records observed execution and
repeated visits; do not configure a fixed calendar or decline topology. Review and
delivery fields appear only for the task currently producing a candidate. Drive candidate,
verdict, human state, timed trace, receipt and memory-write displays from actual
LangChain4j/application events. Set the mode to LangChain4j and the real
`providerLegend`. Adjust remaining Koog/Codex or coverage captions to your behavior. Keep backend
implementation labels such as “mock” out of the product UI, prompts and receipt
status; disclose the fictional backend in technical documentation instead.

Use the wide dashboard at 120+ columns and 32+ rows. F1–F4 expose Assistant,
Workspace, Activity and Evidence views. Preserve input across view changes/resize, marshal
updates to the render thread, and stop child processes/flush traces on exit.
F1 returns focus to the prompt for terminal paste; Enter submits. Isolate each
running app's JARs from later builds so a live session cannot lose lazily loaded
classes when the installed distribution is replaced.
Provide a plain-text fallback. Label fixture-only UI previews explicitly.

## Acceptance and completion report

Build the complete app, then verify these behaviors with deterministic workflow
tests and real stdio mocks. Paid model calls are for live rehearsal, not required
for deterministic branch tests.

| Check | Evidence |
|---|---|
| Shared input | Actual calendar/organizer reads and three seeded sent reasons |
| Chat / skills | Follow-up uses conversation; skill read; levels 11 and 4; no action/history |
| Automatic loop | Initial draft plus at most six shared refinements; Judge receives the current request |
| Mixed critics | Model then human rejection shares one budget; Identify runs once |
| Human revision | Feedback reaches Refine and fresh reviews; old approval cannot authorize new text |
| Boundaries | Hold, exhausted rejection and invalid/unavailable Judge have no send/history |
| Successful action | Exact approved payload; matching receipt; one new sent fact; restart retrieves it |
| Negative action | Every failure fixture produces no confirmed sent fact |
| Reuse | A separate request has its own six-refinement budget |
| Stage usability | Readable live views, resize, preserved input, plain fallback, clean shutdown |
| Observability | Actual trace ID/timestamps, paths/retries/context/duration, disclosed coverage gaps |

Inspect the bundled `app/src/test/kotlin/jclaw/` references, especially
`SendFollowUpTest`, `ReviewGateTest`, `DeliveryReceiptTest`, `McpDeliveryTest`,
`ConversationTest`, `AgentSkillsTest`, `JevTest` and `ObservabilityTest`. They specify behavior; implement your
checks using Viktor's framework and test stack. Traces must show actual inputs,
outputs and attempts; API usage and CLI timing do not imply equivalent token costs.

Return the repository/commit, pinned versions, launch commands, seven-round
capability mapping (branches only after Baruch's approval), test results, live rehearsal evidence and remaining limits. Identify
model/transport differences needing speaker agreement. Compilation or a printed
graph alone does not establish demo readiness.

## Jev: native LangChain4j support and parity contract

Use the released **`dev.langchain4j:langchain4j-typesafe:1.21.0-beta31`**,
with **`dev.langchain4j:langchain4j-http-client-jdk:1.21.0`** for JDK transport.
The merged final API is `DecisionModel` / `TypeSafeDecisionModel`, not the earlier
`StructuredDecisionModel` proposal. [PR #6469](https://github.com/langchain4j/langchain4j/pull/6469)
merged September 29; the published artifact was checked October 2. The integration
is experimental, so pin the artifact and inspect its actual API.

```java
var decider = TypeSafeDecisionModel.builder()
    .apiKey(System.getenv("TYPESAFE_API_KEY"))
    .modelName("jev-1.13.0")
    .timeout(Duration.ofSeconds(8))
    .maxRetries(2)
    .listeners(decisionTelemetry)
    .build();
```

See the independently compilable [native adapter validation](validation/langchain4j)
for exact imports, `DecisionRequest` / `ChoiceQuestion`, response access and listener
hooks. It passed **16/16 live holdout scenarios**, with 16 request callbacks,
16 response callbacks and zero error callbacks. This validates that adapter, not
your future complete Agentic application. beta31 accepts string descriptions for
question text/options: serialize the shared structured descriptions as JSON text.
The mapping preserves all facts but changes their wire representation. It was
validated separately; do not assume examples in moving main-branch docs match beta31.

Share [questions.json](domain/src/main/resources/jclaw/jev/questions.json),
[policy.json](domain/src/main/resources/jclaw/jev/policy.json) and the fixture cases.
Ask intent and event together against the current user message, prior user/assistant
conversation and actual calendar records. Code computes date labels before the call.
Do not give Jev the persona prompt, tools or a request to write a decline.

- Validate model identity, answer types, allowed choices and distributions.
- Apply the pinned 0.60 confidence floors. Uncertain/missing/ambiguous targets ask
  for clarification without drafting. An explicit conflicting organizer/day must
  not select a near-match. Multiple targets also require clarification.
- CHAT uses the original input and ignores the unused event answer. Gemini still
  handles chat and runtime skills; Jev does not generate the chat response.
- DECLINE selects one actual calendar ID. Application code assembles exact organizer,
  known attendee evidence, confirmed sent flavors and current-session proposed flavors.
  Recheck canonical calendar identity before Draft. Held suggestions are separate
  from confirmed sent history. Preserve both across the relevant turns.
- Jev transport/contract errors stop before Draft. Do not silently fall back to a
  different model. Retain Gemini only as an explicitly labelled comparison mode.
- Human rejection is another approver's verdict on the same candidate. It uses
  the existing Refine → Judge → Human path; it never repeats Jev/Identify.

## Jev observability and TamboUI

Use `DecisionModelListener.onRequest/onResponse/onError`; generic `ChatModel`
listeners do not instrument this API. Start an observation beneath the current
workflow node, keep its parent context across the async completion, and close it
on response/error. Record actual state/questions, raw typed answers, requested
and returned model version, latency and reported usage. Include each choice,
complete probabilities, confidence, margin, selected event and application route.
Label unused speculative answers. Never record API credentials.

Calendar reads and confirmed-history/request assembly are application operations,
not model-generated tool calls. Label them accordingly. Judge and Human are two
approvers with the same rejection/refinement contract; their verdicts and the shared
counter must be visible. Native graph spans include Human; delivery and final
history writes need their own evidence/coverage, not assumed agent tracing.

Call the shared dashboard's `decision(List<String>)` with real values and use
`traceStage` for actual decision start/end/error. The compact stage ribbon can show
Calendar → Jev → Identify → Draft → Judge ⇄ Refine → Human → Send → Memory.
Retain F3 trace and F4 evidence views, real probabilities/margins and a plain fallback.

Koog backend receipt is in [langfuse-jev.json](validation/jev/results/langfuse-jev.json):
Jev is nested under `subgraph routeAndIdentify`, model `jev-1.13.0`, with 1,913
input and 144 output tokens. The real run exhausted refinement and blocked;
there was no send/history write. This proves export of the decision observation,
not an approved delivery path.

The current shared limit is **six refinements, seven candidates total**, from
`domain/src/main/resources/jclaw/workflow/policy.json`. Both approvers spend it.
Earlier recorded runs with a two-refinement limit retain their original evidence.


## Current complete Koog reference run

The six-refinement Jev build has now completed real-provider stdout and TamboUI
runs. Stdout consumed three human rejections and delivered candidate four. TamboUI
consumed one Codex rejection and one human rejection, then delivered candidate
three through the same shared loop. Jev ran once per decline, canonical identity
and feedback survived, and each isolated fixture directory gained one exact
confirmed sent message. A corporate-speak follow-up did not send; a restarted
TamboUI process retrieved the saved message. Backend traces include actual Jev
and Human nodes. See [the live receipt](validation/jev/results/live-six-refinements.json).
These are agent-operated mock rehearsals of the complete Koog app, not a paired
benchmark or evidence of a completed LangChain4j implementation.

## October 4 Koog rehearsal reference

[All seven rounds](validation/rounds/README.md) ran with real configured providers,
with the final quality policy. This includes no-action rounds, actual MCP send,
round 3→4 conversation restart, skill reads at eleven/four, round 5's approved
proposal without Human, Hold, mixed Judge/Human refinements and one exact delivery,
round 7 recall, and the four-record snapshot that defeated the prior stricter Judge.
72 app tests pass. Native TamboUI paste, human revision and confirmed delivery were
also inspected. Model and timing evidence is scoped to those runs, not a promise
of identical future model behavior. The full paired show and projector remain
speaker rehearsal work.
