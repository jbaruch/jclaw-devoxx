# TamboUI stage dashboard

The full Devoxx app uses TamboUI 0.4.0's retained Toolkit DSL: persistent scrollable
lists, native Markdown, tabs, an animated spinner, focused borders, bracketed paste
and mouse capture. The app's strategy, review and delivery events drive the display.
The dashboard itself cannot authorize a send.

## Stage layout

At 120+ columns and 32+ rows, the live view places conversation and the pinned
current candidate on the left, with the timed trace and evidence on the right.
A smaller terminal shows the conversation or current candidate in one large pane;
trace and evidence remain available through their full-screen views.

The flow chips show actual stage states, including the human gate, mock send and
memory write in guardrails. Workflow mode stops at the reviewed proposal. The
outcome banner distinguishes critic review/refinement, human approval, holding,
blocking, confirmed delivery and unconfirmed delivery.

White Markdown paragraphs and saturated accents sit on an explicit black canvas.
Text rewraps to each pane's current width, including after resize. Long hashes and
wide Unicode glyphs wrap by terminal columns rather than UTF-16 length.

## Controls

| Control | Effect |
|---|---|
| F1 / click LIVE | Stage dashboard; focus returns to the prompt |
| F2 / click CANDIDATE | Full current message, recipient, critic feedback and instruction |
| F3 / click TRACE | Full timed trace and tool activity |
| F4 / click EVIDENCE | Review, human decision, candidate fingerprint, receipt and persisted fact |
| Tab / Shift+Tab | Cycle focus between visible scroll panes and the prompt |
| Click a pane | Focus it; the border turns green |
| PgUp/PgDn / mouse wheel | Inspect a pane's history |
| Home / End | Jump to the start or end of the focused pane |
| Enter | Submit the input; human confirmation uses the application's exact send/hold rules |
| Ctrl+C | Quit and restore the terminal |

Input text and scroll state survive view switching and resize. Chat and trace
follow new entries until scrolled away; new candidates begin at the top. Candidate
and evidence use native selection-driven scrolling with no visual selection stripe.

Run `./jclaw preview` for a **labelled fixture-only** layout rehearsal. It performs
no model, MCP or memory operations. It simulates rejection/refinement and waits for
send, hold or a requested change. This is UI inspection, not execution evidence.
Use `./jclaw guardrails` for the real provider and mock-action path.

## Application interface

- `JclawTui(onSubmit, title, features, flow, mode, reviewOnly, telemetryEnabled, providerLegend)`
  configures the shell. The existing parameters retain their defaults. Both
  frameworks can embed this shell and supply their actual provider legend.
- `resetFlow()`, `stage(...)`, `chat(...)`, `trace(...)`, `toolCall(...)`,
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
shown without asserting backend arrival; human approval, mock send and final
memory ingestion remain outside the native agent trace.
