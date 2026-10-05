package jclaw

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseSpanAdapter
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.throwables.shouldThrow
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.export.batchSpanProcessor
import jclaw.domain.DeclineCritique
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineRequest
import jclaw.domain.DeclineReview
import jclaw.domain.ExcuseFlavor
import jclaw.domain.PlausibilityTier
import jclaw.domain.Scenario
import jclaw.domain.JevProtocol
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue

@OptIn(ExperimentalApi::class)
class ObservabilityTest : StringSpec({
    "Jev telemetry keeps native nesting and reports real decisions and usage without fictional chat messages" {
        for (fail in listOf(false, true)) {
            val fixture = decisionFixture()
            val spans = ConcurrentLinkedQueue<SpanData>()
            val exporter = object : SpanExporter {
                override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
                    spans.addAll(telemetry.map(::withLangfuseNodeDetails))
                    return OperationResultCode.Success
                }
                override suspend fun forceFlush() = OperationResultCode.Success
                override suspend fun shutdown() = OperationResultCode.Success
            }
            var processor: SpanProcessor? = null
            val pipeline = jclawStrategy(readTools = emptyList(), naive = true,
                draft = { error("No draft in this telemetry fixture") },
                refinePlan = { error("No refinement in this telemetry fixture") },
                judgePlan = { error("No judge in this telemetry fixture") },
                decisionStages = object : DecisionStages {
                    override suspend fun context(input: String, messages: List<Message>) = fixture.context
                    override suspend fun evaluate(context: DecisionContext): DecisionEvaluation {
                        if (fail) error("Jev HTTP 503")
                        return fixture
                    }
                    override suspend fun assemble(evaluation: DecisionEvaluation) = RoutedInput.Clarify("Fixture result")
                },
            )
            val agent = AIAgent(promptExecutor = simpleGoogleAIExecutor("unused"), llmModel = Models.flash, strategy = pipeline) {
                install(OpenTelemetry) {
                    setVerbose(true)
                    addLangfuseSpanAdapter()
                    addSpanProcessor { batchSpanProcessor(exporter).also { processor = it } }
                }
            }
            try {
                if (fail) shouldThrow<Exception> { agent.run(fixture.context.input) }
                else agent.run(fixture.context.input) shouldBe JclawResult.ChatReply("Fixture result")
            } finally {
                agent.close()
                processor?.forceFlush()
            }
            val jev = spans.single { it.attributes["koog.node.id"] == "jevDecision" }
            spans.single { it.spanContext.spanId == jev.parent.spanId }.name shouldContain "routeAndIdentify"
            jev.attributes["gen_ai.provider.name"] shouldBe "typesafe"
            jev.attributes["gen_ai.operation.name"] shouldBe "decision"
            jev.attributes["gen_ai.request.model"] shouldBe JevProtocol.model
            jev.attributes["langfuse.observation.input"] shouldBe fixture.context.payload.toString()
            jev.attributes.containsKey("gen_ai.input.messages") shouldBe false
            jev.attributes.containsKey("langfuse.observation.cost_details") shouldBe false
            if (fail) {
                jev.status.statusCode.name shouldBe "ERROR"
                jev.attributes.containsKey("gen_ai.usage.input_tokens") shouldBe false
                jev.attributes.containsKey("gen_ai.response.model") shouldBe false
            } else {
                jev.attributes["gen_ai.response.model"] shouldBe fixture.response.model
                jev.attributes["gen_ai.usage.input_tokens"] shouldBe fixture.response.usage.getValue("input_tokens").toLong()
                jev.attributes["gen_ai.usage.output_tokens"] shouldBe fixture.response.usage.getValue("output_tokens").toLong()
                jev.attributes["langfuse.observation.output"] shouldBe JevProtocol.json.encodeToString(fixture.response)
                jev.attributes["langfuse.observation.metadata.intent.confidence"] shouldBe fixture.response.answers.getValue("intent").confidence
                jev.attributes["langfuse.observation.metadata.event_id"] shouldBe fixture.decision.eventId
                val calendar = spans.single { it.attributes["koog.node.id"] == "readCalendar" }
                calendar.attributes["langfuse.observation.metadata.client"] shouldBe "application"
                calendar.attributes.containsKey("gen_ai.request.model") shouldBe false
            }
            processor?.shutdown()
        }
    }

    "native Koog export includes typed decisions and each review attempt without inventing CLI generations" {
        val rawSpans = ConcurrentLinkedQueue<SpanData>()
        val enrichedSpans = ConcurrentLinkedQueue<SpanData>()
        val exporter = object : SpanExporter {
            override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
                rawSpans.addAll(telemetry)
                enrichedSpans.addAll(telemetry.map(::withLangfuseNodeDetails))
                return OperationResultCode.Success
            }
            override suspend fun forceFlush() = OperationResultCode.Success
            override suspend fun shutdown() = OperationResultCode.Success
        }
        var processor: SpanProcessor? = null
        val draft = DeclineDeployment(ExcuseFlavor.DEADLINE, messageToOrganizer = "Draft message", hallwayScript = "Draft script")
        val revision = draft.copy(flavor = ExcuseFlavor.ALREADY_PROFICIENT, messageToOrganizer = "Revised message")
        val rejection = DeclineCritique(PlausibilityTier.THIN, false, "Fixture: revise this draft")
        val approval = DeclineCritique(PlausibilityTier.AIRTIGHT, true, "Fixture: revision approved")
        // Scripted graph: exercises Koog serialization/export and retry spans, with no model calls.
        val pipeline = strategy<DeclineRequest, JclawResult>("telemetry-fixture") {
            val deploy by node<DeclineRequest, ReviewAttempt> { ReviewAttempt(draft, request = it) }
            val verify by node<ReviewAttempt, ReviewDecision> {
                reviewDecision(it, if (it.refinements == 0) rejection else approval)
            }
            val refine by node<ReviewDecision, ReviewAttempt> { ReviewAttempt(revision, it.attempt.refinements + 1, it.attempt.request) }
            val readyToSend by node<ReviewDecision, JclawResult> { JclawResult.ReadyToSend(it.attempt.plan, requireNotNull(it.attempt.request)) }
            edge(nodeStart forwardTo deploy)
            edge(deploy forwardTo verify)
            edge(verify forwardTo refine onCondition { it.route == ReviewRoute.REFINE })
            edge(refine forwardTo verify)
            edge(verify forwardTo readyToSend onCondition { it.route == ReviewRoute.APPROVE })
            edge(readyToSend forwardTo nodeFinish)
        }
        val agent = AIAgent(
            promptExecutor = simpleGoogleAIExecutor("not-used-by-this-test"),
            llmModel = Models.flash,
            strategy = pipeline,
        ) {
            install(OpenTelemetry) {
                setVerbose(true)
                addLangfuseSpanAdapter()
                addSpanProcessor { batchSpanProcessor(exporter).also { processor = it } }
            }
        }
        val request = DeclineRequest(Scenario.EVENT_ID, Scenario.BURNED, Scenario.ATTENDEES, Scenario.ORGANIZER)
        try {
            agent.run(request) shouldBe JclawResult.ReadyToSend(revision, request)
        } finally {
            agent.close()
            processor?.forceFlush()
        }
        val reviews = enrichedSpans.filter { it.attributes["koog.node.id"] == "verify" }.sortedBy { it.startTimestamp }
        reviews.size shouldBe 2
        reviews.map { it.attributes["langfuse.observation.metadata.review_attempt"] } shouldBe listOf(1, 2)
        reviews.forEachIndexed { index, span ->
            val input = Json.decodeFromString<ReviewAttempt>(span.attributes.getValue("langfuse.observation.input") as String)
            val output = Json.decodeFromString<ReviewDecision>(span.attributes.getValue("langfuse.observation.output") as String)
            input.refinements shouldBe index
            output.critique shouldBe if (index == 0) rejection else approval
            output.route shouldBe if (index == 0) ReviewRoute.REFINE else ReviewRoute.APPROVE
            span.attributes["langfuse.observation.metadata.provider"] shouldBe "openai"
            span.attributes["langfuse.observation.metadata.authentication"] shouldBe "subscription"
            span.attributes["langfuse.observation.metadata.application_prompt"] shouldBe CliCritic.codexPrompt(DeclineReview(request, input.plan))
            (span.attributes["langfuse.observation.metadata.application_prompt"] as String) shouldContain
                "Is this a usable proposal?"
            span.attributes.containsKey("gen_ai.usage.input_tokens") shouldBe false
            span.attributes.containsKey("langfuse.observation.cost_details") shouldBe false
        }
        enrichedSpans.filter { it.attributes["koog.node.id"] in setOf("deploy", "refine") }.forEach {
            it.attributes["langfuse.observation.metadata.provider"] shouldBe "anthropic"
            it.attributes.containsKey("langfuse.observation.input") shouldBe true
            it.attributes.containsKey("langfuse.observation.output") shouldBe true
        }
        val finalNode = enrichedSpans.single { it.attributes["koog.node.id"] == "readyToSend" }
        Json.decodeFromString<JclawResult>(finalNode.attributes.getValue("langfuse.observation.output") as String) shouldBe
            JclawResult.ReadyToSend(revision, request)
        enrichedSpans.forEach { enriched ->
            val original = rawSpans.single { it.spanContext.spanId == enriched.spanContext.spanId }
            enriched.parent shouldBe original.parent
            enriched.spanContext shouldBe original.spanContext
            enriched.startTimestamp shouldBe original.startTimestamp
            enriched.endTimestamp shouldBe original.endTimestamp
            enriched.name shouldBe original.name
            enriched.status shouldBe original.status
            enriched.attributes["langfuse.observation.metadata.langgraph_step"] shouldBe
                original.attributes["langfuse.observation.metadata.langgraph_step"]
        }
        processor?.shutdown()
    }

    "blocked and chat results preserve their exact typed payloads" {
        listOf<JclawResult>(JclawResult.Blocked("Judge unavailable"), JclawResult.ChatReply("Hello")).forEach {
            Json.decodeFromString<JclawResult>(Json.encodeToString<JclawResult>(it)) shouldBe it
        }
    }
})
