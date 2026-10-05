# j-claw — Devoxx Belgium 2026

The Koog side of **Codepocalypse Now: LangChain4j vs JetBrains Koog**:
a three-hour live showdown with Baruch Sadogursky and Viktor Gamov.

j-claw is a personal assistant. Our shared demo task asks it to get Baruch out of
Basic AI Proficiency Training on Tuesday, run by Dana from People Ops, while
avoiding excuses already used with her. The calendar and organizer are mock MCP
servers; delivery never contacts a real person. Their fictional training takes
place on Tuesday October 6; its conference conflict is also fictional and separate
from the real session schedule.

Built against **Koog 1.3.0**. This repository starts from the
[IdeaConf demo](https://github.com/jbaruch/jclaw-demo), with a reviewed Devoxx baseline.

## Current build

The multi-model workflow uses **Jev 1.13.0** for bounded intent/event selection,
application code for canonical identity and confirmed history, Gemini for chat,
Claude's subscription CLI to draft/refine, and Codex's subscription CLI to review
a typed candidate.
Each review receives the current request, including changes from human feedback.
Six refinements total are allowed across both approvers: the initial draft plus
six replacements gives seven candidates. A failed, invalid or
exhausted review blocks sending.

The application resolves the organizer's exact identity from the selected calendar
event before drafting. An abbreviated model echo cannot change the recipient.

- **Workflow mode (round 5)** ends with a reviewed proposal or a blocked result.
- **Guardrails mode (round 6)** adds the human as a second critic. Either critic's
  rejection goes to the same Refine step, then through Codex and human review again.
  The request and retry count survive; a human cannot override Codex.
- **Observability mode (round 7)** uses that same guardrails path with round-7
  trace metadata, including a nested Jev decision observation.

After explicit approval, the application sends the exact candidate to the mock
organizer. It accepts delivery only when the receipt matches the call, candidate,
event and organizer and contains a valid timestamp. Only a confirmed success
becomes sent history. An uncertain result requires checking before retrying.

Conversation memory keeps the current session; rehearsal rounds 3 and 4 also preserve it across their launcher restart. File-backed long-term memory
keeps confirmed sends across restarts. Runtime skills use native Koog discovery
and read-only file tools restricted to the configured skills directory.
Corporate-speak accepts intensity 1–11 and defaults to eleven.

## Getting started

Viktor's agent can download the
[public demo handoff ZIP](https://github.com/jbaruch/jclaw-devoxx/releases/latest/download/viktor-demo-handoff.zip)
with built mock MCP jars, shared fixtures, TamboUI and the native LangChain4j Jev
probe. Read [HANDOFF-LC4J.md](HANDOFF-LC4J.md) for scope and acceptance criteria.
The bundle assigns only the LangChain4j demo; Baruch owns the presentation and Port.

Prerequisites: JDK 21, Python 3, TypeSafe and Google API access, and `claude` and `codex` on PATH
with subscription login already configured. The Gradle wrapper handles Gradle.
Use JDK 21 for Gradle; on macOS the launcher selects the installed JDK 21, while
other platforms should set JAVA_HOME to that JDK.

```bash
cp .env.example .env
# Edit .env locally with TYPESAFE_API_KEY and GOOGLE_API_KEY.
./gradlew :app:test :mocks:mcpJars
./jclaw doctor
./jclaw prepare-demo
./jclaw demo 1             # then demo 2 through demo 7; quit between rounds
```

Paste the shared opening request:

> Get me out of the Basic AI Proficiency Training on Tuesday, run by Dana from
> People Ops. Don't reuse an excuse I've already used on her - tell me which
> ones you're avoiding.

The development launcher preserves local files and memory and never switches Git branches.
Use `./jclaw demo 3 plain` for stdout. `prepare-demo` creates a new named fictional
rehearsal without deleting personal memory or earlier sessions. Rounds 3/4 share
conversation and sent history; round 5 gets its own seed snapshot; rounds 6/7 share
confirmed sends for restart recall. Quit round 3 before launching round 4. Repeated
launches retain facts; run `prepare-demo` explicitly for a fresh rehearsal.

`./jclaw round 1` through `round 7` use your configured personal memory instead.
Bare `./jclaw` opens guardrails mode. Other tools:

```bash
./jclaw skills 4 'The release is delayed because tests are failing. I will send an update tomorrow.'
./jclaw graph
./jclaw graph native       # native task/verification helper teaching graph; no API calls
./jclaw codex
./jclaw preview            # labelled TamboUI fixture rehearsal; no provider calls or actions
JCLAW_MOCK_DELIVERY=wrong-candidate ./jclaw guardrails plain
```

Optional Langfuse credentials enable Koog OpenTelemetry export. API usage and
CLI input/output/duration coverage differ; CLI cost is not fabricated. Human
review now runs inside the native graph. Application-owned delivery and the final
memory write remain outside the native agent trace.

The TamboUI dashboard keeps Conversation, Workspace, Activity and Evidence visible.
Workspace shows the current request and output; review and delivery details appear
when the task uses that workflow. The activity ribbon follows actual execution events
without a predefined calendar or drafting path. F1–F4 switch between Assistant and
full-screen Workspace, Activity and Evidence. Tab or a click changes focus; PgUp/PgDn
and the mouse wheel inspect history. Narrow terminals keep Conversation visible.
See [the TamboUI controls](tui/README.md) and use `./jclaw preview` to check the layout.
Press F1 to return focus to the prompt before pasting with the terminal's paste
shortcut. Enter submits the prompt. The launcher copies the app and mock JARs to
a private directory for each run, so rebuilding during a demo cannot replace its
running code. INT/TERM/HUP are forwarded, and those files are removed only after the child exits; memory and skills remain in the repo.

The [earlier fixture screenshot](docs/tamboui-preview.png) records the previous
workflow-focused layout; the current assistant panes and controls are described above.

For the Port finale, open [J-Claw Home](https://app.port.io/org_LPlEwoGPsLYRbgGB/organization/home)
and click the opening-request starter or paste the same request into **Ask j-claw**.
The bound chat agent launches the reviewed workflow and returns its run link.
Home also shows the workflow launch card, its three worker agents with model IDs,
and the two demo skills.
See [Port setup and rehearsal](port/README.md).

The earlier screenshot is a provider-free UI preview; live run evidence is recorded in
[BUILD-NOTES.md](BUILD-NOTES.md).

## The seven-round build

Finish the complete application on `main` first. Then derive one branch per step
by removing features from that complete implementation. This keeps fixes and
shared contracts in one place while the app is being built. Step branches wait
until Baruch runs and reviews the complete app; none are created yet.

| Round | Capability | Status |
|---|---|---|
| 1 | Chatbot | Live text-only draft and honest inability to send |
| 2 | Tools / MCP | Live reads, visible results, explicit send and receipt; no sent-history memory |
| 3 | Memory | Live retrieved reasons, conversation follow-up, confirmed literal-message ingestion |
| 4 | Skills | Restart from round 3; native skill discovery/read; intensity 11 then 4; no action |
| 5 | Multi-agent workflows | Live Jev → Draft → Judge → one refinement → approved proposal; no Human/send |
| 6 | Guardrails / human loop | Live Hold and mixed Judge/Human refinement; exact send and one durable fact; native TamboUI delivery |
| 7 | Observability | Backend Jev/CLI/loop traces verified, restart exact-message recall and four-record-history delivery |

All seven modes run from the complete app without step branches. The shared Judge
criterion is a **usable proposal that passes automatic quality review** in every
mode: CREDIBLE suffices; optional polish does not spend a refinement. It rejects
concrete contradictions, reused reasons and internal notes. The app displays
avoided reasons separately from outbound text. Six refinements remain a safety
limit, not a desired demo length. Exact model pins: `gemini-3.7-flash`,
`jev-1.13.0`, `claude-opus-4-6`, `gpt-6.1-sol` with low reasoning.
See [the October 4 audit](validation/rounds/README.md) for timings, transcripts,
actual traces and the limits of this verification.

The closing [Port implementation](port/README.md) is deployed in J-Claw. Live
execution has verified real MCP reads, critic refinements and a native Hold with
no action. The updated graph completed human rejection through the shared Refine
step, fresh Judge/human approval, exact-candidate mock delivery and a verified
durable sent fact. The current six-refinement graph completed a native browser
rehearsal on candidate seven: two Judge and four Human rejections shared all six
refinements, Identify ran once, and one exact mock receipt matched one new catalog
record. See [the evidence](port/validation/six-refinements.json). Existing history
was retained. Human caught Judge accepting a renamed calendar conflict and supplied
a separate, explicitly fictional preparation deadline; the accepted result depends
on that added fact. Earlier two-refinement evidence remains labelled separately.
The AI Agents catalog declares the j-claw chat entry, Identify, Draft & Refine, and Judge. Draft/Refine
and Judge now invoke those registrations; Identify shares its declared configuration
with the workflow's MCP-enabled AI node.
`./jclaw port` starts the mock bridge; `python3 port/setup.py` regenerates the
local review package without connecting to Port. The current temporary tunnel
needs a stable endpoint before the stage run. Use the completed run as a labelled
prepared example for the five-minute closing; the full six-refinement rehearsal
took about nineteen minutes including browser inspection and review waits.

The native task/verification teaching
example is compiled in app/src/main/kotlin/jclaw/NativeWorkflow.kt and starts
after request identification. It accepts per-role API models and an appropriate
executor; it is separate from the full app's subscription transport. Its loop is
bounded per run and returns an approved or blocked result. Step branches follow
the completed application.

See [RUNBOOK.md](RUNBOOK.md) for rehearsal, [HANDOFF-LC4J.md](HANDOFF-LC4J.md)
for Viktor's agent handoff and shared demo acceptance criteria, and [BUILD-NOTES.md](BUILD-NOTES.md) for the
Koog skill review and its role in building this demo.

## Jev decision model

`./jclaw jev` runs the real JVM adapter against the calendar MCP without drafts or
actions. Configure `TYPESAFE_API_KEY` locally. The default workflow uses Jev;
`JCLAW_DECIDER=gemini` explicitly selects the earlier classifier/Identify comparison.
There is no silent fallback. One API call asks independent intent and event Choice
questions. Code computes date labels, resolves exact identity and loads confirmed
history. Missing/ambiguous targets or confidence below the validated 0.60 floors
ask for clarification before drafting. CHAT ignores the speculative event answer.

The TamboUI evidence view shows actual model, decisions, probabilities, confidence,
margin, latency and reported tokens. Langfuse receives those same fields under
`routeAndIdentify → jevDecision`; calendar reads and request assembly are application
steps. A decision-model token count is not generated email text or permission to send.

[Validation and raw evidence](validation/jev/README.md): development 16/16,
independent holdout 32/32 across two runs (187ms median); the published native
LangChain4j adapter also passed the 16 holdout scenarios. These are fixture checks,
not general accuracy claims. The complete LangChain4j workflow still needs Viktor's
implementation and rehearsal.
