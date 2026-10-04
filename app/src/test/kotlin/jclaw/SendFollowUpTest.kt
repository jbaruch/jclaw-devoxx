package jclaw

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jclaw.domain.*
import kotlinx.serialization.json.*

class SendFollowUpTest : StringSpec({
    val request = DeclineRequest(Scenario.EVENT_ID, Scenario.BURNED, Scenario.ATTENDEES, Scenario.ORGANIZER)
    val original = DeclineDeployment(ExcuseFlavor.ALREADY_PROFICIENT,
        messageToOrganizer = "Original candidate", hallwayScript = "Original hallway answer")
    val automatic = original.copy(flavor = ExcuseFlavor.DEADLINE, messageToOrganizer = "Automatic refinement")
    val humanRevision = original.copy(flavor = ExcuseFlavor.EXISTENTIAL_CRISIS, messageToOrganizer = "Human-requested refinement")

    "only exact affirmatives approve and substantive answers critique the current candidate" {
        listOf("send", "SEND", " y ", "yes").forEach { humanReview(it).choice shouldBe HumanChoice.APPROVE }
        listOf("", "n", "no", "Hold", "no thanks.", "don't send", "cancel").forEach {
            humanReview(it).choice shouldBe HumanChoice.HOLD
        }
        listOf("no, let's use another one", "rewrite in corporate-speak", "yes, but change the reason").forEach {
            humanReview(it) shouldBe HumanReview(HumanChoice.REJECT, it)
        }
    }

    "Codex and human rejections share the same graph request and refinement budget" {
        val conversation = Conversation("Generic assistant")
        val prompts = mutableListOf<Prompt>()
        val drafts = mutableListOf<DeclineRequest>()
        val refinements = mutableListOf<String>()
        val reviews = mutableListOf<DeclineReview>()
        val humanAttempts = mutableListOf<ReviewAttempt>()
        val stages = mutableListOf<String>()
        val feedback = "Reject that reason; use another approach."
        val outputs = listOf(
            Json.encodeToJsonElement(ClassifiedInput(Intent.EXCUSE_REQUEST, "Plan a decline")),
            Json.encodeToJsonElement(request),
            Json.encodeToJsonElement(ClassifiedInput(Intent.CHAT, "rewrite in corporate-speak")),
            JsonPrimitive("A corporate rewrite of the preceding draft."),
        )
        val agent = AIAgent(
            promptExecutor = ScriptedExecutor { prompt, tools ->
                prompts += prompt
                Message.Assistant(MessagePart.Tool.Call("finish-${prompts.size}", tools.single { it.name == "finalize_task_result" }.name,
                    JsonObject(mapOf("result" to outputs[prompts.lastIndex])).toString()), ResponseMetaInfo.Empty)
            }, llmModel = Models.flash, systemPrompt = conversation.systemPrompt, maxIterations = 200,
            strategy = jclawStrategy(readTools = emptyList(), naive = true, maxRefinements = 2,
                draft = { drafts += it; original },
                refinePlan = { refinements += it; if (refinements.size == 1) automatic else humanRevision },
                judgePlan = { review ->
                    reviews += review
                    DeclineCritique(PlausibilityTier.CREDIBLE, reviews.size > 1, "Codex feedback")
                },
                humanReview = { attempt ->
                    humanAttempts += attempt
                    if (humanAttempts.size == 1) HumanReview(HumanChoice.REJECT, feedback)
                    else HumanReview(HumanChoice.APPROVE, "send")
                },
                onStage = { stage, _, state -> if (state == PipelineStageState.STARTED) stages += stage },
            ),
        ) { install(ChatMemory) { conversation.configure(this) } }
        try {
            val result = conversation.run(agent, "Plan a decline") as JclawResult.HumanApproved
            result.ready.deployment shouldBe humanRevision
            prompts.size shouldBe 2
            drafts.size shouldBe 1
            refinements.size shouldBe 2
            humanAttempts.map { it.refinements } shouldBe listOf(1, 2)
            stages shouldBe listOf("deploy", "verify", "refine", "verify", "human", "refine", "verify", "human")
            refinements.last() shouldContain "Critic feedback: $feedback"
            val updated = result.ready.request
            updated.eventId shouldBe request.eventId
            updated.organizerName shouldBe request.organizerName
            updated.recentlyUsedFlavors shouldBe request.recentlyUsedFlavors
            updated.previouslyProposedFlavors shouldBe request.previouslyProposedFlavors
            updated.userInstruction shouldContain "Plan a decline\nHuman feedback: $feedback"
            reviews.last().request shouldBe updated
            conversation.run(agent, "rewrite in corporate-speak") shouldBe
                JclawResult.ChatReply("A corporate rewrite of the preceding draft.")
            prompts[2].messages.filterIsInstance<Message.User>().count { it.textContent() == feedback } shouldBe 1
            prompts[2].messages.joinToString { it.textContent() } shouldContain humanRevision.messageToOrganizer
            Memory.sentDeclines.extract(prompts[2].messages).size shouldBe 0
            drafts.size shouldBe 1
        } finally { agent.close() }
    }

    "human rejection at the shared limit blocks instead of restarting Identify or resetting the budget" {
        val conversation = Conversation("Generic assistant")
        var modelCalls = 0
        var draftCalls = 0
        var refineCalls = 0
        var humanCalls = 0
        var hold = false
        val agent = AIAgent(
            promptExecutor = ScriptedExecutor { _, tools ->
                val output = if (modelCalls++ % 2 == 0)
                    Json.encodeToJsonElement(ClassifiedInput(Intent.EXCUSE_REQUEST, "Plan a decline"))
                else Json.encodeToJsonElement(request)
                Message.Assistant(MessagePart.Tool.Call("finish-$modelCalls", tools.single { it.name == "finalize_task_result" }.name,
                    JsonObject(mapOf("result" to output)).toString()), ResponseMetaInfo.Empty)
            }, llmModel = Models.flash, systemPrompt = conversation.systemPrompt, maxIterations = 200,
            strategy = jclawStrategy(readTools = emptyList(), naive = true, maxRefinements = 2,
                draft = { draftCalls++; original },
                refinePlan = { refineCalls++; humanRevision },
                judgePlan = { DeclineCritique(PlausibilityTier.CREDIBLE, true, "Codex approved") },
                humanReview = {
                    humanCalls++
                    if (hold) HumanReview(HumanChoice.HOLD, "hold")
                    else HumanReview(HumanChoice.REJECT, "Another approach, please")
                },
            ),
        ) { install(ChatMemory) { conversation.configure(this) } }
        try {
            val blocked = conversation.run(agent, "Plan a decline") as JclawResult.Blocked
            blocked.reason shouldContain "Human rejected the plan after 2 refinements"
            modelCalls shouldBe 2
            draftCalls shouldBe 1
            refineCalls shouldBe 2
            humanCalls shouldBe 3
            hold = true
            conversation.run(agent, "A fresh request") shouldBe JclawResult.Held(original)
            modelCalls shouldBe 4
            draftCalls shouldBe 2
            refineCalls shouldBe 2
        } finally { agent.close() }
    }
})
