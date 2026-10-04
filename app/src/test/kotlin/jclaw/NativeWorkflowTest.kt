package jclaw

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineRequest
import jclaw.domain.ExcuseFlavor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

private fun nativeVerdict(approved: Boolean, feedback: String = "") =
    Json.parseToJsonElement("""{"isCorrect":$approved,"feedback":${Json.encodeToString(feedback)}}""")

class NativeWorkflowTest : StringSpec({
    val request = DeclineRequest("fixture-event", emptyList(), emptyList(), "Fixture organizer", "Keep it short")
    val first = DeclineDeployment(ExcuseFlavor.DEADLINE, messageToOrganizer = "First proposal", hallwayScript = "First explanation")
    val second = first.copy(messageToOrganizer = "Second proposal")
    val third = first.copy(messageToOrganizer = "Third proposal")

    fun agent(replies: List<JsonElement>, prompts: MutableList<Prompt>): AIAgent<DeclineRequest, JclawResult> = AIAgent(
        promptExecutor = ScriptedExecutor { prompt, tools ->
            val reply = replies[prompts.size]
            prompts += prompt
            val finish = tools.single { it.name == "finalize_task_result" }
            Message.Assistant(MessagePart.Tool.Call("native-${prompts.size}", finish.name,
                JsonObject(mapOf("result" to reply)).toString()), ResponseMetaInfo.Empty)
        },
        llmModel = Models.flash,
        systemPrompt = "Fixture-only deterministic execution; no external tools.",
        maxIterations = 200,
        strategy = nativeWorkflow(Models.flash, Models.flash, maxRefinements = 2),
    )

    "native verification approves the exact revised candidate and carries current constraints" {
        val prompts = mutableListOf<Prompt>()
        val agent = agent(listOf(Json.encodeToJsonElement(first), nativeVerdict(false, "Shorten it"),
            Json.encodeToJsonElement(second), nativeVerdict(true)), prompts)
        try {
            agent.run(request) shouldBe JclawResult.ReadyToSend(second, request)
            prompts.size shouldBe 4
            prompts[1].messages.joinToString { it.textContent() } shouldContain request.userInstruction
            prompts[2].messages.joinToString { it.textContent() } shouldContain "Shorten it"
            prompts[3].messages.joinToString { it.textContent() } shouldContain second.messageToOrganizer
        } finally { agent.close() }
    }

    "native verification blocks after two refinements and a new run has its own allowance" {
        val prompts = mutableListOf<Prompt>()
        val cycle = listOf(Json.encodeToJsonElement(first), nativeVerdict(false, "Fixture rejection"),
            Json.encodeToJsonElement(second), nativeVerdict(false, "Fixture rejection"),
            Json.encodeToJsonElement(third), nativeVerdict(false, "Fixture rejection"))
        val agent = agent(cycle + cycle, prompts)
        try {
            repeat(2) {
                agent.run(request) shouldBe JclawResult.Blocked("Native critic rejected after 2 refinements: Fixture rejection", third)
            }
            prompts.size shouldBe 12
        } finally { agent.close() }
    }
})
