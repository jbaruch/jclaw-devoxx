package jclaw

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jclaw.domain.DeclineReceipt
import jclaw.domain.DeclineSend
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID

/** Uncertainty is not a successful send and must not invite an automatic resend. */
class DeliveryUnconfirmed(message: String) : IllegalStateException(message)
class DeliveryRejected(message: String) : IllegalStateException(message)

internal fun sendEnvelope(ready: JclawResult.ReadyToSend): DeclineSend {
    val event = ready.request.eventId
    val organizer = ready.request.organizerName
    val message = ready.deployment.messageToOrganizer
    require(event.isNotBlank() && organizer.isNotBlank() && message.isNotBlank()) { "Incomplete approved candidate" }
    // Length-delimited canonical input avoids separator collisions in arbitrary message text.
    val canonical = listOf(event, organizer, message).joinToString("") { "${it.toByteArray(Charsets.UTF_8).size}:$it" }
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return DeclineSend(UUID.randomUUID().toString(), fingerprint, event, organizer, message)
}

/** Validate the raw MCP outcome before success messaging or durable sent-history ingestion. */
internal fun confirmedReceipt(result: CallToolResult?, expected: DeclineSend): DeclineReceipt {
    if (result == null) throw DeliveryUnconfirmed("Organizer returned no receipt; delivery is unknown")
    if (result.isError == true) throw DeliveryUnconfirmed("Organizer reported a tool error; delivery is unconfirmed")
    val payload = result.structuredContent?.toString() ?: result.content.filterIsInstance<TextContent>()
        .singleOrNull()?.text ?: throw DeliveryUnconfirmed("Organizer returned no unambiguous receipt")
    val receipt = try { Json.decodeFromString<DeclineReceipt>(payload) }
        catch (_: Exception) { throw DeliveryUnconfirmed("Organizer returned a malformed receipt; delivery is unknown") }
    if (receipt.callId != expected.callId || receipt.candidateId != expected.candidateId ||
        receipt.eventId != expected.eventId || receipt.organizerName != expected.organizerName) {
        throw DeliveryUnconfirmed("Receipt does not match the approved candidate and this send attempt")
    }
    if (!receipt.delivered) throw DeliveryRejected("Organizer declined delivery for this candidate")
    try { OffsetDateTime.parse(receipt.deliveredAt) }
    catch (_: Exception) { throw DeliveryUnconfirmed("Success receipt has no valid delivery timestamp") }
    return receipt
}

internal fun deliveryFailure(error: Throwable): String = when (error) {
    is DeliveryRejected -> "Delivery failed: ${error.message}. No sent-history record was written."
    else -> "Delivery is unconfirmed: ${error.message}. Check the organizer before retrying; no sent-history record was written."
}
