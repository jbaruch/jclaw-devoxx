# Devoxx rehearsal runbook

This is a working build, not a completed rehearsal record. Seven competitive
rounds lead to a verdict and a five-minute Port epilogue. Memory and skills are
separate rounds. All human rejection and approval belongs to guardrails.

## Build and launch

```bash
./gradlew :app:test :mocks:mcpJars
./jclaw workflow           # round 5: reviewed proposal or blocked; never asks to send
./jclaw guardrails         # round 6: full human approval / rejection sequence
./jclaw observability      # round 7: same workflow, round-7 trace metadata
./jclaw port               # closing: local mock bridge for the native Port workflow
```

Append `plain` for stdout. Use `./jclaw --help` for standalone tools.
The launcher sources the ignored local .env, builds the installed app and mock
jars, and keeps memory intact. It never changes branches or clears local data.

Use the TamboUI dashboard as the main stage view. At 120+ columns and 32+ rows,
conversation/current candidate sit beside timed trace/evidence. F1 returns to the
dashboard and prompt; F2 expands the current message, F3 the trace, F4 the evidence.
Tab/Shift+Tab or a click changes focus; PgUp/PgDn and the mouse wheel inspect history.
Enlarge the terminal font and use the full-screen views for individual teaching beats.
`./jclaw preview` supplies labelled fixture-only events for layout checks without
provider calls or actions. It must not be presented as an executed agent run.

Complete the app on `main` before deriving step branches by removing features.
Rounds 1–4 then need their own branches before the seven-round run is stage-ready.
Do not present the combined baseline as those missing checkpoints.

## Shared input and observed outcomes

Use the README's exact opening ask on both frameworks. Use the same mock jars,
seed memory and corporate-speak skill. Agree model roles and exact model selection
with Viktor; disclose transport or model differences before comparing results.

Read the actual candidate and verdict. Immediate approval, refinement and blocking
are all possible. Do not promise the critic will discover a particular excuse.
A prepared result used after failure must be identified as a rehearsal.

## Round 5: automated loop

1. Inspect calendar read → Jev intent/event decision → application request assembly, then draft and the complete critic input.
2. Follow the actual route: approve, refine within the shared six-refinement limit, or block.
3. Confirm the app stops at a reviewed proposal without asking for approval.
4. Show NativeWorkflow.kt's native task/verification helpers before the custom
   subscription-CLI integration. `./jclaw graph native` emits its actual topology
   without provider calls. This small example begins with an identified request.

## Round 6: human loop and delivery

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

Configure Langfuse locally, run observability mode, then quit cleanly to flush.
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

## Remaining rehearsal work

- See BUILD-NOTES.md for the completed live stdout runs, plugin 0.6.0 review and trace evidence.
- Continue full-show rehearsal; the native-helper example passes graph tests, and the TamboUI dashboard passed native terminal interaction checks and live guardrails delivery.
- Derive the seven step branches from the completed app by removing features.
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
