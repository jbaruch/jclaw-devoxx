package jclaw

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.search.SimilaritySearchStrategy
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import jclaw.Observability.langfuse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/** Jev decides, Gemini chats, Claude drafts/refines, Codex judges. The app owns sending. */
fun main(): Unit = runBlocking {
    val apiKey = requireNotNull(System.getenv("GOOGLE_API_KEY")) { "GOOGLE_API_KEY is not set" }
    val naive = System.getenv("JCLAW_NAIVE") == "1"
    val autoSend = System.getenv("JCLAW_AUTOSEND") == "1"
    val reviewOnly = System.getenv("JCLAW_REVIEW_ONLY") == "1"
    JevClient.configured().use { jev ->
    Mcp.boot("calendar-mcp", "organizer-mcp").use { mcp ->
        val skills = AgentSkills.discover()
        val memory = Memory.open(LLMEmbedder(GoogleLLMClient(apiKey), GoogleModels.Embeddings.GeminiEmbedding001))
        println("[mode] " + if (naive) "NAIVE - less context and no memory" else "DOMAIN-MODELLED")
        println("[models] ${if (jev != null) jclaw.domain.JevProtocol.model else Models.flash.id} routes/identifies; " +
            "${Models.flash.id} chats; Claude subscription drafts/refines; Codex subscription judges")
        val conversation = Conversation("${Persona.PROMPT}\n${skills.prompt}")
        val agent = AIAgent(
            id = "j-claw",
            promptExecutor = simpleGoogleAIExecutor(apiKey),
            agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = conversation.systemPrompt, llm = Models.flash, maxAgentIterations = 200,
            ),
            strategy = jclawStrategy(
                mcp, naive, skills,
                onStage = { stage, model, state -> println("[$stage] $model - $state") },
                onReview = {
                    if (it is ReviewEvent.Draft) conversation.rememberProposal(it.attempt)
                    println("\n${it.chatText()}\n")
                },
                jev = jev, memory = memory, proposedFlavors = conversation::proposedFlavors,
                onDecision = { it.lines().forEach { line -> println("[decision] $line") } },
                humanReview = if (reviewOnly) null else { attempt ->
                    val plan = attempt.plan
                    val request = requireNotNull(attempt.request)
                    println("=== HUMAN REVIEW: CODEX APPROVED THIS CANDIDATE ===")
                    println("flavor: ${plan.flavor}\nmessage: ${plan.messageToOrganizer}\nhallway: ${plan.hallwayScript}")
                    println("recipient: ${request.organizerName}; event: ${request.eventId}")
                    print("Send it? [send/y/N, or tell me what to change] ")
                    val answer = if (autoSend) { println("y (mock rehearsal)"); "y" }
                        else readlnOrNull()?.trim().orEmpty()
                    humanReview(answer)
                },
            ),
            toolRegistry = mcp.registry + skills.registry,
        ) {
            install(ChatMemory) { conversation.configure(this) }
            if (Observability.enabled) install(OpenTelemetry) {
                langfuse(
                    System.getenv("JCLAW_ROUND")?.toIntOrNull() ?: if (reviewOnly) 5 else 6,
                    if (naive) "naive" else "domain-modelled", "critic:codex", "drafter:claude-code",
                    metadata = mapOf("model" to Models.flash.id, "decider" to if (jev != null) jclaw.domain.JevProtocol.model else Models.flash.id,
                        "drafter" to "claude-code", "critic" to "codex"),
                )
            }
            if (!naive) install(LongTermMemory) {
                retrieval { storage = memory; searchStrategy = SimilaritySearchStrategy(topK = 5) }
            }
            handleEvents { onToolCallStarting { println("      tool ${it.toolName}") } }
        }
        try {
            println("\n${Persona.WELCOME} Blank line or ctrl-D quits.\n")
            while (true) {
                print("you: ")
                val line = readlnOrNull()?.trim()
                if (line.isNullOrEmpty()) break
                var deliveryAttempted = false
                try {
                    val result = conversation.run(agent, line)
                    when (result) {
                        is JclawResult.ChatReply -> println("j-claw: ${result.text}")
                        is JclawResult.Blocked -> println("BLOCKED: ${result.reason}\nNothing was sent. There is no send override.")
                        is JclawResult.Held -> println("held. Nothing was sent.")
                        is JclawResult.ReadyToSend -> {
                            val plan = result.deployment
                            println("=== CODEX APPROVED THIS PLAN ===")
                            println("flavor: ${plan.flavor}\nmessage: ${plan.messageToOrganizer}\nhallway: ${plan.hallwayScript}")
                            println("recipient: ${result.request.organizerName}; event: ${result.request.eventId}")
                            println("REVIEWED PROPOSAL. Human confirmation and sending are disabled in this round.")
                        }
                        is JclawResult.HumanApproved -> {
                            deliveryAttempted = true
                            sendAndRemember(result.ready, mcp, memory,
                                onDelivered = { receipt ->
                                    println("sent: $receipt")
                                    conversation.assistant("Delivered. Organizer receipt: $receipt")
                                },
                                onMemoryFailure = { println("Delivered, but could not save to memory: ${it.message}") },
                            )
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (deliveryAttempted) println(deliveryFailure(error))
                    else println("BLOCKED: ${error.message}\nNothing was sent.")
                }
                println()
            }
        } finally {
            agent.close()
            Observability.flush()
            mcp.close()
        }
        jev?.close()
        exitProcess(0)
    }
    }
}
