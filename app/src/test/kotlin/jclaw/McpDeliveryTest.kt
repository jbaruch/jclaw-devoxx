package jclaw

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineRequest
import jclaw.domain.ExcuseFlavor
import jclaw.domain.Scenario
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.io.path.listDirectoryEntries

/** Constant test vectors verify persistence/retrieval, not semantic embedding quality. */
private object FixtureEmbedder : Embedder {
    override suspend fun embed(text: String) = Vector(listOf(1.0, 1.0))
    override fun diff(embedding1: Vector, embedding2: Vector) = 1.0 - embedding1.cosineSimilarity(embedding2)
}

class McpDeliveryTest : StringSpec({
    val ready = JclawResult.ReadyToSend(
        DeclineDeployment(ExcuseFlavor.ALREADY_PROFICIENT,
            messageToOrganizer = "Exact approved fixture message", hallwayScript = "Fixture explanation"),
        DeclineRequest(Scenario.EVENT_ID, emptyList(), emptyList(), Scenario.ORGANIZER, "Fixture instruction"),
    )

    "calendar identity is canonical before drafting and unknown event targets block" {
        withTimeout(20_000) {
            Mcp.boot("calendar-mcp", onStderr = {}).use { mcp ->
                mcp.canonicalRequest(ready.request.copy(organizerName = "Dana")) shouldBe ready.request
                shouldThrow<IllegalStateException> { mcp.canonicalRequest(ready.request.copy(eventId = "unknown-event")) }
            }
        }
    }

    "real MCP delivery persists the exact candidate and target across a memory restart" {
        withTimeout(20_000) {
            val root = Files.createTempDirectory("jclaw-delivery-")
            root.resolve("documents").createDirectory()
            try {
                val memory = Memory.open(FixtureEmbedder, root, trace = {})
                Mcp.boot("organizer-mcp", environment = mapOf("JCLAW_MOCK_DELIVERY" to "success"), onStderr = {}).use { mcp ->
                    deliverApproved(ready, confirm = { false }, send = { error("Held plan must not send") }) shouldBe false
                    root.resolve("documents").listDirectoryEntries().size shouldBe 0
                    val receipt = sendAndRemember(ready, mcp, memory,
                        onDelivered = { it.delivered shouldBe true }, onMemoryFailure = { throw it })
                    receipt.eventId shouldBe ready.request.eventId
                    receipt.organizerName shouldBe ready.request.organizerName
                    receipt.candidateId shouldBe sendEnvelope(ready).candidateId
                }
                val reopened = Memory.open(FixtureEmbedder, root, trace = {})
                val stored = reopened.search(SimilaritySearchRequest("fixture", limit = 5), null).single().document.content
                stored shouldContain ready.request.eventId
                stored shouldContain ready.request.organizerName
                stored shouldContain ready.deployment.messageToOrganizer
                root.resolve("documents").listDirectoryEntries().size shouldBe 1
            } finally { root.toFile().deleteRecursively() }
        }
    }

    "real refused error malformed and mismatched MCP receipts never announce delivery or write memory" {
        withTimeout(30_000) {
            val root = Files.createTempDirectory("jclaw-delivery-fail-")
            root.resolve("documents").createDirectory()
            try {
                val memory = Memory.open(FixtureEmbedder, root, trace = {})
                listOf("refused", "error", "malformed", "wrong-event", "wrong-call", "wrong-candidate").forEach { mode ->
                    Mcp.boot("organizer-mcp", environment = mapOf("JCLAW_MOCK_DELIVERY" to mode), onStderr = {}).use { mcp ->
                        suspend fun attempt() = sendAndRemember(ready, mcp, memory,
                            onDelivered = { error("$mode must not announce success") }, onMemoryFailure = { throw it })
                        if (mode == "refused") shouldThrow<DeliveryRejected> { attempt() }
                        else shouldThrow<DeliveryUnconfirmed> { attempt() }
                    }
                    root.resolve("documents").listDirectoryEntries().size shouldBe 0
                }
            } finally { root.toFile().deleteRecursively() }
        }
    }
})
