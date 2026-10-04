package jclaw

import ai.koog.prompt.message.Message
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import jclaw.domain.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.TimeSource

@Serializable
internal sealed interface RoutedInput {
    @Serializable data class Chat(val message: String) : RoutedInput
    @Serializable data class Decline(val request: DeclineRequest) : RoutedInput
    @Serializable data class Clarify(val question: String) : RoutedInput
}

data class DecisionEvidence(val response: JevResponse, val decision: JevDecision, val elapsedMs: Long) {
    fun lines(): List<String> = buildList {
        add("${response.model} · ${elapsedMs}ms · ${decision.path}")
        response.answers.forEach { (name, answer) ->
            val sorted = answer.probabilities.values.sortedDescending()
            val margin = sorted.first() - sorted.getOrElse(1) { 0.0 }
            val unused = if (name == "event" && decision.path == DecisionPath.CHAT) " · unused for CHAT" else ""
            add("$name: ${answer.choice} · confidence ${"%.2f".format(answer.confidence)} · margin ${"%.2f".format(margin)}$unused")
            answer.probabilities.entries.sortedByDescending { it.value }.forEach { (option, probability) ->
                add("  $option ${"%.1f".format(probability * 100)}%")
            }
        }
        if (response.usage.isNotEmpty()) add("Reported tokens: ${response.usage}")
    }
}

/** One coroutine-native client per app; credentials never enter prompts or traces. */
class JevClient(private val apiKey: String) : AutoCloseable {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(JevProtocol.json) }
        install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000 }
    }

    suspend fun evaluate(request: kotlinx.serialization.json.JsonObject): JevResponse {
        repeat(3) { attempt ->
            val response = client.post("https://api.typesafe.ai/v1/systemone") {
                bearerAuth(apiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status == HttpStatusCode.TooManyRequests || response.status.value == 529) {
                check(attempt < 2) { "Jev unavailable after bounded retries" }
                delay(1_000L shl attempt)
            } else {
                check(response.status == HttpStatusCode.OK) { "Jev HTTP ${response.status.value}" }
                return response.body()
            }
        }
        error("Jev returned no decision")
    }

    override fun close() = client.close()

    companion object {
        fun configured(): JevClient? = when (val mode = System.getenv("JCLAW_DECIDER") ?: "jev") {
            "gemini" -> null
            "jev" -> JevClient(requireNotNull(System.getenv("TYPESAFE_API_KEY")?.takeIf { it.isNotBlank() }
                ?: System.getenv("JEV_API_KEY")?.takeIf { it.isNotBlank() }) {
                "TYPESAFE_API_KEY is not set; configure it or explicitly choose JCLAW_DECIDER=gemini"
            })
            else -> error("Unknown JCLAW_DECIDER: $mode")
        }
    }
}

@Serializable
internal data class DecisionContext(val input: String, val calendar: List<CalendarRecord>, val payload: JsonObject)

@Serializable
internal data class DecisionEvaluation(
    val context: DecisionContext, val response: JevResponse, val decision: JevDecision, val elapsedMs: Long,
)

/** Separate boundaries give graph traces the same ownership as execution. */
internal interface DecisionStages {
    suspend fun context(input: String, messages: List<Message>): DecisionContext
    suspend fun evaluate(context: DecisionContext): DecisionEvaluation
    suspend fun assemble(evaluation: DecisionEvaluation): RoutedInput
}

/** Jev chooses; application code owns context, identity and typed request assembly. */
internal class JevRouter(
    private val client: JevClient,
    private val mcp: Mcp,
    private val memory: Memory,
    private val naive: Boolean,
    private val proposedFlavors: (String) -> List<ExcuseFlavor>,
    private val onDecision: (DecisionEvidence) -> Unit = {},
) : DecisionStages {
    override suspend fun context(input: String, messages: List<Message>): DecisionContext {
        val calendar = mcp.calendar()
        val conversation = messages.dropLast(1).mapNotNull { message ->
            val role = when (message) { is Message.User -> "user"; is Message.Assistant -> "assistant"; else -> return@mapNotNull null }
            message.textContent().takeIf { it.isNotBlank() }?.let { DecisionMessage(role, it) }
        }
        return DecisionContext(input, calendar, JevProtocol.request(input, conversation, calendar))
    }

    override suspend fun evaluate(context: DecisionContext): DecisionEvaluation {
        val start = TimeSource.Monotonic.markNow()
        val response = client.evaluate(context.payload)
        val decision = JevProtocol.decide(response, context.payload)
        val elapsedMs = start.elapsedNow().inWholeMilliseconds
        onDecision(DecisionEvidence(response, decision, elapsedMs))
        return DecisionEvaluation(context, response, decision, elapsedMs)
    }

    override suspend fun assemble(evaluation: DecisionEvaluation): RoutedInput {
        val (context, _, decision) = evaluation
        val (input, calendar) = context
        return when (decision.path) {
            DecisionPath.CHAT -> RoutedInput.Chat(input)
            DecisionPath.ASK -> RoutedInput.Clarify(
                "Please name one calendar event, with its organizer and day, or say whether you want an edit instead of a new decline plan.")
            DecisionPath.DECLINE -> {
                val event = calendar.single { it.id == decision.eventId }
                val attendees = if (event.id == Scenario.EVENT_ID) Scenario.ATTENDEES else emptyList()
                RoutedInput.Decline(DeclineRequest(event.id,
                    if (naive) emptyList() else memory.usedFlavors(event.organizer),
                    attendees, event.organizer, userInstruction = input,
                    previouslyProposedFlavors = proposedFlavors(event.id)))
            }
        }
    }
}
