# TamboUI stage dashboard

J-Claw is a general-purpose personal assistant. The Devoxx app uses TamboUI 0.4.0's retained Toolkit DSL: persistent scrollable
lists, native Markdown, tabs, an animated spinner, focused borders, bracketed paste
and mouse capture. The app's strategy, review and delivery events drive the display.
The dashboard itself cannot authorize a send.

## Stage layout

At 120+ columns and 32+ rows, the Assistant view places Conversation and Workspace
on the left, with Activity and Evidence on the right. Conversation gets the larger
left pane. Workspace shows the current request while it runs, then its answer,
rewrite or other output. A reviewed candidate is one kind of task output. A smaller
terminal keeps Conversation visible; the other panes have full-screen views.

The activity ribbon is generated from actual execution events, in observed start
order. It has no preconfigured calendar, drafting or approval path. Only stages
that ran appear; another visit creates another entry. Dots separate chronological
entries rather than asserting dependency edges or a static graph. Activity retains
the full timed trace, including overlapping subgraphs.

Evidence always shows request counters, actual decision-model output and configured
telemetry. Review, human decision, delivery and receipt fields appear when the
current task produces a reviewed candidate. A new question clears those task-specific
fields and replaces Workspace with the new output. Workflow mode stops at a reviewed
proposal; guardrails mode adds the human critic and exact-candidate delivery.

White Markdown paragraphs and saturated accents sit on an explicit black canvas.
Text rewraps to each pane's current width, including after resize. Long hashes and
wide Unicode glyphs wrap by terminal columns rather than UTF-16 length.

## Controls

| Control | Effect |
|---|---|
| F1 / click ASSISTANT | Assistant dashboard; focus returns to the prompt |
| F2 / click WORKSPACE | Full current task output; candidate details when reviewing one |
| F3 / click ACTIVITY | Full timed trace and tool activity |
| F4 / click EVIDENCE | Current task counters and decisions; review/delivery facts when applicable |
| Tab / Shift+Tab | Cycle focus between visible scroll panes and the prompt |
| Click a pane | Focus it; the border turns green |
| PgUp/PgDn / mouse wheel | Inspect a pane's history |
| Home / End | Jump to the start or end of the focused pane |
| Enter | Submit the input; human confirmation uses the application's exact send/hold rules |
| Ctrl+C | Quit and restore the terminal |

Press F1 before using the terminal's paste shortcut so the prompt owns focus.
Pasting fills the input; Enter submits it.

Input text and scroll state survive view switching and resize. Conversation and Activity
follow new entries until scrolled away; new task outputs begin at the top. Workspace
and Evidence use native selection-driven scrolling with no visual selection stripe.

Run `./jclaw preview` for a **labelled fixture-only** layout rehearsal. It performs
no model, MCP or memory operations. It simulates rejection/refinement and waits for
send, hold or a requested change. This is UI inspection, not execution evidence.
Use `./jclaw guardrails` for the real provider and mock-action path.

## Application interface

- `JclawTui(onSubmit, title, features, mode, reviewOnly, telemetryEnabled, providerLegend)`
  configures the shell. The existing parameters retain their defaults. Both
  frameworks can embed this shell and supply their actual provider legend.
- `resetFlow(request)`, `work(content)`, `stage(...)`, `chat(...)`, `trace(...)`, `toolCall(...)`,
  `startBusy()` / `stopBusy()` marshal to the render thread.
- `candidate(CandidateView)`, `candidateIdentity(...)`, `reviewResult(...)` and
  `outcome(...)` keep the current reviewed message and decision visible.
- `deliveryConfirmed(...)`, `deliveryFailed(...)`, `memorySaved()` and
  `memoryFailed()` receive application outcomes. Memory success is announced only
  after the actual write completes.
- `traceStage(...)` keeps one in-place stopwatch per invocation; retries create
  separate rows. `finishTraceStages(...)` closes unfinished phases and clears busy
  state. Timing is observed elapsed time, with no fabricated percentage progress.
- `quietStdStreams(...)` files library output while JLine owns the terminal;
  `restoreStdStreams()` restores normal output.

Calls before startup are queued. Background and scheduler callbacks hop to the
render thread; stateful lists, tabs, spinner and input remain the same instances.
Rendered Markdown is cached by message and width. Display history is bounded;
application conversation and durable memory are separate.

Agent tool counts come from Koog's tool-start events. The canonical calendar lookup
and application send have separate MCP trace lines. Langfuse configuration is
shown without asserting backend arrival. Human review is a native graph node;
application-owned mock send and final memory ingestion remain outside the agent trace.

## Decision-model evidence

`decision(List<String>)` updates the render-thread evidence panel with model,
measured latency, route, choices, full probabilities, confidence, margin and
reported usage. Feed actual responses on both frameworks. Use `traceStage` for
start/end/failure; the ribbon uses supplied stage names as they execute, with no task-specific
alias table or fixed topology. F4 expands Evidence; F3 expands Activity. The preview marks all these numbers as fixture data.
