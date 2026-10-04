package com.jbaruch.jclaw.tui

import dev.tamboui.layout.Constraint
import dev.tamboui.style.Color
import dev.tamboui.style.Style
import dev.tamboui.text.CharWidth
import dev.tamboui.text.Line
import dev.tamboui.text.Span
import dev.tamboui.text.Text
import dev.tamboui.toolkit.Toolkit.*
import dev.tamboui.toolkit.app.ToolkitApp
import dev.tamboui.toolkit.app.ToolkitRunner
import dev.tamboui.toolkit.element.Element
import dev.tamboui.toolkit.element.StyledElement
import dev.tamboui.toolkit.elements.ListElement
import dev.tamboui.toolkit.elements.TabsElement
import dev.tamboui.toolkit.event.EventResult
import dev.tamboui.tui.TuiConfig
import dev.tamboui.tui.event.KeyCode
import dev.tamboui.tui.event.TickEvent
import dev.tamboui.widgets.input.TextInputState
import dev.tamboui.widgets.tabs.TabsState
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

enum class ChatKind { JCLAW, YOU, TOOL_RESULT, OK, ERR }
enum class StageState { PENDING, ACTIVE, DONE, FAILED }
enum class TraceKind { SUBGRAPH_START, SUBGRAPH_END, TOOL_CALL, LLM, RUNNING, ERROR }

/** A stage dashboard built entirely with TamboUI's retained Toolkit elements. */
class JclawTui(
    private val onSubmit: (String) -> Unit,
    private val title: String = "j-claw",
    private val features: List<String> = emptyList(),
    private val flow: List<String> = emptyList(),
    private val candidateLimit: Int = 7,
    private val mode: String = "KOOG / DEVOXX",
    private val reviewOnly: Boolean = false,
    private val telemetryEnabled: Boolean = false,
    private val providerLegend: String = "Gemini API  →  Claude Code  →  Codex CLI  |  organizer: mock",
) : ToolkitApp() {
    private val stageStates = HashMap<String, StageState>()
    private val chatMessages = mutableListOf<ChatMessage>()
    private val traceLines = mutableListOf<TraceEntry>()
    private val activeTraceStages = mutableMapOf<Pair<String, String>, TraceStopwatch>()
    private var traceRefresh: ToolkitRunner.ScheduledAction? = null
    private var startedNanos = System.nanoTime()
    private var traceTick = 0L
    @Volatile private var stopped = false
    private val pending = ConcurrentLinkedQueue<Runnable>()
    private val promptInput = TextInputState()
    private val tabState = TabsState(0)
    private var busyDepth = 0
    private var statusText: String? = null
    private var turn = 0
    private var turnStartedNanos: Long? = null
    private var turnFinishedNanos: Long? = null
    private var toolCalls = 0
    private var skillReads = 0
    private var savedFacts = 0
    private var candidate: CandidateView? = null
    private var decisionLines: List<String> = emptyList()
    private var verdict = "Not reviewed"
    private var feedback = ""
    private var human = if (reviewOnly) "Disabled in workflow round" else "Not asked"
    private var delivery = "Not attempted"
    private var receiptId: String? = null
    private var memoryStatus = "No new sent fact"
    private var outcome = DemoOutcome.STARTING
    private var outcomeDetail = "Connecting mock tools and loading skills"

    // All stateful elements survive render/resize/view changes, including the input.
    private fun logPane(id: String, tail: Boolean = true): ListElement<*> = list()
        .selected(-1).highlightSymbol("").highlightStyle(Style.EMPTY)
        .apply { if (tail) stickyScroll() else autoScroll() }.scrollbar().id(id).focusable()
    private val chatList = logPane(CHAT_ID)
    private val traceList = logPane(TRACE_ID)
    private val candidateList = logPane(CANDIDATE_ID, tail = false)
    private val evidenceList = logPane(EVIDENCE_ID, tail = false)
    private val busySpinner = spinner().fg(Color.YELLOW).bold()
    private val promptElement = textInput(promptInput)
        .id(PROMPT_ID).focusable()
        .placeholderStyle(Style.EMPTY.fg(Color.CYAN))
        .onSubmit(Runnable {
            val line = promptInput.text()
            if (line.isNotBlank()) {
                appendChat("you: $line", ChatKind.YOU)
                promptInput.clear()
                onSubmit(line)
            }
        })
    private val viewTabs: TabsElement = tabs(*VIEW_NAMES).state(tabState)
        .fg(Color.CYAN).highlightStyle(Style.EMPTY.fg(Color.BLACK).bg(Color.CYAN).bold())
        .padding(" ", " ").divider("  ")
        .onMouseEvent { event ->
            if (!event.isPress || !event.isLeftButton) EventResult.UNHANDLED
            else {
                val x = event.x() - (viewTabsAreaX())
                var offset = 0
                val index = VIEW_NAMES.indexOfFirst { name ->
                    val end = offset + CharWidth.of(name) + 2
                    val hit = x in offset until end
                    offset = end + 2
                    hit
                }
                if (index < 0) EventResult.UNHANDLED else { selectView(index); EventResult.HANDLED }
            }
        }
    private fun viewTabsAreaX(): Int = viewTabs.renderedArea()?.x() ?: 0

    override fun configure(): TuiConfig = TuiConfig.builder()
        .mouseCapture(true).bracketedPaste(true).tickRate(Duration.ofMillis(150)).build()

    private fun onRenderThread(block: () -> Unit) {
        if (stopped) return
        val r = runner()
        val guarded = Runnable { if (!stopped) block() }
        if (r == null) {
            pending.add(guarded)
            if (stopped) pending.remove(guarded)
        } else r.runOnRenderThread(guarded)
    }

    override fun onStart() {
        stopped = false
        startedNanos = System.nanoTime()
        traceTick = 0
        runner()?.focusManager()?.setFocus(PROMPT_ID)
        while (true) (pending.poll() ?: break).run()
    }

    override fun onStop() {
        stopped = true
        finishTraceStagesOnRenderThread(TraceStageState.CANCELLED, System.nanoTime())
        pending.clear()
    }

    fun stage(name: String, state: StageState) = onRenderThread { stageStates[name] = state }

    fun resetFlow() = onRenderThread {
        stageStates.clear()
        turn++
        turnStartedNanos = System.nanoTime()
        turnFinishedNanos = null
        toolCalls = 0
        skillReads = 0
        candidate = null
        decisionLines = emptyList()
        candidateList.selected(0)
        evidenceList.selected(0)
        verdict = "Not reviewed"
        feedback = ""
        human = if (reviewOnly) "Disabled in workflow round" else "Not asked"
        delivery = "Not attempted"
        receiptId = null
        memoryStatus = "No new sent fact"
        outcome = DemoOutcome.RUNNING
        outcomeDetail = "Identify the request; Claude drafts; Codex reviews"
    }

    fun outcome(state: DemoOutcome, detail: String = "") = onRenderThread {
        outcome = state
        outcomeDetail = detail
        if (state in TERMINAL_OUTCOMES) turnFinishedNanos = System.nanoTime()
        if (state == DemoOutcome.HUMAN) {
            human = "Waiting for this exact candidate"
            stageStates["human"] = StageState.ACTIVE
            tabState.select(0)
            runner()?.focusManager()?.setFocus(PROMPT_ID)
        }
        if (state == DemoOutcome.HELD) { human = "Held / change requested"; stageStates["human"] = StageState.DONE }
        if (state == DemoOutcome.SENDING) { human = "Approved exact candidate"; stageStates["human"] = StageState.DONE }
        if (state == DemoOutcome.BLOCKED) stageStates["verify"] = StageState.FAILED
    }

    fun candidate(value: CandidateView) = onRenderThread {
        candidate = value
        candidateList.selected(0)
        verdict = "Awaiting Codex"
        feedback = ""
        outcome = DemoOutcome.REVIEWING
        outcomeDetail = "Review ${value.attempt}/$candidateLimit · current request and exact proposed message"
    }

    fun candidateIdentity(id: String) = onRenderThread { candidate = candidate?.copy(candidateId = id) }

    /** Actual decision-model answers and measured latency, supplied by either framework. */
    fun decision(lines: List<String>) = onRenderThread { decisionLines = lines.toList() }

    fun reviewResult(approved: Boolean, reason: String) = onRenderThread {
        verdict = if (approved) "APPROVED" else "REJECTED"
        feedback = reason
        outcome = if (approved) DemoOutcome.PROPOSAL else DemoOutcome.REFINING
        outcomeDetail = reason
    }

    fun toolCall(name: String, args: String) = onRenderThread {
        toolCalls++
        if (name == "__read_file__") skillReads++
        appendTrace(TraceMessage("↪ $name($args)", TraceKind.TOOL_CALL))
    }

    fun deliveryConfirmed(id: String) = onRenderThread {
        receiptId = id
        delivery = "Validated mock receipt"
        memoryStatus = "Saving confirmed outbound message"
        stageStates["send"] = StageState.DONE
        outcome = DemoOutcome.DELIVERED
        outcomeDetail = "Exact message and target confirmed by the organizer mock"
        turnFinishedNanos = System.nanoTime()
    }

    fun memorySaved() = onRenderThread {
        savedFacts++
        memoryStatus = "Exact outbound message persisted"
        turnFinishedNanos = System.nanoTime()
        stageStates["memory"] = StageState.DONE
    }

    fun memoryFailed() = onRenderThread {
        memoryStatus = "Write failed after confirmed delivery"
        stageStates["memory"] = StageState.FAILED
    }

    fun deliveryFailed(unconfirmed: Boolean) = onRenderThread {
        delivery = if (unconfirmed) "Unknown; check before retrying" else "Refused by organizer mock"
        stageStates["send"] = StageState.FAILED
        outcome = if (unconfirmed) DemoOutcome.UNCONFIRMED else DemoOutcome.BLOCKED
        outcomeDetail = delivery
        turnFinishedNanos = System.nanoTime()
    }

    fun chat(line: String, kind: ChatKind = ChatKind.JCLAW) = onRenderThread { appendChat(line, kind) }
    private fun appendChat(line: String, kind: ChatKind) {
        chatMessages += ChatMessage(line, kind)
        if (chatMessages.size > MAX_MESSAGES) chatMessages.removeAt(0)
    }
    fun trace(line: String, kind: TraceKind = TraceKind.TOOL_CALL) = onRenderThread { appendTrace(TraceMessage(line, kind)) }
    private fun appendTrace(entry: TraceEntry) {
        traceLines += entry
        if (traceLines.size > MAX_TRACE) traceLines.removeAt(0)
    }

    /** One live stopwatch row per invocation; retries create a separate row. */
    fun traceStage(stage: String, provider: String, state: TraceStageState) {
        val eventNanos = System.nanoTime()
        onRenderThread {
            val key = stage to provider
            if (state == TraceStageState.STARTED) {
                activeTraceStages.remove(key)?.finish(TraceStageState.CANCELLED, eventNanos)
                val stopwatch = TraceStopwatch(stage, provider, eventNanos)
                activeTraceStages[key] = stopwatch
                appendTrace(stopwatch)
                if (traceRefresh == null) {
                    val r = runner()
                    traceRefresh = r?.scheduleRepeating({ r.runOnRenderThread {
                        if (!stopped && activeTraceStages.isNotEmpty()) redrawTrace()
                    } }, Duration.ofSeconds(1))
                }
            } else {
                activeTraceStages.remove(key)?.finish(state, eventNanos)
                if (activeTraceStages.isEmpty()) stopTraceRefresh()
            }
            redrawTrace()
        }
    }

    fun finishTraceStages(state: TraceStageState) {
        require(state != TraceStageState.STARTED)
        val eventNanos = System.nanoTime()
        onRenderThread { finishTraceStagesOnRenderThread(state, eventNanos); redrawTrace() }
    }
    private fun finishTraceStagesOnRenderThread(state: TraceStageState, eventNanos: Long) {
        activeTraceStages.forEach { (key, stopwatch) ->
            stopwatch.finish(state, eventNanos)
            stageStates[key.first] = if (state == TraceStageState.COMPLETED) StageState.DONE else StageState.FAILED
        }
        activeTraceStages.clear()
        stopTraceRefresh()
        busyDepth = 0
        statusText = null
    }
    private fun stopTraceRefresh() { traceRefresh?.cancel(); traceRefresh = null }
    private fun redrawTrace() { runner()?.tuiRunner()?.dispatch(
        TickEvent(++traceTick, Duration.ofNanos((System.nanoTime() - startedNanos).coerceAtLeast(0))),
    ) }
    fun startBusy() = onRenderThread { busyDepth++; statusText = PHRASES.random() }
    fun stopBusy() = onRenderThread { busyDepth = (busyDepth - 1).coerceAtLeast(0); if (busyDepth == 0) statusText = null }

    private fun selectView(index: Int) {
        tabState.select(index)
        runner()?.focusManager()?.setFocus(when (index) {
            1 -> CANDIDATE_ID; 2 -> TRACE_ID; 3 -> EVIDENCE_ID; else -> PROMPT_ID
        })
    }
    private fun borderFor(id: String): Color =
        if (runner()?.focusManager()?.focusedId() == id) Color.GREEN else Color.CYAN
    private fun badge(label: String, bg: Color): Element =
        text(" $label ").fg(Color.BLACK).bg(bg).bold().constraint(Constraint.length(CharWidth.of(label) + 2))
    private fun gap(): Element = text(" ").constraint(Constraint.length(1))

    private fun header(width: Int): Element {
        val cells = mutableListOf<Element>(badge(title, Color.GREEN), gap(), text(mode).fg(Color.MAGENTA).bold())
        cells += text("").constraint(Constraint.fill())
        if (width >= 105) features.forEachIndexed { i, f -> cells += gap(); cells += badge(f, BADGE_COLORS[i % BADGE_COLORS.size]) }
        return row(*cells.toTypedArray()).constraint(Constraint.length(1))
    }
    private fun flowRow(width: Int): Element? {
        if (flow.isEmpty()) return null
        val cells = mutableListOf<Element>()
        cells += badge("FLOW", Color.CYAN)
        for (item in flow) {
            cells += gap()
            if (item in CONNECTORS) cells += text(item).fg(Color.CYAN).constraint(Constraint.length(CharWidth.of(item)))
            else {
                val label = when (item) {
                    "readCalendar" -> "calendar"
                    "jevDecision" -> "Jev"
                    "assembleRequest" -> "identify"
                    else -> item
                }
                val (mark, color) = when (stageStates[item]) {
                    StageState.ACTIVE -> "●" to Color.YELLOW
                    StageState.DONE -> "✓" to Color.GREEN
                    StageState.FAILED -> "✘" to Color.RED
                    else -> "·" to Color.CYAN
                }
                cells += text("$label $mark").fg(color).bold().constraint(Constraint.length(CharWidth.of(label) + 2))
            }
        }
        if (width < 105) {
            val active = stageStates.filterValues { it == StageState.ACTIVE }.keys.joinToString(" / ").ifBlank { outcome.label }
            return row(badge("FLOW", Color.CYAN), gap(), text(active).fg(Color.YELLOW).bold()).constraint(Constraint.length(1))
        }
        return row(*cells.toTypedArray()).constraint(Constraint.length(1))
    }
    private fun outcomeColor() = when (outcome) {
        DemoOutcome.BLOCKED, DemoOutcome.UNCONFIRMED -> Color.RED
        DemoOutcome.DELIVERED, DemoOutcome.PROPOSAL, DemoOutcome.READY, DemoOutcome.CHAT -> Color.GREEN
        else -> Color.YELLOW
    }
    private fun elapsedSeconds(): Long = turnStartedNanos?.let {
        ((turnFinishedNanos ?: System.nanoTime()) - it).coerceAtLeast(0) / 1_000_000_000
    } ?: 0
    private fun evidenceText(): String = buildString {
        appendLine("TURN $turn · ${elapsedSeconds()}s · agent tool calls $toolCalls · skill reads $skillReads")
        if (decisionLines.isNotEmpty()) {
            appendLine("DECISION MODEL")
            decisionLines.forEach { appendLine(it) }
            appendLine()
        }
        appendLine("Review: $verdict · candidate ${candidate?.attempt ?: 0}/$candidateLimit")
        appendLine("Human: $human")
        appendLine("Delivery: $delivery")
        appendLine("Memory: $memoryStatus · saved this session: $savedFacts")
        receiptId?.let { appendLine("Receipt: $it") }
        candidate?.candidateId?.let { appendLine("Candidate: $it") }
        appendLine("Langfuse: ${if (telemetryEnabled) "enabled; verify backend arrival" else "not configured"}")
        appendLine("Trace coverage: agent/API calls and CLI node handoffs.")
        append("Human review is a graph node; mock send and final ingestion are outside the agent trace.")
    }
    private fun candidateText(): String = candidate?.let {
        buildString {
            appendLine("**To:** ${it.recipient}\n**Event:** ${it.eventId}")
            appendLine("\n${it.message}")
            appendLine("\n**Hallway script:** ${it.hallwayScript}")
            if (feedback.isNotBlank()) appendLine("\n**Critic feedback:** $feedback")
            append("\n**Current instruction:** ${it.instruction}")
        }
    } ?: "The current draft will appear here before critic review.\n\nThe message stays pinned through refinement and human approval."

    private fun updateChat(width: Int) {
        val lines = chatMessages.flatMap { it.rows(width) }.takeLast(MAX_ROWS)
        chatList.elements(*lines.map { richText(Text.from(it)) }.toTypedArray())
    }
    private fun updateTrace(width: Int) {
        val now = System.nanoTime()
        traceList.elements(*traceLines.flatMap { entry -> wrap(entry.text(now), width).map { traceText(it, entry.kind) } }
            .takeLast(MAX_ROWS).toTypedArray())
    }
    private var candidateCache: Pair<String, Int>? = null
    private var candidateRows = emptyList<Line>()
    private fun updateCandidate(width: Int) {
        val key = candidateText() to width
        if (candidateCache != key) { candidateRows = markdownLines(key.first, width); candidateCache = key }
        candidateList.elements(*candidateRows.map { richText(Text.from(it)) }.toTypedArray())
    }
    private fun updateEvidence(width: Int) {
        evidenceList.elements(*wrap(evidenceText(), width).map { text(it).fg(Color.CYAN) }.toTypedArray())
    }

    override fun render(): Element {
        val size = runner()?.tuiRunner()?.terminal()?.size()
        val width = size?.width() ?: 120
        val height = size?.height() ?: 40
        val selected = tabState.selected() ?: 0
        val wide = width >= 120 && height >= 32
        val leftWidth = if (wide && selected == 0) width * 58 / 100 else width
        val rightWidth = width - leftWidth
        updateChat((leftWidth - 4).coerceAtLeast(20))
        updateCandidate((leftWidth - 4).coerceAtLeast(20))
        updateTrace(((if (wide && selected == 0) rightWidth else width) - 4).coerceAtLeast(20))
        updateEvidence(((if (wide && selected == 0) rightWidth else width) - 4).coerceAtLeast(20))
        fun chatPanel() = panel("CONVERSATION", chatList).rounded().borderColor(borderFor(CHAT_ID)).constraint(Constraint.fill())
        fun candidatePanel() = panel("${candidate?.flavor ?: "CURRENT CANDIDATE"} / $verdict / F2", candidateList).rounded().borderColor(borderFor(CANDIDATE_ID)).constraint(Constraint.fill())
        fun tracePanel() = panel("LIVE TRACE / ${toolCalls} agent tool calls", traceList).rounded().borderColor(borderFor(TRACE_ID)).constraint(Constraint.fill())
        fun evidencePanel() = panel("EVIDENCE", evidenceList).rounded().borderColor(borderFor(EVIDENCE_ID)).constraint(Constraint.fill())
        val body: Element = when (selected) {
            1 -> candidatePanel()
            2 -> tracePanel()
            3 -> evidencePanel()
            else -> if (wide) row(
                column(chatPanel().constraint(Constraint.percentage(40)), candidatePanel().constraint(Constraint.percentage(60)))
                    .constraint(Constraint.percentage(58)),
                column(tracePanel(), evidencePanel().constraint(Constraint.length(if (receiptId != null) 13 else 10)))
                    .constraint(Constraint.percentage(42)),
            ).constraint(Constraint.fill())
            else if (height >= 36) column(chatPanel(), candidatePanel()).constraint(Constraint.fill())
            else if (candidate != null) candidatePanel() else chatPanel()
        }
        val summary = "TURN $turn · ${elapsedSeconds()}s · agent tools $toolCalls · skill reads $skillReads · ${candidate?.let { "review ${it.attempt}/$candidateLimit" } ?: "ready"}"
        val decisionLine = when (outcome) {
            DemoOutcome.HUMAN -> if (width >= 105) "SEND = approve exact message   HOLD = no delivery   Or type a change for a fresh review"
                else "send: approve this message · hold: stop · or type a change"
            DemoOutcome.BLOCKED -> "No send override. $outcomeDetail"
            else -> outcomeDetail
        }
        val status = panel(outcome.label,
            text(CharWidth.substringByWidth(summary, (width - 4).coerceAtLeast(2))).fg(outcomeColor()).bold(),
            text(CharWidth.substringByWidth(decisionLine, (width - 4).coerceAtLeast(2))).fg(outcomeColor()),
        ).rounded().borderColor(outcomeColor()).constraint(Constraint.length(4))
        promptElement.placeholder(if (outcome == DemoOutcome.HUMAN) "send / hold / tell me what to change…" else "Ask j-claw; Enter submits…")
        val busyLine = if (busyDepth > 0) row(busySpinner, gap(), text(statusText ?: "Working").fg(Color.YELLOW))
            else row(text(providerLegend).fg(Color.MAGENTA))
        return column(
            header(width),
            *listOfNotNull(flowRow(width)).toTypedArray(),
            status,
            row(viewTabs).constraint(Constraint.length(1)),
            body,
            busyLine.constraint(Constraint.length(1)),
            panel(if (outcome == DemoOutcome.HUMAN) "HUMAN DECISION" else "PROMPT", promptElement)
                .rounded().borderColor(borderFor(PROMPT_ID)).constraint(Constraint.length(3)),
            row(text(if (width >= 105) "TamboUI · F1 live · F2 candidate · F3 trace · F4 evidence · Tab focus · PgUp/PgDn scroll · Ctrl+C quit"
                else "TamboUI · F1 live · F2 message · F3 trace · F4 evidence · Tab focus · Ctrl+C quit").fg(Color.CYAN))
                .constraint(Constraint.length(1)),
        ).bg(Color.BLACK).fg(Color.WHITE).onKeyEvent { event ->
            val index = when (event.code()) { KeyCode.F1 -> 0; KeyCode.F2 -> 1; KeyCode.F3 -> 2; KeyCode.F4 -> 3; else -> -1 }
            if (index < 0) EventResult.UNHANDLED else { selectView(index); EventResult.HANDLED }
        }
    }

    private fun traceText(line: String, kind: TraceKind): StyledElement<*> = text(line).fg(when (kind) {
        TraceKind.SUBGRAPH_START -> Color.MAGENTA
        TraceKind.SUBGRAPH_END -> Color.GREEN
        TraceKind.TOOL_CALL, TraceKind.LLM -> Color.CYAN
        TraceKind.RUNNING -> Color.YELLOW
        TraceKind.ERROR -> Color.RED
    })

    private class ChatMessage(val source: String, val kind: ChatKind) {
        private var width = -1
        private var cached = emptyList<Line>()
        fun rows(newWidth: Int): List<Line> {
            if (width != newWidth) { width = newWidth; cached = chatRows(source, kind, width) }
            return cached
        }
    }
    companion object {
        private const val MAX_MESSAGES = 150
        private const val MAX_TRACE = 1000
        private const val MAX_ROWS = 600
        private const val CHAT_ID = "chat-list"
        private const val TRACE_ID = "trace-list"
        private const val CANDIDATE_ID = "candidate-list"
        private const val EVIDENCE_ID = "evidence-list"
        private const val PROMPT_ID = "jclaw-prompt"
        private val VIEW_NAMES = arrayOf("F1 LIVE", "F2 CANDIDATE", "F3 TRACE", "F4 EVIDENCE")
        private val BADGE_COLORS = listOf(Color.CYAN, Color.MAGENTA, Color.YELLOW)
        private val CONNECTORS = setOf("→", "⇄", "↺", "->", "<->")
        private val TERMINAL_OUTCOMES = setOf(DemoOutcome.READY, DemoOutcome.PROPOSAL, DemoOutcome.HELD,
            DemoOutcome.DELIVERED, DemoOutcome.BLOCKED, DemoOutcome.UNCONFIRMED, DemoOutcome.CHAT)
        private val originalOut = System.out
        private val originalErr = System.err
        fun quietStdStreams(path: Path) {
            val log = PrintStream(FileOutputStream(path.toFile(), true), true, Charsets.UTF_8)
            System.setOut(log); System.setErr(log)
        }
        fun restoreStdStreams() { System.setOut(originalOut); System.setErr(originalErr) }
        private val WRAP = (System.getenv("JCLAW_WRAP")?.toIntOrNull() ?: 88).coerceAtLeast(20)
        private fun chatRows(line: String, kind: ChatKind, width: Int): List<Line> {
            if (kind == ChatKind.JCLAW) return markdownLines(line, width)
            val style = when (kind) {
                ChatKind.YOU -> Style.EMPTY.fg(Color.GREEN).bold()
                ChatKind.OK -> Style.EMPTY.fg(Color.GREEN).bold()
                ChatKind.ERR -> Style.EMPTY.fg(Color.RED).bold()
                ChatKind.TOOL_RESULT -> Style.EMPTY.fg(Color.YELLOW)
                ChatKind.JCLAW -> Style.EMPTY.fg(Color.BLUE)
            }
            return wrap(line, width).map { Line.from(Span.styled(it, style)) }
        }
        /** Wrap by terminal columns, preserving wide glyphs and embedded paragraph breaks. */
        fun wrap(text: String, width: Int = WRAP): List<String> {
            val columns = width.coerceAtLeast(2)
            val out = mutableListOf<String>()
            for (paragraph in text.replace("\r", "").replace("\t", "  ").split("\n")) {
                if (paragraph.isEmpty()) { out += ""; continue }
                var current = ""
                for (word in paragraph.split(" ").filter { it.isNotEmpty() }) {
                    var remaining = word
                    while (CharWidth.of(remaining) > columns) {
                        if (current.isNotEmpty()) { out += current; current = "" }
                        val chunk = CharWidth.substringByWidth(remaining, columns)
                        out += chunk
                        remaining = remaining.substring(chunk.length)
                    }
                    if (remaining.isEmpty()) continue
                    val next = if (current.isEmpty()) remaining else "$current $remaining"
                    if (CharWidth.of(next) > columns) { out += current; current = remaining } else current = next
                }
                if (current.isNotEmpty()) out += current
            }
            return out
        }
        private val PHRASES = listOf("Thinking", "Considering the request", "Looking for the right words",
            "Waiting for the next token", "Giving the GPUs a moment", "Almost certainly overthinking this",
            "Working through the details", "Keeping the cursor company", "Making every token count")
    }
}
