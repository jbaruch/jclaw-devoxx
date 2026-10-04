package jclaw

import com.jbaruch.jclaw.tui.CandidateView
import com.jbaruch.jclaw.tui.ChatKind
import com.jbaruch.jclaw.tui.DemoOutcome
import com.jbaruch.jclaw.tui.JclawTui
import com.jbaruch.jclaw.tui.StageState
import com.jbaruch.jclaw.tui.TraceKind
import com.jbaruch.jclaw.tui.TraceStageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path

/** UI inspection only. Every event below is fixture data; no model, MCP or memory calls. */
fun main() {
    JclawTui.quietStdStreams(Path("jclaw-tui.log"))
    val input = Channel<String>(Channel.UNLIMITED)
    val tui = JclawTui(
        onSubmit = { input.trySend(it) },
        title = "FIXTURE PREVIEW",
        mode = "SIMULATED / NO ACTIONS",
        features = listOf("MCP", "MEMORY", "SKILLS", "JEV", "WORKFLOW", "GUARDRAILS"),
        providerLegend = "FIXTURE EVENTS ONLY · no provider calls, deliveries or memory writes",
    )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        tui.chat("UI REHEARSAL ONLY. All model, tool, review, receipt and memory events are simulated. No external calls or writes.", ChatKind.OK)
        var approved = false
        suspend fun phase(name: String, provider: String, action: () -> Unit = {}) {
            tui.stage(name, StageState.ACTIVE)
            tui.traceStage(name, "$provider [fixture]", TraceStageState.STARTED)
            tui.startBusy()
            delay(600)
            action()
            tui.stopBusy()
            tui.traceStage(name, "$provider [fixture]", TraceStageState.COMPLETED)
            tui.stage(name, StageState.DONE)
        }
        suspend fun proposal(instruction: String) {
            approved = false
            tui.resetFlow(instruction)
            tui.chat("you: $instruction", ChatKind.YOU)
            phase("readCalendar", "Application") {
                tui.toolCall("getCalendar", "fixture")
            }
            phase("jevDecision", "Jev") {
                tui.decision(listOf("FIXTURE · jev-1.13.0 · 187ms · DECLINE",
                    "intent: EXCUSE_REQUEST · confidence 0.97 · margin 0.94",
                    "  EXCUSE_REQUEST 98.0%", "  CHAT 2.0%",
                    "event: basic-ai-proficiency-2026 · confidence 0.96 · margin 0.92"))
            }
            phase("assembleRequest", "Application") {
                tui.trace("[fixture memory] previous excuses: calendar conflict, family obligation, customer escalation", TraceKind.TOOL_CALL)
            }
            phase("deploy", "Claude Code") {
                tui.candidate(CandidateView("CALENDAR_CONFLICT", "Dana from People Ops", "basic-ai-proficiency-2026",
                    "Hi Dana, I have a conflicting meeting during Tuesday's training. Could I catch up later?",
                    "I have a conflicting meeting.", instruction, 1))
            }
            phase("verify", "Codex") {
                tui.reviewResult(false, "Calendar conflict was already used. Find a different basis.")
                tui.chat("[fixture critic] REJECTED draft 1: calendar conflict is in sent history.", ChatKind.ERR)
            }
            phase("refine", "Claude Code") {
                tui.candidate(CandidateView("ALREADY_PROFICIENT", "Dana from People Ops", "basic-ai-proficiency-2026",
                    "Hi Dana, I already teach these AI topics in my conference sessions. Would you accept equivalent evidence of proficiency instead of attendance at Tuesday's basic training? I can share my session materials or complete an assessment if that helps.",
                    "I asked Dana whether equivalent evidence of proficiency would meet the requirement.", instruction, 2))
            }
            phase("verify", "Codex") {
                tui.reviewResult(true, "This asks for an alternative without claiming an exemption is already approved.")
                tui.chat("[fixture critic] APPROVED draft 2.", ChatKind.OK)
            }
            tui.finishTraceStages(TraceStageState.COMPLETED)
            approved = true
            tui.candidateIdentity("fixture-candidate-id-not-a-real-receipt")
            tui.outcome(DemoOutcome.HUMAN, "Fixture decision: send, hold, or type a change")
        }
        proposal("Help me get out of Tuesday's Basic AI Proficiency Training without reusing an excuse.")
        while (true) {
            val answer = input.receive().trim()
            when (sendReply(answer)) {
                SendReply.Send -> if (approved) {
                    approved = false
                    tui.outcome(DemoOutcome.SENDING, "Simulated delivery only")
                    phase("send", "Organizer mock")
                    tui.deliveryConfirmed("fixture-receipt-no-delivery")
                    tui.memorySaved()
                    tui.chat("FIXTURE: simulated receipt and stored fact; nothing was delivered or persisted.", ChatKind.OK)
                } else tui.chat("Fixture candidate is no longer awaiting approval. Type another request to rehearse.", ChatKind.ERR)
                SendReply.Hold -> {
                    approved = false
                    tui.outcome(DemoOutcome.HELD, "Fixture candidate held; type a change to replay the UI")
                }
                is SendReply.FollowUp -> proposal(answer)
            }
        }
    }
    try { tui.run() }
    finally {
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        JclawTui.restoreStdStreams()
    }
}
