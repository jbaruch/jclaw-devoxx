package jclaw

import ai.koog.agents.core.agent.AIAgent
import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.message.Message
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import jclaw.domain.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal fun liveJevCases(): List<JsonObject> = listOf("development-02.json", "holdout-01.json").flatMap {
    Json.parseToJsonElement(File(System.getProperty("jclaw.validation"), it).readText())
        .jsonObject.getValue("results").jsonArray.map { it.jsonObject }
}

internal fun decisionFixture(): DecisionEvaluation {
    val case = liveJevCases().first()
    val payload = case.getValue("request").jsonObject
    val state = payload.getValue("state").jsonObject
    val records = JevProtocol.json.decodeFromJsonElement<List<CalendarRecord>>(state.getValue("calendar"))
    val response = JevProtocol.json.decodeFromJsonElement<JevResponse>(case.getValue("response"))
    return DecisionEvaluation(DecisionContext(state.getValue("userMessage").jsonPrimitive.content, records, payload),
        response, JevProtocol.decide(response, payload), case.getValue("latencyMs").jsonPrimitive.double.toLong())
}

class JevTest : StringSpec({
    "JVM payloads and policy replay all 48 admitted live responses exactly" {
        val cases = liveJevCases()
        cases.size shouldBe 48
        cases.forEach { case ->
            val payload = case.getValue("request").jsonObject
            val state = payload.getValue("state").jsonObject
            JevProtocol.request(state.getValue("userMessage").jsonPrimitive.content,
                JevProtocol.json.decodeFromJsonElement(state.getValue("conversation")),
                JevProtocol.json.decodeFromJsonElement(state.getValue("calendar"))) shouldBe payload
            val observed = JevProtocol.decide(JevProtocol.json.decodeFromJsonElement(case.getValue("response")), payload)
            val expected = case.getValue("expected").jsonObject
            observed.path.name shouldBe expected.getValue("path").jsonPrimitive.content
            observed.eventId shouldBe expected["eventId"]?.jsonPrimitive?.contentOrNull
        }
    }
    "changed model unknown option and invalid distributions fail before any decline" {
        val fixture = decisionFixture()
        val response = fixture.response
        val intent = response.answers.getValue("intent")
        listOf(response.copy(model = "jev-latest"),
            response.copy(answers = response.answers - "intent"),
            response.copy(answers = response.answers + ("intent" to intent.copy(choice = "invented"))),
            response.copy(answers = response.answers + ("intent" to intent.copy(probabilities = mapOf("CHAT" to -0.1, "EXCUSE_REQUEST" to 1.1)))),
            response.copy(answers = response.answers + ("intent" to intent.copy(confidence = Double.NaN))),
        ).forEach { shouldThrow<IllegalArgumentException> { JevProtocol.decide(it, fixture.context.payload) } }
    }
    "low confidence asks and chat never consumes the speculative event answer" {
        val fixture = decisionFixture()
        val intent = fixture.response.answers.getValue("intent")
        JevProtocol.decide(fixture.response.copy(answers = fixture.response.answers + ("intent" to intent.copy(confidence = 0.59))),
            fixture.context.payload).path shouldBe DecisionPath.ASK
        JevProtocol.decide(fixture.response.copy(answers = mapOf("intent" to
            intent.copy(choice = "CHAT", confidence = 1.0, probabilities = mapOf("CHAT" to 1.0, "EXCUSE_REQUEST" to 0.0)))),
            fixture.context.payload).path shouldBe DecisionPath.CHAT
    }
    "native Jev graph routes once and shares six refinements across model and human critics" {
        val fixture = decisionFixture()
        val request = DeclineRequest(Scenario.EVENT_ID, Scenario.BURNED, Scenario.ATTENDEES, Scenario.ORGANIZER)
        val stages = mutableListOf<String>()
        var decisions = 0
        var reviews = 0
        var humanReviews = 0
        var refinements = 0
        val pipeline = jclawStrategy(readTools = emptyList(), naive = true,
            decisionStages = object : DecisionStages {
                override suspend fun context(input: String, messages: List<Message>) = fixture.context.copy(input = input)
                override suspend fun evaluate(context: DecisionContext) = fixture.copy(context = context).also { decisions++ }
                override suspend fun assemble(evaluation: DecisionEvaluation) = RoutedInput.Decline(request)
            },
            draft = { DeclineDeployment(ExcuseFlavor.ALREADY_PROFICIENT, messageToOrganizer = "Draft", hallwayScript = "Draft") },
            refinePlan = { refinements++; DeclineDeployment(ExcuseFlavor.ALREADY_PROFICIENT, messageToOrganizer = "Revision $refinements", hallwayScript = "Revised") },
            judgePlan = { reviews++; DeclineCritique(PlausibilityTier.CREDIBLE, reviews != 1 && reviews != 3, "Judge feedback") },
            humanReview = { humanReviews++; HumanReview(if (humanReviews < 5) HumanChoice.REJECT else HumanChoice.APPROVE, "Make it shorter") },
            onStage = { name, _, status -> if (status == PipelineStageState.STARTED) stages += name },
        )
        val agent = AIAgent(promptExecutor = simpleGoogleAIExecutor("unused"), llmModel = Models.flash, strategy = pipeline)
        try {
            val result = agent.run(fixture.context.input) as JclawResult.HumanApproved
            result.ready.request.eventId shouldBe Scenario.EVENT_ID
            result.ready.request.organizerName shouldBe Scenario.ORGANIZER
            result.ready.deployment.messageToOrganizer shouldBe "Revision 6"
            decisions shouldBe 1
            refinements shouldBe 6
            stages.take(4) shouldBe listOf("readCalendar", "jevDecision", "assembleRequest", "deploy")
            stages.count { it == "refine" } shouldBe 6
            stages.count { it == "verify" } shouldBe 7
            stages.count { it == "human" } shouldBe 5
        } finally { agent.close() }
    }
    "confirmed file history and held suggestions remain separate" {
        val root = Files.createTempDirectory("jev-memory")
        val docs = root.resolve("documents").createDirectories()
        docs.resolve("seed").writeText(Memory.story("Training", Scenario.ORGANIZER, "CALENDAR_CONFLICT", "Sent seed").content)
        docs.resolve("other").writeText(Memory.story("Other event", "Other organizer", "DEADLINE", "Other sent message").content)
        val embedder = object : Embedder {
            override suspend fun embed(text: String) = Vector(listOf(1.0, 0.0))
            override fun diff(embedding1: Vector, embedding2: Vector) = 1.0 - embedding1.cosineSimilarity(embedding2)
        }
        try {
            val memory = Memory.open(embedder, root, trace = {})
            val conversation = Conversation("Fixture")
            val request = DeclineRequest(Scenario.EVENT_ID, emptyList(), emptyList(), Scenario.ORGANIZER)
            conversation.rememberProposal(ReviewAttempt(DeclineDeployment(ExcuseFlavor.ALREADY_PROFICIENT,
                messageToOrganizer = "Held", hallwayScript = "Held"), request = request))
            conversation.proposedFlavors(Scenario.EVENT_ID) shouldBe listOf(ExcuseFlavor.ALREADY_PROFICIENT)
            memory.usedFlavors(Scenario.ORGANIZER) shouldBe listOf(ExcuseFlavor.CALENDAR_CONFLICT)
            memory.add(listOf(Memory.story("Training", Scenario.ORGANIZER, "ALREADY_PROFICIENT", "Confirmed send")))
            memory.usedFlavors(Scenario.ORGANIZER).toSet() shouldBe setOf(ExcuseFlavor.CALENDAR_CONFLICT, ExcuseFlavor.ALREADY_PROFICIENT)
        } finally { root.toFile().deleteRecursively() }
    }
})
