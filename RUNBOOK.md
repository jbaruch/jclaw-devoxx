# Devoxx rehearsal runbook

All seven Koog round modes have completed live provider rehearsals;
[the audit](validation/rounds/README.md) records their actual scope and results. Seven competitive
rounds lead to a verdict and a five-minute Port epilogue. Memory and skills are
separate rounds. All human rejection and approval belongs to guardrails.

## Build and launch

```bash
./jclaw doctor             # local prerequisites; no provider calls
./jclaw prepare-demo       # new fictional session; preserves all existing history
./jclaw demo 1             # then 2–7, quitting between rounds
./jclaw demo 5 plain       # stdout fallback for any round
./jclaw port               # closing: local bridge for the native Port workflow
```

Append `plain` for stdout. Use `./jclaw --help` for standalone tools.
The launcher sources the ignored local .env, builds the installed app and mock
jars, then runs private copies of those files. Later builds cannot replace code
under the running JVM. The copies are removed on exit; memory and skills remain
in the repo. It never changes branches or clears local data.

Use the TamboUI dashboard as the main stage view. At 120+ columns and 32+ rows,
Conversation and Workspace sit beside Activity and Evidence. F1 returns to the
Assistant dashboard and prompt; F2 expands Workspace, F3 Activity, F4 Evidence.
The ribbon follows actual executions, and Workspace handles any task output.
Review and delivery details appear when the current task produces a candidate.
Tab/Shift+Tab or a click changes focus; PgUp/PgDn and the mouse wheel inspect history.
F1 returns focus to the prompt before terminal paste; Enter submits it.
Enlarge the terminal font and use the full-screen views for individual teaching beats.
`./jclaw preview` supplies labelled fixture-only events for layout checks without
provider calls or actions. It must not be presented as an executed agent run.

Baruch must run and review the complete app on `main` before step branches are
derived by removing features. Do not prepare them before that review.
All seven capability modes already run without branches. Step branches are still
on hold until that review; no branch preparation is required to rehearse now.

## Shared input and observed outcomes

Use the README's exact opening ask on both frameworks. Use the same mock jars,
seed memory and corporate-speak skill. Agree model roles and exact model selection
with Viktor; disclose transport or model differences before comparing results.

Read the actual candidate and verdict. Immediate approval, refinement and blocking
are all possible. Do not promise the critic will discover a particular excuse.
A prepared result used after failure must be identified as a rehearsal.

## Round 1–4: conversational assistant

1. `./jclaw demo 1`: paste the shared opening ask, then “Can you send it?” Text
   alone has no tool authority. There is no calendar, history retrieval or send.
2. Quit; `./jclaw demo 2`: paste the same ask. Inspect actual calendar and organizer
   calls/results in Activity. Declined flags omit the old reasons; j-claw must
   acknowledge that gap. “Send that exact message to Dana for the training” invokes
   a validated receipt, with no durable sent-history feature yet.
3. Quit; `./jclaw demo 3`: repeat the ask. Retrieve the three real seed documents;
   identify the reasons being avoided. Send the chosen text explicitly and inspect
   the new literal sent fact. Conversation follow-ups use native ChatMemory.
4. Quit; `./jclaw demo 4`: “Rewrite the last message in corporate-speak. Keep the
   facts and commitments unchanged. Do not send it.” Round 3's conversation survives
   this restart. Show actual discovery and SKILL.md read; default intensity eleven.
   Then “Keep that message, but turn the corporate-speak down to intensity 4.
   Do not send it.” Both rewrites preserve facts and perform no action/history write.

The assistant and four generic panes remain the same; only available capabilities
change. No fixed calendar/draft topology is displayed before execution. F3 shows
actual tool results; F4 distinguishes receipt confirmation from durable memory.
A fresh `prepare-demo` creates three independent seed groups: rounds 3/4, round 5,
and rounds 6/7. Earlier teaching sends cannot accidentally burn round 5's baseline.
Repeated runs within each group retain confirmed facts. Personal memory is untouched.

## Round 5: automated loop

Launch `./jclaw demo 5`. Judge requires a usable, reasonably plausible proposal;
CREDIBLE is sufficient. Minor polish is advice, in both the automatic and human
rounds. Avoided-reason notes appear in the app, outside outbound text. A failed or
invalid Judge still blocks; no approval is manufactured at the limit.

1. Inspect calendar read → Jev intent/event decision → application request assembly, then draft and the complete critic input.
2. Follow the actual route: approve, refine within the shared six-refinement limit, or block.
3. Confirm the app stops at a reviewed proposal without asking for approval.
4. Show NativeWorkflow.kt's native task/verification helpers before the custom
   subscription-CLI integration. `./jclaw graph native` emits its actual topology
   without provider calls. This small example begins with an identified request.

## Round 6: human loop and delivery

Launch `./jclaw demo 6`.

1. Run guardrails mode and read the exact reviewed message and recipient.
2. Hold it: no delivery and no sent-history write.
3. Reject with a substantive change. Follow the same Refine step, then Codex and
   human review again. Confirm Identify runs once, the current request carries
   the feedback and both critics share six refinements total. Earlier approval
   cannot approve the revised candidate; rejection at the limit blocks.
4. Approve the current candidate. Inspect the matching mock receipt and stored
   literal outbound message.
5. Restart and retrieve the new sent history. A draft or style rewrite is not a send.

Delivery fixtures use `JCLAW_MOCK_DELIVERY`: `success` (default), `refused`,
`error`, `malformed`, `wrong-event`, `wrong-call`, `wrong-candidate`.
An explicit refusal is failed delivery. A tool error or invalid receipt leaves
delivery unconfirmed; check the organizer before retrying. Neither writes a
confirmed sent fact. These are fixtures, not live framework verdicts.

## Round 7: trace evidence

Configure Langfuse locally, run `./jclaw demo 7`, then quit cleanly to flush.
Begin with “What exact message did you send to Dana for the Basic AI Proficiency
Training? Quote it. Do not send anything.” It retrieves round 6’s confirmed send.
Select the newest trace by timestamp, session and Devoxx release metadata.
Inspect actual node inputs/outputs, repeated review attempts, feedback and duration.

Open `routeAndIdentify → jevDecision` and inspect the exact state/questions,
answers, actual `jev-1.13.0` response model, choices, complete distributions,
confidence, margins, latency, usage and failure status. `readCalendar` and
`assembleRequest` are application steps. Generic chat telemetry does not cover
a decision model automatically; Viktor's native adapter uses
`DecisionModelListener` request/response/error hooks under his workflow parent.

Jev/Gemini API usage and CLI transport evidence have different coverage. No equivalent
CLI token price is claimed. The human critic now runs inside the native graph;
mock delivery and the final memory write remain outside its trace. Rehearse the
human node and backedge during the projector walkthrough; both have now run live.

## Skills meta-moment

Distinguish the coding agent reading the Koog framework skill during this build
from running j-claw reading corporate-speak to answer a user. Show the skill read,
the resulting code change and its compile/test result. BUILD-NOTES.md records
the source versions and corrections. Plugin 0.6.0 includes the repairs from
issue #31 / PR #32; the relevant updated guidance was reviewed against this build.

## Recovery and remaining rehearsal work

Preflight CLI subscription login, model availability and Langfuse before the show.
`doctor` checks configuration; it does not assert a live subscription works or that
traces arrived. The audit's successful runs took about 16–112 seconds per scenario,
including launch/embedding work and scripted review inputs. These are observations,
not framework benchmarks or runtime guarantees. Native human waits add real time.

Timebox a live attempt at two minutes of provider work. Inspect the actual failure;
if provider availability prevents progress, show the labelled prepared transcript
and trace in `validation/rounds`, then continue the explanation. Do not repeatedly
ask the same failing Judge, weaken the criterion during the show or reset sent
history to manufacture a success. Unconfirmed delivery requires inspection before
retrying. A fresh fictional session is an explicit presenter action.


- See BUILD-NOTES.md for the completed live stdout runs, plugin 0.6.0 review and trace evidence.
- Continue full-show rehearsal; the native-helper example passes graph tests, and the TamboUI dashboard passed native terminal interaction checks and live guardrails delivery.
- Wait for Baruch's complete-app review before deriving step branches.
- Use the refreshed fictional Tuesday October 6 fixture on both sides.

## Port closing

The complete local package and five-minute run are in [port/README.md](port/README.md).
Open [J-Claw Home](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/home).
Use **Ask j-claw** and click the shared opening-request starter or paste the request.
Expand the widget for the stage view. This widget is bound to the registered
`jclaw` entry agent; the generic **Build anything** chat has no agent picker in
the current J-Claw UI. Follow the returned run link and confirm a new workflow
run exists. Home's workflow card opens the direct launch form; its tables show
the three worker agents with model IDs and the two demo skills. Show the four
j-claw declarations in AI Agents, then the worker references in the
workflow. Draft/Refine and Judge use AI_AGENT nodes; Identify's AI node shares its
declared configuration to support the workflow MCP connector. Human critic two
is the native INPUT step.
Generate the review artifacts with `python3 port/setup.py`; this makes no external
writes. Start the bridge with `./jclaw port` and verify its read-only MCP surface
with the reference client before setting up the target organization.

After instance access, configure actual model/provider pairs, reviewer identity
and an HTTPS bridge endpoint. Seed and connect the prepared graph, then rehearse
approve, hold, either critic's rejection through the shared refinement loop,
exhaustion at six total shared refinements and an unsuccessful receipt.
Disclose API-versus-CLI model transport. The native DAG unrolls the same bounded
loop used by Koog. Do not
present the local package as a completed Port run. INPUT notifications are disabled.

J-Claw now has the deployed chat page, graph, four declared agents, fixture records, published skills and two-tool
MCP connector. Native execution has verified reads and critic refinement through
the human gate and a Hold with no action. The revised shared two-critic graph has
completed human rejection → shared Refine → Judge → fresh human approval, followed
by exact-candidate mock delivery and a verified durable sent fact. Identify ran
once and one shared refinement was used. Identify now uses Sonnet 4.6,
Draft/refine Opus 4.6 and Judge GPT-5.
Inspect the exact organizer payload: it is sent verbatim, while avoided-excuse
explanations belong in the separate human review panel. The registered-agent
version has also blocked at its then-configured two-refinement limit with no action.
The current graph and bridge share six refinements / seven candidates and pass
read-only preflight. The current-bound browser rehearsal completed candidate seven
after two Judge and four Human rejections. All six shared refinements were used;
Identify ran once and one exact mock receipt matched one new catalog fact.
See [the sanitized evidence](port/validation/six-refinements.json).
Human caught a renamed calendar conflict and explicitly supplied a separate
fictional preparation deadline; this result depends on that added fact. Existing
history was retained, and DEADLINE is now also used for Dana.
For the five-minute closing, open the completed run and label it **PREPARED RUN**.
The full refinement rehearsal took about nineteen minutes including browser/review
waits; model-node durations summed to about seven minutes. Do not claim a timed
five-minute live run or clear history implicitly.
Failed-receipt paths
still need native rehearsal. Keep the temporary bridge tunnel
running and arrange a stable endpoint before presenting.
- Rehearse the verified Google/Claude/Codex paths in the terminal; agree exact CLI model versions with Viktor.
- Exercise the unavailable critic recovery on stage; approval, substantive rejection and the refinement limit have run live.
- Verify physical projector readability in the TUI; wide/compact terminal layouts, view switching, keyboard/mouse focus, input preservation and resize during approval are checked. Runtime skill reading and a rewrite without sending have run live in the TUI.
- Walk the arrived Langfuse traces on the projector and agree comparable evidence with Viktor.
- Rehearse Port failed-receipt paths, and arrange stable hosting.
- Time the full sequence, prepare clearly labelled recovery checkpoints and final resources.

## Decision-model preflight

Run `./jclaw jev` before the show. `TYPESAFE_API_KEY` is in the ignored `.env`;
never paste it into the request. Jev is pinned to `jev-1.13.0`.
`JCLAW_DECIDER=gemini ./jclaw workflow` is an explicit comparison mode.

Use the two shared JSON resources in `domain/src/main/resources/jclaw/jev/`.
The live evaluator and JVM adapter share them. Run
`python3 validation/jev/validate.py --split holdout --repeat 2` only when a live
recheck is needed. Existing checked-in reports include the first development
failure and the final admitted runs. If the questions, model or policy change,
repeat evaluation; do not carry thresholds to an unvalidated model.

Inspect full probabilities in F4 and the timed Jev stage in F3. Confidence and
margin describe the distribution; they do not certify correctness or authorize
delivery. Jev does not draft, invoke tools, judge candidates or send.


## Current complete-app live check (October 2)

The current six-refinement Koog build has completed the real-provider path in both
stdout and TamboUI from isolated copies of the three shared seed facts. Stdout
accepted three human rejections and delivered candidate four, proving continuation
beyond the earlier two-refinement bound. TamboUI used one Codex rejection and one
human rejection in the same loop, then delivered candidate three. Each wrote exactly
one confirmed outbound record. Jev ran once per decline; every replacement received
a fresh Judge verdict and final exact-candidate human approval.

The follow-up used corporate-speak at intensity four with actual discovery/file
reads and no send. A restarted TamboUI process recalled the newly saved literal
message. Langfuse received the Jev and Human graph observations. See
[the current-bound receipt](validation/jev/results/live-six-refinements.json).
Human inputs in these checks were agent-operated mock rehearsal inputs, not claims
about a conference audience. Your original rehearsal history was untouched.

This verifies the complete Koog app; seven step branches, paired LangChain4j
rehearsal, physical projector checks and full-show timing remain separate work.
The expanded native Port human-loop rehearsal is now recorded in
[port/validation/six-refinements.json](port/validation/six-refinements.json).
