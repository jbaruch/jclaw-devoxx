package jclaw

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineReceipt
import jclaw.domain.DeclineRequest
import jclaw.domain.ExcuseFlavor
import jclaw.domain.Scenario
import kotlinx.serialization.json.Json

/** Rounds 1–4: one conversational agent, with only the capabilities unlocked so far. */
internal fun earlyRoundStrategy(
    stage: DemoStage,
    tools: List<ToolBase<*, *>>,
): AIAgentGraphStrategy<String, JclawResult> = strategy("j-claw-round-${stage.number}") {
    val exchange = createStorageKey<List<Message>>("early-turn")
    val beginTurn by node<String, String> { input ->
        llm.writeSession {
            appendPrompt { user(input) }
            storage.set(exchange, prompt.messages)
        }
        input
    }
    val respond by subgraphWithTask<String, String>(tools = tools, llmModel = Models.flash) { input ->
        "Reply to the current request using the conversation. ${Scenario.USER_CONTEXT}\n" +
            if (stage.tools) "Read tools for current facts. Calendar declined flags are not sent-excuse records. " +
                "Writing a draft does not authorize sending; send only when the user asks for that action. " +
                "Do not invent calendar events to manufacture an excuse.\nUSER: $input"
            else "You have no calendar, sent-history records or action tools. Be honest about those limits.\nUSER: $input"
    }
    val rememberReply by node<String, JclawResult> { reply ->
        llm.writeSession {
            val previous = storage.getValue(exchange)
            prompt = prompt.withMessages { previous }
            appendPrompt { assistant(reply) }
        }
        JclawResult.ChatReply(reply)
    }
    edge(nodeStart forwardTo beginTurn)
    edge(beginTurn forwardTo respond)
    edge(respond forwardTo rememberReply)
    edge(rememberReply forwardTo nodeFinish)
}

/** The model selects the send tool; application code owns IDs and validates its MCP receipt. */
internal class EarlyActions(
    private val mcp: Mcp,
    private val memory: Memory?,
    private val delivered: suspend (DeclineReceipt) -> Unit,
    private val memorySaved: () -> Unit,
    private val memoryFailure: (Exception) -> Unit,
) : ToolSet {
    @Tool
    @LLMDescription("Send this exact message to the selected calendar event's organizer, only when the user requests sending. The flavor describes the reason in the literal message. Returns the actual receipt. Does not invent calendar events.")
    suspend fun sendDecline(
        eventId: String,
        organizerName: String,
        message: String,
        flavor: ExcuseFlavor,
    ): String {
        val request = mcp.canonicalRequest(DeclineRequest(eventId, emptyList(), emptyList(), organizerName))
        require(request.organizerName == organizerName) { "Organizer must match the selected calendar event" }
        val ready = JclawResult.ReadyToSend(DeclineDeployment(flavor, messageToOrganizer = message, hallwayScript = ""), request)
        val receipt = if (memory == null) mcp.sendDecline(ready).also { delivered(it) }
        else sendAndRemember(ready, mcp, memory, delivered, memoryFailure, memorySaved)
        return Json.encodeToString(receipt)
    }
}

internal fun earlyRoundTools(stage: DemoStage, mcp: Mcp, actions: EarlyActions, skills: AgentSkills): ToolRegistry =
    if (!stage.tools) ToolRegistry.EMPTY
    else ToolRegistry {
        Slices(mcp.registry).read.forEach { tool(it) }
        actions.asTools().forEach { tool(it) }
        skills.registry.tools.forEach { tool(it) }
    }
