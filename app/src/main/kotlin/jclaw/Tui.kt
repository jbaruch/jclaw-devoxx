package jclaw

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import jclaw.Observability.langfuse
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.search.SimilaritySearchStrategy
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import com.jbaruch.jclaw.tui.ChatKind
import com.jbaruch.jclaw.tui.CandidateView
import com.jbaruch.jclaw.tui.DemoOutcome
import com.jbaruch.jclaw.tui.JclawTui
import com.jbaruch.jclaw.tui.StageState
import com.jbaruch.jclaw.tui.TraceKind
import com.jbaruch.jclaw.tui.TraceStageState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlin.io.path.Path
import kotlin.system.exitProcess

/**
 * Devoxx workflow and guardrails with the TamboUI stage dashboard.
 *
 * Same pipeline and JCLAW_NAIVE switch. The difference is
 * that the subtask boundaries and tool calls land in a TRACE pane where they
 * stay legible at streaming resolution, instead of racing past in a log.
 *
 * The TUI owns the main thread; the agent runs on its own scope. Every
 * agent -> UI call marshals through the render thread inside JclawTui, per
 * the tamboui render-thread-discipline rule.
 */
fun main(args: Array<String>) {
    JclawTui.quietStdStreams(Path("jclaw-tui.log"))
    val apiKey = requireNotNull(System.getenv("GOOGLE_API_KEY")) { "GOOGLE_API_KEY is not set" }
    val naive = System.getenv("JCLAW_NAIVE") == "1"
    val reviewOnly = System.getenv("JCLAW_REVIEW_ONLY") == "1"
    val jev = JevClient.configured()

    val submissions = Channel<String>(Channel.UNLIMITED)
    val tui = JclawTui(
        onSubmit = { submissions.trySend(it) },
        features = listOfNotNull("MCP", "MEMORY".takeUnless { naive }, "SKILLS", "JEV".takeIf { jev != null }, "WORKFLOW",
            "GUARDRAILS".takeUnless { reviewOnly }, "LANGFUSE".takeIf { Observability.enabled }),
        flow = (if (jev != null) listOf("readCalendar", "→", "jevDecision", "→", "assembleRequest") else listOf("classify", "→", "identify")) +
            listOf("→", "deploy", "→", "verify", "⇄", "refine") +
            if (reviewOnly) emptyList() else listOf("→", "human", "→", "send", "→", "memory"),
        mode = when {
            reviewOnly -> "KOOG / R5 WORKFLOWS"
            System.getenv("JCLAW_ROUND") == "7" -> "KOOG / R7 OBSERVABILITY"
            else -> "KOOG / R6 GUARDRAILS"
        },
        reviewOnly = reviewOnly,
        telemetryEnabled = Observability.enabled,
        candidateLimit = jclaw.domain.WorkflowPolicy.maxCandidates,
        providerLegend = "${if (jev != null) jclaw.domain.JevProtocol.model else Models.flash.id} decisions · Gemini chat → Claude Code → Codex CLI | organizer: mock",
    )

    // The agent is created inside its scope; closing it must happen from the TUI's shutdown path.
    var closeAgent: (suspend () -> Unit)? = { jev?.close() }

    val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("jclaw-agent"))
    agentScope.launch {
        try {
            val mcp = Mcp.boot("calendar-mcp", "organizer-mcp", onStderr = { tui.trace(it, TraceKind.TOOL_CALL) })
            // Also clean up if skills, embeddings or agent construction fail during startup.
            closeAgent = { jev?.close(); mcp.close() }
            val skills = AgentSkills.discover(trace = { tui.trace(it, TraceKind.TOOL_CALL) })
            val memory = Memory.open(
                LLMEmbedder(GoogleLLMClient(apiKey), GoogleModels.Embeddings.GeminiEmbedding001),
                trace = { tui.trace(it.trim(), TraceKind.TOOL_CALL) },
            )

            tui.trace("mode: " + if (naive) "NAIVE — less context, no memory" else "DOMAIN-MODELLED", TraceKind.SUBGRAPH_START)
            tui.trace("models: ${if (jev != null) jclaw.domain.JevProtocol.model else Models.flash.id} decisions · Gemini chat → Claude subscription → Codex subscription", TraceKind.SUBGRAPH_START)

            val conversation = Conversation("${Persona.PROMPT}\n${skills.prompt}")
            val agent = AIAgent(
                id = "j-claw",   // names the agent spans in Langfuse; a UUID otherwise
                promptExecutor = simpleGoogleAIExecutor(apiKey),
                agentConfig = AIAgentConfig.withSystemPrompt(
                    prompt = conversation.systemPrompt,
                    llm = Models.flash,
                    maxAgentIterations = 200,
                ),
                strategy = jclawStrategy(mcp, naive, skills,
                    jev = jev, memory = memory, proposedFlavors = conversation::proposedFlavors,
                    onDecision = { evidence ->
                        tui.decision(evidence.lines())
                        evidence.lines().forEach { tui.trace(it, TraceKind.SUBGRAPH_END) }
                    },
                    onStage = { stage, model, state ->
                        tui.traceStage(stage, model, when (state) {
                            PipelineStageState.STARTED -> TraceStageState.STARTED
                            PipelineStageState.COMPLETED -> TraceStageState.COMPLETED
                            PipelineStageState.FAILED -> TraceStageState.FAILED
                        })
                        tui.stage(stage, when (state) {
                            PipelineStageState.STARTED -> StageState.ACTIVE
                            PipelineStageState.COMPLETED -> StageState.DONE
                            PipelineStageState.FAILED -> StageState.FAILED
                        })
                        if (stage != "human" && state == PipelineStageState.STARTED) tui.startBusy() else tui.stopBusy()
                    },
                    humanReview = if (reviewOnly) null else { attempt ->
                        val request = requireNotNull(attempt.request)
                        val ready = JclawResult.ReadyToSend(attempt.plan, request)
                        tui.candidateIdentity(sendEnvelope(ready).candidateId)
                        tui.chat("Codex approved this exact candidate. Human critic: send, hold, or tell me what to change.", ChatKind.OK)
                        tui.chat("recipient: ${request.organizerName}; event: ${request.eventId}", ChatKind.OK)
                        tui.outcome(DemoOutcome.HUMAN)
                        humanReview(submissions.receive().trim())
                    },
                    onReview = { event ->
                        if (event is ReviewEvent.Draft) conversation.rememberProposal(event.attempt)
                        tui.chat(event.chatText(), ChatKind.JCLAW)
                        when (event) {
                            is ReviewEvent.Draft -> {
                                val request = requireNotNull(event.attempt.request)
                                val plan = event.attempt.plan
                                tui.candidate(CandidateView(plan.flavor.name, request.organizerName, request.eventId,
                                    plan.messageToOrganizer, plan.hallwayScript, request.userInstruction,
                                    event.attempt.refinements + 1))
                            }
                            is ReviewEvent.Verdict -> tui.reviewResult(event.critique.approved, event.critique.feedback)
                        }
                    },
                ),
                toolRegistry = mcp.registry + skills.registry,
            ) {
                install(ChatMemory) { conversation.configure(this) }

                // Real traces, when there is somewhere to send them: see Observability.
                if (Observability.enabled) install(OpenTelemetry) {
                    langfuse(
                        round = System.getenv("JCLAW_ROUND")?.toIntOrNull() ?: if (reviewOnly) 5 else 6,
                        if (naive) "naive" else "domain-modelled",
                        "critic:codex", "drafter:claude-code",
                        metadata = mapOf("model" to Models.flash.id, "decider" to if (jev != null) jclaw.domain.JevProtocol.model else Models.flash.id,
                            "drafter" to "claude-code", "critic" to "codex"),
                    )
                }
                if (!naive) install(LongTermMemory) {
                    retrieval {
                        storage = memory
                        searchStrategy = SimilaritySearchStrategy(topK = 5)
                    }
                }
                handleEvents {
                    onSubgraphExecutionStarting {
                        if (it.subgraph.name in setOf("classify", "identify", "chatReply")) {
                            tui.traceStage(it.subgraph.name, "${Models.flash.id} (API)", TraceStageState.STARTED)
                        } else {
                            tui.trace("┌─ ▶ ${it.subgraph.name}", TraceKind.SUBGRAPH_START)
                        }
                        tui.stage(it.subgraph.name, StageState.ACTIVE)
                    }
                    onSubgraphExecutionCompleted {
                        if (it.subgraph.name in setOf("classify", "identify", "chatReply")) {
                            tui.traceStage(it.subgraph.name, "${Models.flash.id} (API)", TraceStageState.COMPLETED)
                        } else {
                            tui.trace("└─ ✓ ${it.subgraph.name}", TraceKind.SUBGRAPH_END)
                        }
                        tui.stage(it.subgraph.name, StageState.DONE)
                    }
                    onToolCallStarting { tui.toolCall(it.toolName, it.toolArgs.toString()) }
                    onLLMCallStarting { _ -> tui.startBusy() }
                    onLLMCallCompleted { _ -> tui.stopBusy() }
                }
            }
            closeAgent = { agent.close(); jev?.close(); Observability.flush(); mcp.close() }

            tui.chat(
                Persona.WELCOME,
                ChatKind.OK,
            )
            tui.outcome(DemoOutcome.READY, "Ask for a plan, inspect the workflow, then decide what may be sent")

            // A program argument, if given, is asked on startup (the smoke tests use it); on
            // stage the sentence is pasted. JclawTui echoes what you type, so only the
            // argument needs echoing here.
            var next: String? = args.joinToString(" ").ifBlank { null }
            next?.let { tui.chat("you: $it", ChatKind.YOU) }

            while (true) {
                val prompt = next ?: submissions.receive()
                next = null
                var deliveryAttempted = false
                try {
                    tui.resetFlow()
                    val result = conversation.run(agent, prompt)
                    tui.finishTraceStages(TraceStageState.COMPLETED)
                    if (result is JclawResult.ChatReply) {
                        tui.chat("j-claw: ${result.text}", ChatKind.JCLAW)
                        tui.outcome(DemoOutcome.CHAT, "Assistant reply complete")
                        continue
                    }
                    if (result is JclawResult.Blocked) {
                        tui.stage("verify", StageState.FAILED)
                        tui.chat("BLOCKED: ${result.reason}", ChatKind.ERR)
                        tui.chat("Nothing was sent. There is no send override.", ChatKind.ERR)
                        tui.outcome(DemoOutcome.BLOCKED, result.reason)
                        continue
                    }
                    if (result is JclawResult.Held) {
                        tui.chat("j-claw: held. Nothing was sent.", ChatKind.OK)
                        tui.outcome(DemoOutcome.HELD, "The human critic stopped this candidate. Nothing was sent.")
                        continue
                    }
                    val ready = when (result) {
                        is JclawResult.ReadyToSend -> result
                        is JclawResult.HumanApproved -> result.ready
                    }
                    val plan = ready.deployment
                    tui.candidateIdentity(sendEnvelope(ready).candidateId)
                    tui.chat("j-claw: ✓ Codex approved — flavor ${plan.flavor}", ChatKind.OK)
                    tui.chat("j-claw: ${plan.messageToOrganizer}", ChatKind.JCLAW)
                    tui.chat("j-claw: hallway script → ${plan.hallwayScript}", ChatKind.JCLAW)
                    tui.chat("recipient: ${ready.request.organizerName}; event: ${ready.request.eventId}", ChatKind.OK)
                    if (reviewOnly) {
                        tui.chat("REVIEWED PROPOSAL. Human confirmation and sending are disabled in this round.", ChatKind.OK)
                        tui.outcome(DemoOutcome.PROPOSAL, "Round 5 ends here. Human approval and delivery belong to guardrails.")
                        continue
                    }
                    check(result is JclawResult.HumanApproved) { "Human approval is required" }
                    deliveryAttempted = true
                    tui.outcome(DemoOutcome.SENDING, "Sending the exact approved candidate to the organizer mock")
                    tui.stage("send", StageState.ACTIVE)
                    sendAndRemember(ready, mcp, memory,
                        onDelivered = { receipt ->
                            tui.chat("j-claw: delivered. $receipt", ChatKind.OK)
                            tui.deliveryConfirmed(receipt.callId)
                            conversation.assistant("Delivered. Organizer receipt: $receipt")
                        },
                        onMemoryFailure = {
                            tui.memoryFailed()
                            tui.chat("Delivered, but could not save to memory: ${it.message}", ChatKind.ERR)
                        },
                        onMemorySaved = { tui.memorySaved() },
                    )
                } catch (c: CancellationException) {
                    tui.finishTraceStages(TraceStageState.CANCELLED)
                    throw c
                } catch (t: Throwable) {
                    tui.finishTraceStages(TraceStageState.FAILED)
                    if (deliveryAttempted) tui.deliveryFailed(t !is DeliveryRejected)
                    else tui.outcome(DemoOutcome.BLOCKED, t.message ?: t.javaClass.simpleName)
                    tui.chat(
                        if (deliveryAttempted) deliveryFailure(t)
                        else "BLOCKED: ${t.message ?: t.javaClass.simpleName}. Nothing was sent.",
                        ChatKind.ERR,
                    )
                    t.printStackTrace()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            tui.outcome(DemoOutcome.BLOCKED, "Startup failed: ${error.message}. Ctrl+C exits.")
            tui.chat("Startup failed: ${error.message}", ChatKind.ERR)
            tui.trace("Startup failed: ${error.message}", TraceKind.ERROR)
            error.printStackTrace()
        }
    }

    try {
        tui.run()
    } catch (t: Throwable) {
        JclawTui.restoreStdStreams()
        println("j-claw TUI died: $t - details in jclaw-tui.log")
        t.printStackTrace()
        exitProcess(1)
    } finally {
        runBlocking { agentScope.coroutineContext[Job]?.cancelAndJoin() }
        // Closing ends the spans Koog still holds; the flush ships them (see Observability).
        closeAgent?.let { runBlocking { it() } }
        // Same reason as the CLI front end: the MCP reader thread will not let the
        // JVM exit once the TUI has been closed.
        exitProcess(0)
    }
}
