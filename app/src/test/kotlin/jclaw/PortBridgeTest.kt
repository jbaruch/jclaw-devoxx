package jclaw

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jclaw.domain.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.time.Instant

private val portRequest = DeclineRequest(Scenario.EVENT_ID, emptyList(), Scenario.ATTENDEES, Scenario.ORGANIZER,
    "Get me out of training; avoid previously used excuses")
private val portPlan = DeclineDeployment(ExcuseFlavor.ALREADY_PROFICIENT,
    messageToOrganizer = "I teach practical AI agent development; may I use the advanced session instead?",
    hallwayScript = "I requested the advanced material.")
private val portVerdict = DeclineCritique(PlausibilityTier.AIRTIGHT, true, "Addresses the current request")
private val portReadToken = "read-" + "r".repeat(40)
private val portActionToken = "action-" + "a".repeat(40)

private suspend fun portFixture(mode: String = "success", block: suspend (PortBridge, PortAttempts) -> Unit) {
    withTimeout(30_000) {
        val db = Files.createTempFile("jclaw-port-test-", ".sqlite")
        try {
            Mcp.boot("calendar-mcp", "organizer-mcp", environment = mapOf("JCLAW_MOCK_DELIVERY" to mode), onStderr = {}).use { mcp ->
                PortAttempts(db).use { attempts -> block(PortBridge(mcp, "signing-" + "s".repeat(40), attempts), attempts) }
            }
        } finally { Files.deleteIfExists(db) }
    }
}

private suspend fun PortBridge.prepared() = review(PortReviewInput(portRequest, portPlan,
    portJson.encodeToJsonElement(portVerdict), workflowRunId = "run-1"))
private fun PortReviewOutput.approval() = PortDeliveryInput(requireNotNull(reviewToken),
    requireNotNull(prepared).send.candidateId, "approve", "approve", "reviewer@example.test", "run-1")

class PortBridgeTest : StringSpec({
    "Port context corrects the organizer and derives burned flavors only from matching sent records" {
        portFixture { bridge, _ ->
            val context = bridge.context(PortContextInput(portRequest.copy(organizerName = "Dana", recentlyUsedFlavors = listOf(ExcuseFlavor.DEADLINE)),
                "Try an entirely different approach", listOf(
                    PortSentFact("old-1", Scenario.ORGANIZER, ExcuseFlavor.FAMILY_OBLIGATION, "Family obligation", "2026-04-02"),
                    PortSentFact("old-2", "Somebody else", ExcuseFlavor.DEADLINE, "Deadline", "2026-04-02")),
                listOf(ExcuseFlavor.ALREADY_PROFICIENT)))
            context.organizerName shouldBe Scenario.ORGANIZER
            context.recentlyUsedFlavors shouldBe listOf(ExcuseFlavor.FAMILY_OBLIGATION)
            context.previouslyProposedFlavors shouldBe listOf(ExcuseFlavor.ALREADY_PROFICIENT)
            context.userInstruction shouldBe "Try an entirely different approach"
        }
    }
    "invalid unavailable exhausted and noncanonical reviews cannot issue a delivery token" {
        portFixture { bridge, _ ->
            listOf(null, JsonPrimitive("malformed"), buildJsonObject { put("approved", true) }).forEach { critique ->
                val blocked = bridge.review(PortReviewInput(portRequest, portPlan, critique, workflowRunId = "run-1"))
                blocked.route shouldBe ReviewRoute.BLOCK
                blocked.feedback shouldContain "Port Judge returned no valid verdict"
            }
            val rejected = portVerdict.copy(approved = false, feedback = "Unsupported reason")
            val initial = bridge.review(PortReviewInput(portRequest, portPlan, portJson.encodeToJsonElement(rejected), 0, "run-1"))
            initial.route shouldBe ReviewRoute.REFINE
            initial.reviewToken shouldBe null
            val exhausted = bridge.review(PortReviewInput(portRequest, portPlan, portJson.encodeToJsonElement(rejected), 6, "run-1"))
            exhausted.route shouldBe ReviewRoute.BLOCK
            exhausted.reviewToken shouldBe null
            exhausted.feedback shouldContain "Port Judge rejected the plan after 6 refinements"
            shouldThrow<IllegalArgumentException> { bridge.review(PortReviewInput(portRequest.copy(organizerName = "Dana"), portPlan,
                portJson.encodeToJsonElement(portVerdict), 0, "run-1")) }
        }
    }
    "only the exact candidate and a native human approval can deliver; replay returns the same receipt" {
        portFixture { bridge, _ ->
            val reviewed = bridge.prepared()
            val input = reviewed.approval()
            listOf(input.copy(selectedOutlet = "hold"), input.copy(buttonIdentifier = "try_another"),
                input.copy(submittedBy = ""), input.copy(candidateId = "another"), input.copy(workflowRunId = "run-2"),
                input.copy(reviewToken = input.reviewToken.dropLast(5) + "xxxxx")).forEach { wrong ->
                shouldThrow<IllegalArgumentException> { bridge.deliver(wrong) }
            }
            val delivered = bridge.deliver(input)
            delivered.receipt.candidateId shouldBe reviewed.prepared!!.send.candidateId
            delivered.receipt.callId shouldBe reviewed.prepared.send.callId
            delivered.sentFact.message shouldBe portPlan.messageToOrganizer
            bridge.deliver(input) shouldBe delivered
        }
    }
    "Port human critic preserves the request and uses the same global refinement budget" {
        portFixture { bridge, attempts ->
            val reviewed = bridge.review(PortReviewInput(portRequest, portPlan,
                portJson.encodeToJsonElement(portVerdict), refinements = 1, workflowRunId = "run-1"))
            val approved = reviewed.approval()
            val rejected = PortHumanReviewInput(approved.reviewToken, approved.candidateId,
                "reject", "reject", approved.submittedBy, approved.workflowRunId, "Use another reason")
            val result = bridge.humanReview(rejected)
            result.route shouldBe ReviewRoute.REFINE
            result.reviewToken shouldBe null
            val request = requireNotNull(result.refinement).request
            request.eventId shouldBe portRequest.eventId
            request.organizerName shouldBe portRequest.organizerName
            request.recentlyUsedFlavors shouldBe portRequest.recentlyUsedFlavors
            request.userInstruction shouldBe portRequest.userInstruction + "\nHuman feedback: Use another reason"
            request.previouslyProposedFlavors shouldBe portRequest.previouslyProposedFlavors
            result.refinement.plan shouldBe portPlan
            val final = bridge.review(PortReviewInput(request, portPlan,
                portJson.encodeToJsonElement(portVerdict), refinements = 6, workflowRunId = "run-1"))
            val finalApproval = final.approval()
            val blocked = bridge.humanReview(rejected.copy(reviewToken = finalApproval.reviewToken,
                candidateId = finalApproval.candidateId))
            blocked.route shouldBe ReviewRoute.BLOCK
            blocked.feedback shouldContain "Human rejected the plan after 6 refinements"
            blocked.refinement shouldBe null
            attempts.claim(requireNotNull(reviewed.prepared).send.callId) shouldBe true
        }
    }
    "human feedback must attest the signed exact candidate and workflow run" {
        portFixture { bridge, attempts ->
            val ready = bridge.prepared()
            val input = ready.approval()
            val rejection = PortHumanReviewInput(input.reviewToken, input.candidateId,
                "reject", "reject", input.submittedBy, input.workflowRunId, "Change it")
            listOf(rejection.copy(selectedOutlet = "approve"), rejection.copy(buttonIdentifier = "hold"),
                rejection.copy(submittedBy = ""), rejection.copy(candidateId = "wrong-candidate"),
                rejection.copy(workflowRunId = "wrong-run")).forEach {
                shouldThrow<IllegalArgumentException> { bridge.humanReview(it) }
            }
            attempts.claim(requireNotNull(ready.prepared).send.callId) shouldBe true
        }
    }
    "failed or mismatched delivery does not return a sent fact and is never automatically retried" {
        listOf("refused", "wrong-candidate", "malformed", "error").forEach { mode ->
            portFixture(mode) { bridge, _ ->
                val input = bridge.prepared().approval()
                shouldThrow<IllegalStateException> { bridge.deliver(input) }
                shouldThrow<DeliveryUnconfirmed> { bridge.deliver(input) }
            }
        }
    }
    "a confirmed attempt survives bridge restart and cannot become a second mock send" {
        val db = Files.createTempFile("jclaw-port-restart-", ".sqlite")
        val signingKey = "restart-" + "s".repeat(40)
        try {
            val pair = Mcp.boot("calendar-mcp", "organizer-mcp", onStderr = {}).use { mcp ->
                PortAttempts(db).use { attempts ->
                    val bridge = PortBridge(mcp, signingKey, attempts)
                    val input = bridge.prepared().approval()
                    input to bridge.deliver(input)
                }
            }
            Mcp.boot("calendar-mcp", "organizer-mcp", environment = mapOf("JCLAW_MOCK_DELIVERY" to "wrong-candidate"), onStderr = {}).use { mcp ->
                PortAttempts(db).use { attempts ->
                    PortBridge(mcp, signingKey, attempts).deliver(pair.first) shouldBe pair.second
                }
            }
        } finally { Files.deleteIfExists(db) }
    }
    "expired reviewed candidates are refused before a send attempt is claimed" {
        val db = Files.createTempFile("jclaw-port-expiry-", ".sqlite")
        try {
            Mcp.boot("calendar-mcp", "organizer-mcp", onStderr = {}).use { mcp ->
                PortAttempts(db).use { attempts ->
                    var now = Instant.parse("2026-10-01T00:00:00Z")
                    val bridge = PortBridge(mcp, "expiry-" + "s".repeat(40), attempts, clock = { now })
                    val reviewed = bridge.prepared()
                    now = now.plusSeconds(7201)
                    shouldThrow<IllegalArgumentException> { bridge.deliver(reviewed.approval()) }
                    attempts.claim(reviewed.prepared!!.send.callId) shouldBe true
                }
            }
        } finally { Files.deleteIfExists(db) }
    }
    "the HTTP MCP surface exposes only read tools and read credentials cannot call action endpoints" {
        portFixture { bridge, _ ->
            testApplication {
                application { portRoutes(bridge, portReadToken, portActionToken) }
                val listing = client.post("/mcp") {
                    bearerAuth(portReadToken); contentType(ContentType.Application.Json)
                    setBody("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
                }
                listing.status shouldBe HttpStatusCode.OK
                listing.bodyAsText() shouldContain "getCalendar"
                listing.bodyAsText() shouldNotContain "sendDecline"
                val forbidden = client.post("/deliver") { bearerAuth(portReadToken) }
                forbidden.status shouldBe HttpStatusCode.Unauthorized
                client.post("/review/human") { bearerAuth(portReadToken) }.status shouldBe HttpStatusCode.Unauthorized
                val hiddenTool = client.post("/mcp") {
                    bearerAuth(portReadToken); contentType(ContentType.Application.Json)
                    setBody("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sendDecline","arguments":{}}}""")
                }
                hiddenTool.bodyAsText() shouldContain "Unknown read-only tool"
                val calendar = client.post("/mcp") {
                    bearerAuth(portReadToken); contentType(ContentType.Application.Json)
                    setBody("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"getCalendar","arguments":{}}}""")
                }
                calendar.bodyAsText() shouldContain Scenario.EVENT_ID
                // MCP optional fields must be absent, not JSON null (the JS reference SDK rejects null).
                val result = portJson.parseToJsonElement(calendar.bodyAsText()).jsonObject.getValue("result").jsonObject
                result.containsKey("structuredContent") shouldBe false
                val text = result.getValue("content").jsonArray.single().jsonObject
                text.getValue("type").jsonPrimitive.content shouldBe "text"
                text.containsKey("annotations") shouldBe false
                val reviewed = bridge.prepared()
                val delivered = client.post("/deliver") {
                    bearerAuth(portActionToken); contentType(ContentType.Application.Json)
                    setBody(portJson.encodeToString(reviewed.approval()))
                }
                delivered.status shouldBe HttpStatusCode.OK
                portJson.decodeFromString<PortDeliveryOutput>(delivered.bodyAsText()).receipt.delivered shouldBe true
            }
        }
    }
})
