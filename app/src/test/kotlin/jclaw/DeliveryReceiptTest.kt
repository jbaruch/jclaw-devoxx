package jclaw

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineReceipt
import jclaw.domain.DeclineRequest
import jclaw.domain.ExcuseFlavor
import kotlinx.serialization.json.Json

class DeliveryReceiptTest : StringSpec({
    val ready = JclawResult.ReadyToSend(
        DeclineDeployment(ExcuseFlavor.DEADLINE, messageToOrganizer = "Exact approved message", hallwayScript = "Explanation"),
        DeclineRequest("a-different-event", emptyList(), emptyList(), "A different organizer", "My current constraints"),
    )
    fun result(receipt: DeclineReceipt) = CallToolResult(content = listOf(TextContent(Json.encodeToString(receipt))))
    fun success(expected: jclaw.domain.DeclineSend) = DeclineReceipt(
        true, expected.callId, expected.candidateId, expected.eventId, expected.organizerName, "2026-10-06T15:30:00+02:00",
    )

    "the action envelope preserves the approved target and message with a unique call identity" {
        val first = sendEnvelope(ready)
        first.eventId shouldBe "a-different-event"
        first.organizerName shouldBe "A different organizer"
        first.message shouldBe ready.deployment.messageToOrganizer
        val second = sendEnvelope(ready)
        (second.callId == first.callId) shouldBe false
        second.candidateId shouldBe first.candidateId
        (sendEnvelope(ready.copy(deployment = ready.deployment.copy(messageToOrganizer = "Edited"))).candidateId == first.candidateId) shouldBe false
    }

    "only a complete matching successful receipt confirms delivery" {
        val expected = sendEnvelope(ready)
        val receipt = success(expected)
        confirmedReceipt(result(receipt), expected) shouldBe receipt
        shouldThrow<DeliveryRejected> { confirmedReceipt(result(receipt.copy(delivered = false)), expected) }
    }

    "an absent error malformed or ambiguous tool result is unconfirmed" {
        val expected = sendEnvelope(ready)
        listOf(null, CallToolResult(content = listOf(TextContent("failed")), isError = true),
            CallToolResult(content = listOf(TextContent("not JSON"))),
            CallToolResult(content = listOf(TextContent("{}"))),
            CallToolResult(content = listOf(TextContent(Json.encodeToString(success(expected))), TextContent("another response"))),
        ).forEach { shouldThrow<DeliveryUnconfirmed> { confirmedReceipt(it, expected) } }
    }

    "receipts for another action or lacking a valid timestamp do not confirm delivery" {
        val expected = sendEnvelope(ready)
        val receipt = success(expected)
        listOf(receipt.copy(callId = "other"), receipt.copy(candidateId = "other"),
            receipt.copy(eventId = "other"), receipt.copy(organizerName = "other"),
            receipt.copy(deliveredAt = null), receipt.copy(deliveredAt = "not a timestamp"),
        ).forEach { invalid ->
            shouldThrow<DeliveryUnconfirmed> { confirmedReceipt(result(invalid), expected) }
        }
    }
})
