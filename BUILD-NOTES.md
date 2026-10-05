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
- Make Codex and the human two critics of the same candidate; either rejection
  enters the same Refine step with one shared two-refinement budget.
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

The app test suite passes **60 tests**, including native graph execution, the full
typed review loop, human retry constraints, approval gates, CLI schema parsing,
raw receipt validation, real stdio MCP fixtures, exact-target resolution, memory
persistence/reopening, skill discovery/file scope, terminal-column wrapping, trace metadata
and the Port action boundary. `:app:test :app:installDist` passes on JDK 21. These tests
use fixture responses and local mock processes, without paid model calls.
The revised graph tests exercise Codex rejection followed by human rejection,
one Identify call, preserved request/recipient/history, fresh review of the revised
candidate, a shared limit of two, Hold and exhausted human rejection.

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

These runs predate the October 2 change that puts human review inside the graph
and shares the refinement budget. They are distinct executed paths, not benchmark
comparisons or timing guarantees; the revised live loop still needs rehearsal.
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
features. Human approval now runs inside the native graph; mock delivery and final
ingestion remain outside the native agent trace. The new human node's exported
trace still needs live inspection; equivalent CLI token pricing is not claimed.

## Port closing package

The local bridge uses Ktor 3.4 and the same running calendar/organizer MCP mocks,
canonical recipient lookup, review decision, exact-candidate envelope and raw
receipt validator as the app. Nine Port tests cover context correction, invalid
verdicts, exhausted refinement, modified/wrong/expired approvals, unsuccessful
receipts, confirmed replay after restart, HTTP credential separation and native
human rejection carrying the same request, proof and shared retry count.

The packaged bridge also passed MCP JavaScript reference SDK **1.31.0** over
Streamable HTTP: initialize, list tools, calendar and organizer calls. Exactly two
read tools were available. This check caught a blocking server startup and null
optional fields incompatible with the reference SDK; startup now suspends and
MCP results use the SDK's `McpJson` serializer. The final packaged check also passed
clean SIGTERM shutdown and occupied-port startup failure after mock-process cleanup;
the shutdown callback closes resources without calling `System.exit` inside a JVM
shutdown hook. The check is reproducible with
`port/reference-client/probe.mjs`.

The generated native Port graph has 35 nodes and 46 connections; local validation
checks typed output, bounded paths, explicit tool access, native INPUT outlets,
exact-candidate delivery and receipt-gated history. Two total refinements across
both critics are expanded into a finite DAG. Model and human rejection at each
depth join the same next Refine node; Identify runs once. Koog uses a backedge for
the same behavior. Configured Port APIs differ from the subscription CLI stages.

The workflow, blueprints, fictional fixture seeds, two skills and MCP connector
are reviewable under `port/preview/` and deployed in J-Claw. Live execution on
October 2 verified both real MCP calls, catalog context, typed drafting, two
substantive critic rejections/refinements and the native human gate. The configured
Port models are Haiku 4.5, Sonnet 4.6 and GPT-5.

The first Identify invocation discovered tool definitions but never called them.
Explicit discovery-then-execution instructions fixed this; invocation logs prove
both calls. Readiness now checks the published allowlist and fetches upstream tools
through Port instead of treating a machine identity's `usable: false` as evidence
that shared-header workflow access is unavailable.

Exact payload review also caught a user-facing aside inside the organizer email,
despite critic approval. Draft/refine and Judge now forbid those notes, and the
human panel displays avoided flavors separately. A later run completed native
Hold with no delivery or history write. The shared two-critic graph is now deployed.
Read-only preflight, MCP workflow readback and the restarted bridge's reference
client check passed.

The updated native run
[`wfr_9gVkNwMvf2BNcPcJ`](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/workflow-run?runId=wfr_9gVkNwMvf2BNcPcJ)
completed successfully after first-draft approval, the speaker's native human
rejection, shared Refine, fresh Judge approval and native human approval. Identify
ran once; one of two shared refinements was used. Refine shortened the email,
preserved ALREADY_PROFICIENT and removed the Tuesday-afternoon reference.
Invocation logs prove both actual MCP reads, Judge's skill calls and the human
feedback in Refine and the subsequent Judge input. The revised candidate has a
new identity; only that candidate reached delivery.

The mock receipt matched its candidate, call, event and organizer. The native
history node wrote `jclaw-send-ea292fe6-4c1e-4d35-aa21-3c1e31fe88b9`; an independent
Port MCP catalog read matched its literal message, flavor, recipient, timestamp,
candidate and call against the approved envelope and receipt. Exactly one delivery
and one history write executed. The shared seed records were retained.
Failed-receipt paths still need native rehearsal; their local tests pass.
A stable endpoint and timed stage run remain outstanding.

Codex Port MCP is registered and OAuth login completed for J-Claw. The active
setup uses MCP workflow operations; no unrelated Port organization is modified.

## Port agent declarations and starter cleanup

The J-Claw registry originally contained four sample agents and no j-claw
declarations because the workflow used inline AI nodes. On request, the four
sample declarations (DORA Insights, Reliability, Delivery Performance and Standards
Insights) were backed up locally and removed via Port MCP. The registry at cleanup
contained exactly three active j-claw agents: Identify (Sonnet 4.6), Draft & Refine
(Sonnet 4.6) and Judge (GPT-5), with explicit prompts and tool limits. Draft/refine
was subsequently upgraded to Opus 4.6 as recorded below.

The six Draft/Refine/Judge nodes now invoke their agent identifiers with typed
output schemas. Identify's MCP-enabled AI node is generated from the same declared
configuration because Port agent MCP is supported in interactive chat; automated
connector calls require workflow AI nodes. Agent declarations are included in the
public preview and repeatable setup. Live registry/workflow readback and preflight
verify the references, models, stored tool patterns and prompts.

The first registered-version run, `wfr_ixqcHaw7lR65mYkK`, stopped before drafting:
Haiku searched for both MCP tools but did not invoke them, then invented an event
ID. The canonical-request check refused it. Identify was upgraded to Sonnet 4.6
and its shared declaration requires both actual reads and verbatim event IDs.
The earlier Haiku run evidence above remains a record of those executed versions.

The registered-agent run
[`wfr_II5riF2x7X46bnFK`](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/workflow-run?runId=wfr_II5riF2x7X46bnFK)
completed successfully on the blocked path. Identify made both real MCP calls;
Draft and both refinements invoked `jclaw-draft-refine`, and Judge invoked
`jclaw-judge`. Invocation records carry those agent relations and the configured
Sonnet/GPT-5 model IDs. Judge rejected reused flavors in substance despite changed
labels, including the ALREADY_PROFICIENT reason confirmed in the preceding send.
After two refinements, the shared limit blocked. Identify ran once; no human gate,
delivery or sent-history write executed. The native bounded-exhaustion path is
now verified too. Port's blocked/error message identifies its own Judge; the Koog
CLI continues to name Codex.

The run logs also show `load_skill` in Identify and Draft even though their stored
tool patterns do not list it. The declaration tables report configured patterns;
these observations do not establish strict runtime tool isolation. Judge's actual
skill load is verified independently. No action tool call was observed.

## Port Draft and Refine on Opus

On request, the shared `jclaw-draft-refine` declaration was changed to Port-managed
`claude-opus-4-6`. The J-Claw provider read confirmed that this model is enabled.
Identify remains on Sonnet 4.6 and Judge on GPT-5. All three Draft/Refine nodes use
the same declared agent without provider/model overrides, so both refinements
inherit Opus. The public example and generated preview now record this lineup.
The earlier Sonnet run evidence above describes the versions that actually ran.

Live registry/workflow readback and preflight passed. In native verification run
[`wfr_cs740YKNieQM8H6h`](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/workflow-run?runId=wfr_cs740YKNieQM8H6h),
all seven invocation records confirmed the lineup: one Sonnet Identify, three
Opus Draft/Refine calls linked to `jclaw-draft-refine`, and three GPT-5 Judge calls
linked to `jclaw-judge`. The final candidate passed Judge after two refinements
and reached native human review. No delivery or sent-history write ran. The
verification run was stopped at that gate. Graceful cancellation waited for the
INPUT, so documented force cancellation closed it with a FAILED run result; all
seven model calls had succeeded. See [Port cancellation semantics](https://docs.port.io/workflows/track-workflow-execution/track-and-manage-runs/#cancelling-a-workflow-run).
This checks actual model use, not a comparison of model quality.

## Prompt the Port agent to launch the workflow

The chat entry is now the active `jclaw` agent, titled **j-claw**, on Sonnet 4.6.
It discovers `jclaw_decline` using `list_self_service_triggers`, then calls
`trigger_run` once with the complete user request unchanged. Its configured tools
cover workflow discovery, launch and status; it has no external MCP relation.
Identify, Opus Draft/Refine and GPT-5 Judge remain the three workflow workers.
Human criticism stays in the native run, with the same shared refinement budget.
The registry now has four active j-claw declarations.

A live agent invocation (`0f138b75-870b-40f2-b092-255aaf24b444`) confirmed Sonnet
and the `jclaw` relation. Its SSE tool calls show discovery and automatic launch
with the exact shared opening prompt. Native run
[`wfr_asNVgE4lNdgDRzFm`](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/workflow-run?runId=wfr_asNVgE4lNdgDRzFm)
received that exact request, ran Identify once and entered Draft/Judge. The
verification was gracefully cancelled during Judge; no delivery or sent-history
write executed. Read-only preflight passed with all four declarations.

The API verification established the registered agent's launch behavior. The
initial side-chat instructions assumed an agent selector described in Port's
documentation; the subsequent UI audit below found that selector absent in this
organization. The dedicated chat page now supplies the verified browser entry.

## Port side-chat routing audit

The presenter reported that the chat drafted inline instead of starting the
workflow. The latest two UI invocations (`9b20908b-4bc4-4a2d-8ff8-877d3bf74bb3`
and `f460facb-83ec-4ac9-ae75-2c69bf37e394`) have source `ai-assistant` and no
`agent` relation. Their calls are tool discovery, both calendar/organizer reads
and `jclaw-decline-review` loading; there is no `trigger_run` call and no new
workflow run. The registered `jclaw` invocation previously verified by API does
have its agent relation and discovery/trigger calls. Inspection of the actual
**Build anything** UI found only Prompts, Port Tools and Connectors in the **+**
menu, with no AI Agents selector. Selecting `jclaw-read` attached read tools to
the generic assistant; it did not select the registered entry agent.

## Port chat page and browser verification

The [j-claw page](https://app.port.io/org_LPlEwoGPsLYRbgGB/jclaw-demo) is deployed
after Skills Registry in the Catalog sidebar. The machine-token MCP session
could not describe a user profile and refused organization-page creation. The
signed-in Admin created the dashboard shell, then Port MCP successfully added
the native `ai-agent` widget with `agentIdentifier: jclaw` and `useMCP: true`.
Readback confirms a full-width, 400-pixel widget; its native expand control gives
the stage view. The portable definition is in `port/preview/dashboard.json`.

Clicking the conversation starter in the real browser produced invocation
`29df81dd-3c99-4561-bbe0-5857e1c8d9af`, with the `jclaw` relation, Sonnet 4.6
and no error. Its logs record `list_self_service_triggers` followed by exactly
one automatic `trigger_run`, preserving the complete shared request. The widget
returned the actual run link, with no inline candidate drafting.

Native run
[`wfr_4iGC5x87Fe0x0PZb`](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/workflow-run?runId=wfr_4iGC5x87Fe0x0PZb)
ran Identify once, Draft, both refinements and all three Judge calls. Judge
rejected the reused ALREADY_PROFICIENT rationale despite a changed flavor label.
The shared two-refinement limit routed to Blocked and No external action. Port
reports `COMPLETED / SUCCESS` because the blocked branch completed normally;
this is not delivery success. No human gate, send or sent-history write executed.
A cancellation attempt returned HTTP 409 because the run had already completed.
The run evidence, invocation logs and a browser screenshot are saved in ignored
`port/generated/`. The runbook now uses this bound chat page.

## Port Home demo widgets

On request, [J-Claw Home](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/home)
now contains four native widgets: the `jclaw_decline` workflow launch card, the
three worker-agent records with model IDs, the two j-claw skill records, and the
chat bound to `jclaw`. The workflow and chat occupy the first row; the linked
agent and skill tables occupy the second. `port/preview/home.json` records the
reproducible definition, generated by `prepare.py`.

Home's original starter widgets and layout were backed up in ignored
`port/generated/mcp-home-before.json`. Dataset checks returned exactly the three
requested worker agents and two demo skills. Port MCP created all four widgets.
Its page-upsert tool refused the machine-token update with a page-creation
permission error, so the documented widget PATCH API updated the existing
dashboard container's layout and widget list. Home's type and visibility were
preserved. MCP readback and the browser confirmed the four widgets, exact models,
skill links and opening-request starter. The card's Execute button opened the
correct request form; the form was closed without starting another run. The
homepage chat uses the same entry-agent binding already verified above. A
screenshot is saved in ignored `port/generated/jclaw-home.png`.


## Jev, native LangChain4j validation and six shared refinements (October 2)

Jev `jev-1.13.0` now handles bounded intent and calendar-event selection. The
application reads the actual MCP calendar, computes date labels and assembles
canonical identity and confirmed history. One request asks independent Choice
questions; chat, ambiguous/low-confidence clarification and decline remain
separate outcomes. Google chat, Claude CLI drafting and Codex CLI review remain
unchanged. Gemini classification is an explicit comparison mode, with no fallback.

The first development pass was 14/16; explicit organizer/day matching was
tightened before admission. Final development passed 16/16. The untouched holdout
passed 32/32 over two repetitions (187 ms median), using a frozen shared contract.
The published `TypeSafeDecisionModel` adapter, `langchain4j-typesafe:1.21.0-beta31`,
passed all 16 holdout scenarios (162 ms median), with listener request/response/error
counts 16/16/0. That release accepts string descriptions, so the probe serializes
structured question/option descriptions as JSON text; wire payloads are not identical.
PR [#6469](https://github.com/langchain4j/langchain4j/pull/6469) merged September 29;
use its final `DecisionModel` API rather than the earlier proposal. Raw evidence and
reproduction commands are in [validation/jev](validation/jev/README.md) and
[validation/langchain4j](validation/langchain4j/README.md).

A real Koog workflow produced Langfuse trace `074f9b2787c0062e865209cf1c03755e`.
Its Jev observation is nested under `routeAndIdentify`, contains the exact request
and response, reported model, route, choices, probabilities, confidence, margins
and 351 ms call latency, with 1,913 input and 144 output tokens. Calendar reading
and canonical assembly are application steps. This recorded run used the earlier
two-refinement bound and blocked with no action; its receipt preserves that history.
No generated email, calibrated correctness, hidden reasoning or CLI price is inferred
from decision-model token usage. Delivery and post-send history remain outside the
native agent trace.

The shared policy is now **six refinements / seven candidates**, defined in
`domain/src/main/resources/jclaw/workflow/policy.json`. Judge and Human are two
approvers of the current candidate. Either rejection spends the same request-scoped
budget and enters the same Refine → Judge → Human path. Identify runs once;
canonical identity, constraints and feedback survive. At six refinements approval
can still succeed, while rejection blocks. Hold performs no action. TamboUI counts
up to seven candidates.

All **66 app tests pass**, including a native mixed-approver run that uses two model
rejections and four human rejections, then succeeds on candidate seven. Existing
shorter-bound fixtures explicitly configure their own test limit. The current
Port workflow was upserted through Port MCP and read back with **71 nodes / 98
connections**. The bridge was restarted without clearing credentials, history or
its SQLite ledger; local and public health both report `maxRefinements: 6`.
Read-only Port preflight checks the deployed graph, agents, fixture records, two
MCP read tools and matching bridge bound. A live native INPUT rehearsal subsequently
completed all six shared refinements and delivered candidate seven; see the
current-bound Port evidence below. Earlier successful human-loop evidence is unchanged.

The outline, narrative, script, slide specifications, rhetorical review, shownotes
and downloadable talk skill use the same approver semantics and limit. Viktor’s
handoff includes Jev contracts, evidence, the native adapter probe and current
Koog references; it assigns no Port or presentation work to his agent. Affected
slide image prompts are refreshed; existing raster artwork still needs regeneration
after the speaker’s outline review.


## Complete current Koog app: live stdout and TamboUI (October 2)

The current Jev build with six shared refinements ran end to end against the real
TypeSafe, Google, Claude-subscription and Codex-subscription transports, using
separate copies of the three committed seed stories. Original rehearsal memory
was preserved. All organizer delivery was to the actual mock MCP process.

- Stdout: one Jev decision, one initial draft, three human rejections, three
  refinements, four Codex reviews and final human approval of candidate four.
  Mock delivery confirmed call `0a968eec-4bac-4cb0-bba4-1b1a0d349f06`; exactly one
  new document contained the literal approved message. The third rejection passed
  the old two-refinement limit without restarting Identify/Jev.
- TamboUI: a real Codex quality rejection, refinement, human rejection, the same
  refinement path, fresh Codex approval and human approval of candidate three.
  Mock delivery confirmed call `0caa7170-8410-4985-8cc6-cc0a7ccc0fa7`; the evidence
  panel showed candidate 3/7, matched receipt and one saved sent fact.
- Follow-up: Jev selected CHAT; Gemini discovered/read corporate-speak and rewrote
  at intensity four, preserving facts and commitments. No second send or document.
- Restart: a fresh TamboUI process retrieved and quoted the saved message, with no
  action or new document. Backend raw reply contains the exact stored message.

Langfuse backend observations confirm the actual traces:
`fc52dc9ecfe284833627700fff2e0a6e` (stdout) and
`92f9a43b8efd0fee2edf25c64beb6490` (TamboUI). Each has one Jev decision under
`routeAndIdentify`, actual model `jev-1.13.0`, reported 1,913 input/144 output tokens,
and the native Human nodes/backedges. Calendar and canonical assembly are application
nodes. Delivery and final memory writes remain outside the agent trace. A stale
TamboUI coverage caption was found during the live run, corrected and rebuilt.
The restarted UI was checked against the corrected caption.

The sanitized [current-bound evidence](validation/jev/results/live-six-refinements.json)
records node counts, typed verdicts, appended feedback, receipts, canonical request
and exact persisted-message checks. Human inputs were agent-operated rehearsal
feedback/approval, not an audience's choices. Terminal text and backend data were
inspected; native screenshot automation was unavailable because cmux Computer Use
onboarding is incomplete. No screenshot or physical-projector check is claimed.

This establishes the complete Koog application path. It does not complete the seven
step branches, Viktor's parity implementation, the three-hour paired rehearsal or
Port failed-receipt paths and stage timing.

## Current native Port bound: browser approval and receipt (October 2)

Home's bound `jclaw` chat used `list_self_service_triggers` and one `trigger_run`,
preserving the complete opening request. Run `wfr_zJUi8df41Svu5xK0` completed
on its seventh candidate. Identify ran once; two Judge rejections and four native
Human rejections entered the same six Refine steps. Every replacement returned
through Judge. Human review appeared five times, including final approval at
six of six refinements. The last panel offered **Reject — block (limit reached)**;
that branch was visible but was not selected in this successful run.

The four existing catalog facts, including the earlier confirmed proficiency
excuse, were retained. Judge first rejected a conference clash mislabelled
DEADLINE, then accepted a rewording of that same clash. Human caught this semantic
reuse and explicitly supplied a separate fictional Tuesday-afternoon preparation
deadline at the third human rejection. The final approved message depends on that
new fact. Enum labels and an approved model verdict alone do not establish safe
reasoning. The human inputs were operated by Codex as the user authorized for
this mock rehearsal; they are not audience or user-authored approval evidence.

Only candidate seven reached delivery. Receipt call
`ec63a44a-36ee-438c-a0ae-ae90905abaf1` and candidate
`443de5ecf68958c869f45cc103928cb628e930fc32d0c36fd9ccca63c8f39969`
matched native approval, the exact outgoing message and one independently read
catalog record. History grew from four to five records without resetting the
SQLite ledger. The run finished successfully and all delivery was mock-only.

[Sanitized evidence](port/validation/six-refinements.json) records review decisions,
feedback, models, exact approved plan, receipt and catalog comparison. Raw MCP
responses remain ignored because deployed graph data includes credentials and
signed review tokens. Native browser screenshots remain under ignored
`build/port-validation/`. Read-only Port preflight passed again.

The run took 1,136.953 seconds including browser navigation and review waits;
model-node durations summed to 425.423 seconds. This is a complete-bound exercise,
not a five-minute epilogue timing result or a framework benchmark. Use the
completed run as a labelled prepared example. DEADLINE is now also used for Dana;
do not expect a repeated baseline request to reproduce it or silently erase history.
Stable hosting, current-bound native Hold, exhaustion selection, failed-receipt
rehearsal and physical stage timing remain separate work.

## General assistant dashboard and isolated launches (October 4)

The four TamboUI panes are now Conversation, Workspace, Activity and Evidence.
Ordinary answers and skill rewrites populate Workspace. Candidate review,
human decisions and receipt fields appear only when the current task produces
a candidate. A new request clears those fields. The activity ribbon records
actual stage events and repeated visits, with no configured calendar/decline
topology. Native subgraphs and API calls retain their actual names/models in
the timed trace. These changes do not alter the workflow or approval contract.

Three rendered-dashboard regression tests cover generic startup, arbitrary
repeated stages and replacing a reviewed candidate with an ordinary answer.
All **69 app tests pass**, and the installed app and mock JARs build successfully.
Baruch reported terminal paste working in his session; no paste implementation
change was made. F1 restores prompt focus before terminal paste.

A live session failed with `NoClassDefFoundError: jclaw/Conversation$run$1`:
its JVM started before a build replaced the installed app JAR. The class was
present in the new JAR, but the running class loader could no longer load it.
The launcher now gives each run private copies of the distribution and mock
JARs, retaining the repository working directory for skills and memory. Normal
exit removes those copies. A launcher integration check starts two overlapping
runs and replaces the shared distribution between them; both retain their own
app/mock versions, arguments and exit codes, then remove their private files.
Run it with `python3 tools/test-launcher.py`.

This verifies the updated UI and launcher. The earlier complete provider-run
evidence remains the reference for the workflow. Baruch's review of the current
complete demo is still in progress; step branches have not been prepared.

## Seven-round audit and pragmatic Judge (October 4)

The prior successful October 2 runs used isolated three-seed snapshots. They did
not establish that the later four-record personal history would work; that scope
was previously overstated. The reported seven-candidate rejection was a real demo
failure. This review changed the automatic criterion to a usable CREDIBLE proposal
with concrete blockers, identical with and without Human. It also fixed Judge
requiring avoided-excuse notes inside the email even though the app displays them
separately. Reused reasons and internal notes still fail live regression review.

All seven modes now run from the complete application. `./jclaw prepare-demo`
creates a named fictional session without clearing any existing records;
`./jclaw demo 1` through `demo 7` enable only their intended capabilities. Rounds
3/4 share native file-backed ChatMemory and sent history; round 5 has separate
seeds; rounds 6/7 share confirmed sends. Early sends use application-created IDs
and the same matching-receipt validation. Model pins and low Judge reasoning are
explicit. The TamboUI remains a general assistant with actual event topology,
tool results and task-specific review fields; backend implementation labels are
removed from its product copy and the Port connector/workflow labels.

[The live audit](validation/rounds/README.md) records all seven provider runs,
round 5 without Human/send, Hold, mixed model/human refinements, exact receipt/store,
restart recall, the four-record case, prepared unavailable-Judge and wrong-receipt
failures. Successful scripted scenarios took about 16–112 seconds including launch
and retrieval, not a timing guarantee or framework comparison. Backend Jev/CLI
observations are independently retrieved; CLI costs remain unreported. Native
TamboUI paste, human revision, final approval and exact literal persistence also
completed. 72 app tests pass. Step branches and slide artwork remain on hold.

The Python launcher supervisor fixes the post-merge Copilot signal-cleanup finding:
INT/TERM/HUP reach the child, and runtime files are retained until it exits. The
integration check verifies each signal and overlapping launches. A native UI
shutdown export gap was caught during this audit; the following rehearsal records
its actual backend result rather than equating “enabled” with arrival.

Port's deployed review skill, worker prompts and visible connector/workflow labels
were updated by subset property writes and read back exactly. Model/provider pairs,
credentials, relations, graph edges and sent history were preserved. The current
Port graph remains 71 nodes / 98 connections. This update is not a new timed Port
rehearsal; its earlier completed run remains the prepared closing example.

The native exit follow-up now passed: round 7 recalled the exact native round-6
message in 11 seconds, made no action/history write and exited to the restored
terminal. Backend trace `cac56d1211d5dd052b101838b6e4f472` contains the actual
Jev CHAT response, Gemini call and quoted literal record. A JVM shutdown hook
now closes the agent and flushes when Ctrl+C arrives as SIGINT rather than a
Toolkit key event; normal UI exit shares that once-only cleanup. The original
pre-fix round-6 trace is not claimed to have arrived.
