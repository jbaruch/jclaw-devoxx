package jclaw.domain

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Real MCP records are the only event IDs the decision model may select. */
@Serializable
public data class CalendarRecord(
    val id: String,
    val title: String,
    val start: String,
    val organizer: String,
    val declined: Boolean = false,
)

@Serializable
public data class DecisionMessage(val role: String, val content: String)

@Serializable
public data class JevChoice(
    val type: String,
    val choice: String,
    val probabilities: Map<String, Double>,
    val confidence: Double,
)

@Serializable
public data class JevResponse(
    val model: String,
    val answers: Map<String, JevChoice>,
    val usage: Map<String, Int> = emptyMap(),
)

public enum class DecisionPath { CHAT, DECLINE, ASK }
@Serializable
public data class JevDecision(val path: DecisionPath, val eventId: String? = null)

/** Shared resource contract: Koog, Viktor's implementation and the live evaluator. */
public object JevProtocol {
    public val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private fun resource(name: String): JsonObject = json.parseToJsonElement(
        requireNotNull(javaClass.getResourceAsStream("/jclaw/jev/$name")) { "Missing Jev contract: $name" }
            .bufferedReader().use { it.readText() },
    ).jsonObject
    private val questions = resource("questions.json")
    private val policy = resource("policy.json")
    public val model: String = policy.getValue("model").jsonPrimitive.content
    private val intentFloor = policy.getValue("intentConfidenceMin").jsonPrimitive.double
    private val eventFloor = policy.getValue("eventConfidenceMin").jsonPrimitive.double
    private val sentinels = setOf("NO_MATCH", "AMBIGUOUS")
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE MMMM dd yyyy HH:mm", Locale.ENGLISH)
    public const val SCENARIO: String = "Fictional rehearsal. Today is Friday October 2, 2026; the next Tuesday is October 6, 2026."

    public fun request(message: String, conversation: List<DecisionMessage>, calendar: List<CalendarRecord>): JsonObject {
        require(calendar.map { it.id }.distinct().size == calendar.size) { "Duplicate calendar IDs" }
        require(calendar.none { it.id in sentinels || it.id.isBlank() || it.organizer.isBlank() }) { "Invalid calendar identity" }
        val records = calendar.map { record ->
            JsonObject(json.encodeToJsonElement(record).jsonObject +
                ("dateLabel" to JsonPrimitive(OffsetDateTime.parse(record.start).format(dateFormat))))
        }
        val criteria = records.associate { record ->
            record.getValue("id").jsonPrimitive.content to JsonObject(record - "id")
        } + mapOf(
            "NO_MATCH" to JsonPrimitive("ZERO records match ALL specified details. This includes an explicit organizer or date/weekday absent from matching records, no identified obligation, or no request for a decline plan. A matching title does not override a conflicting organizer or day."),
            "AMBIGUOUS" to JsonPrimitive("Two or more records match ALL specified details equally; insufficient evidence to choose one; or multiple targets are requested."),
        )
        return buildJsonObject {
            put("model", model)
            put("state", buildJsonObject {
                put("userMessage", message)
                put("conversation", json.encodeToJsonElement(conversation))
                put("calendar", JsonArray(records))
                put("scenario", SCENARIO)
            })
            put("questions", JsonObject(questions +
                ("event" to JsonObject(questions.getValue("event").jsonObject + ("criteria" to JsonObject(criteria))))))
        }
    }

    public fun decide(response: JevResponse, request: JsonObject): JevDecision {
        require(response.model == model) { "Jev returned an unvalidated model version" }
        val qs = request.getValue("questions").jsonObject
        fun choice(id: String): JevChoice {
            val answer = requireNotNull(response.answers[id]) { "Jev omitted $id" }
            val options = qs.getValue(id).jsonObject.getValue("criteria").jsonObject.keys
            require(answer.type == "choice" && answer.choice in options) { "Invalid Jev $id choice" }
            require(answer.probabilities.keys == options && answer.probabilities.values.all {
                it.isFinite() && it in 0.0..1.0
            }) { "Invalid Jev $id distribution" }
            require(kotlin.math.abs(answer.probabilities.values.sum() - 1) <= 0.02 &&
                answer.confidence.isFinite() && answer.confidence in 0.0..1.0) { "Invalid Jev $id confidence" }
            require(answer.probabilities.getValue(answer.choice) + 0.001 >= answer.probabilities.values.max()) {
                "Jev $id choice conflicts with its distribution"
            }
            return answer
        }
        val intent = choice("intent")
        if (intent.confidence < intentFloor) return JevDecision(DecisionPath.ASK)
        if (intent.choice == "CHAT") return JevDecision(DecisionPath.CHAT)
        val event = choice("event")
        if (event.choice in sentinels || event.confidence < eventFloor) return JevDecision(DecisionPath.ASK)
        return JevDecision(DecisionPath.DECLINE, event.choice)
    }
}
