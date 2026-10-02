package jclaw

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import jclaw.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.system.exitProcess

internal val portJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

@Serializable
internal data class PortSentFact(val eventId: String, val organizerName: String, val flavor: ExcuseFlavor,
    val message: String, val deliveredAt: String)

@Serializable
internal data class PortContextInput(val identified: DeclineRequest, val userInstruction: String,
    val sentFacts: List<PortSentFact>, val previouslyProposedFlavors: List<ExcuseFlavor> = emptyList())

@Serializable
internal data class PortReviewInput(val request: DeclineRequest, val plan: DeclineDeployment,
    val critique: JsonElement? = null, val refinements: Int = 0, val workflowRunId: String)

@Serializable
internal data class PortPrepared(val request: DeclineRequest, val plan: DeclineDeployment,
    val critique: DeclineCritique, val refinements: Int, val send: DeclineSend,
    val workflowRunId: String, val expiresAt: Long)

@Serializable
internal data class PortReviewOutput(val route: ReviewRoute, val feedback: String,
    val prepared: PortPrepared? = null, val reviewToken: String? = null)

@Serializable
internal data class PortDeliveryInput(val reviewToken: String, val candidateId: String,
    val selectedOutlet: String, val buttonIdentifier: String, val submittedBy: String,
    val workflowRunId: String)

@Serializable
internal data class PortDeliveryOutput(val receipt: DeclineReceipt, val sentFact: PortSentFact)

/** Durable attempt ledger. Claim BEFORE invoking the mock: an uncertain call is never retried automatically. */
internal class PortAttempts(path: Path) : AutoCloseable {
    private val database = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
    init { database.createStatement().use { it.executeUpdate(
        "CREATE TABLE IF NOT EXISTS port_send_attempts (call_id TEXT PRIMARY KEY, outcome TEXT)") } }
    fun claim(callId: String): Boolean = database.prepareStatement(
        "INSERT OR IGNORE INTO port_send_attempts(call_id) VALUES(?)").use {
        it.setString(1, callId); it.executeUpdate() == 1
    }
    fun outcome(callId: String): PortDeliveryOutput? = database.prepareStatement(
        "SELECT outcome FROM port_send_attempts WHERE call_id=?").use {
        it.setString(1, callId)
        it.executeQuery().use { row -> if (row.next()) row.getString(1)?.let {
            portJson.decodeFromString<PortDeliveryOutput>(it)
        } else null }
    }
    fun confirm(callId: String, outcome: PortDeliveryOutput) = database.prepareStatement(
        "UPDATE port_send_attempts SET outcome=? WHERE call_id=?").use {
        it.setString(1, portJson.encodeToString(outcome)); it.setString(2, callId); it.executeUpdate()
    }
    override fun close() = database.close()
}

/** Shares the app's MCP mocks, canonical recipient, review decision and raw-receipt validator. */
internal class PortBridge(private val mcp: Mcp, private val signingKey: String,
    private val attempts: PortAttempts, private val clock: () -> Instant = Instant::now) {
    private val mutex = Mutex()
    init { require(signingKey.length >= 32) { "JCLAW_PORT_SIGNING_KEY must contain at least 32 characters" } }

    suspend fun context(input: PortContextInput): DeclineRequest {
        require(input.userInstruction.isNotBlank()) { "The current user instruction is required" }
        val canonical = mcp.canonicalRequest(input.identified)
        return canonical.copy(userInstruction = input.userInstruction,
            recentlyUsedFlavors = input.sentFacts.filter { it.organizerName == canonical.organizerName }
                .map { it.flavor }.distinct(),
            previouslyProposedFlavors = input.previouslyProposedFlavors.distinct())
    }

    suspend fun review(input: PortReviewInput): PortReviewOutput {
        require(input.refinements in 0..2) { "Only two refinements are allowed" }
        require(input.workflowRunId.isNotBlank())
        require(input.plan.messageToOrganizer.isNotBlank() && input.plan.hallwayScript.isNotBlank())
        val canonical = mcp.canonicalRequest(input.request)
        require(canonical == input.request) { "Recipient changed after drafting; review the canonical request again" }
        val critique = try { input.critique?.let { portJson.decodeFromJsonElement<DeclineCritique>(it) } }
            catch (_: Exception) { null }
        val decision = reviewDecision(ReviewAttempt(input.plan, input.refinements, canonical), critique)
        if (decision.route != ReviewRoute.APPROVE) return PortReviewOutput(decision.route, decision.feedback)
        val ready = JclawResult.ReadyToSend(input.plan, canonical)
        val prepared = PortPrepared(canonical, input.plan, requireNotNull(critique), input.refinements,
            sendEnvelope(ready), input.workflowRunId, clock().plusSeconds(7200).epochSecond)
        return PortReviewOutput(decision.route, decision.feedback, prepared, sign(prepared))
    }

    /** Port is trusted to attest its native INPUT response; models never possess the action credential. */
    suspend fun deliver(input: PortDeliveryInput): PortDeliveryOutput = mutex.withLock {
        require(input.selectedOutlet == "approve" && input.buttonIdentifier == "approve") { "Human approval is required" }
        require(input.submittedBy.isNotBlank()) { "Port must identify the human responder" }
        val prepared = verify(input.reviewToken)
        require(prepared.workflowRunId == input.workflowRunId) { "Approval belongs to another workflow run" }
        require(prepared.send.candidateId == input.candidateId) { "Approval belongs to another candidate" }
        require(mcp.canonicalRequest(prepared.request) == prepared.request) { "The selected event changed; review it again" }
        val claimed = withContext(Dispatchers.IO) { attempts.claim(prepared.send.callId) }
        if (!claimed) return@withLock withContext(Dispatchers.IO) { attempts.outcome(prepared.send.callId) }
            ?: throw DeliveryUnconfirmed("This call was already attempted without a confirmed outcome; inspect the mock before retrying")
        val receipt = mcp.sendDecline(prepared.send)
        val output = PortDeliveryOutput(receipt, PortSentFact(prepared.send.eventId, prepared.send.organizerName,
            prepared.plan.flavor, prepared.send.message, requireNotNull(receipt.deliveredAt)))
        withContext(Dispatchers.IO) { attempts.confirm(prepared.send.callId, output) }
        output
    }

    private fun signature(payload: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(signingKey.toByteArray(), "HmacSHA256")); doFinal(payload.toByteArray())
    }
    private fun sign(prepared: PortPrepared): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(portJson.encodeToString(prepared).toByteArray())
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature(payload))
    }
    private fun verify(token: String): PortPrepared {
        val parts = token.split('.'); require(parts.size == 2) { "Invalid reviewed-candidate token" }
        require(MessageDigest.isEqual(signature(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) { "Reviewed candidate was modified" }
        val prepared = portJson.decodeFromString<PortPrepared>(String(Base64.getUrlDecoder().decode(parts[0])))
        require(prepared.expiresAt > clock().epochSecond) { "Reviewed candidate expired; review again" }
        return prepared
    }

    suspend fun readTool(name: String, args: JsonObject): JsonElement = when (name) {
        "getCalendar" -> McpJson.encodeToJsonElement(requireNotNull(mcp.call("calendar-mcp", name, emptyMap())))
        "getOrganizerSensitivity" -> McpJson.encodeToJsonElement(requireNotNull(mcp.call("organizer-mcp", name,
            mapOf("name" to requireNotNull(args["name"]?.jsonPrimitive?.content)))))
        else -> error("Unknown read-only tool: $name")
    }
}

internal fun Application.portRoutes(bridge: PortBridge, readToken: String, actionToken: String) {
    require(readToken.length >= 32 && actionToken.length >= 32 && readToken != actionToken)
    install(ContentNegotiation) { json(portJson) }
    routing {
        get("/health") { call.respond(buildJsonObject { put("mode", "MOCK ONLY"); put("status", "ready") }) }
        post("/mcp") {
            if (call.request.headers["Authorization"] != "Bearer $readToken") {
                call.respond(HttpStatusCode.Unauthorized); return@post
            }
            val body = call.receive<JsonObject>()
            if (body["id"] == null) { call.respond(HttpStatusCode.Accepted); return@post }
            val id = body["id"]!!
            val rpc = try {
                val result = when (body["method"]?.jsonPrimitive?.content) {
                    "initialize" -> buildJsonObject {
                        put("protocolVersion", "2025-06-18")
                        putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                        putJsonObject("serverInfo") { put("name", "jclaw-read"); put("version", "1.0.0") }
                    }
                    "ping" -> buildJsonObject {}
                    "tools/list" -> portJson.parseToJsonElement("""{"tools":[
                        {"name":"getCalendar","description":"Read the fictional demo calendar; no sent excuse text.","inputSchema":{"type":"object","properties":{}}},
                        {"name":"getOrganizerSensitivity","description":"Read organizer sensitivity; cannot send anything.","inputSchema":{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}}]}""")
                    "tools/call" -> {
                        val params = body.getValue("params").jsonObject
                        bridge.readTool(params.getValue("name").jsonPrimitive.content,
                            params["arguments"]?.jsonObject ?: buildJsonObject {})
                    }
                    else -> error("Method not supported")
                }
                buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { buildJsonObject {
                put("jsonrpc", "2.0"); put("id", id); putJsonObject("error") {
                    put("code", -32602); put("message", error.message ?: "Invalid read request")
                }
            } }
            if (call.request.headers["Accept"].orEmpty().contains("text/event-stream"))
                call.respondText("event: message\ndata: $rpc\n\n", ContentType.Text.EventStream)
            else call.respond(rpc)
        }
        post("/context") {
            if (call.request.headers["Authorization"] != "Bearer $actionToken") { call.respond(HttpStatusCode.Unauthorized); return@post }
            try { call.respond(bridge.context(call.receive())) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to (error.message ?: "Invalid context"))) }
        }
        post("/review") {
            if (call.request.headers["Authorization"] != "Bearer $actionToken") { call.respond(HttpStatusCode.Unauthorized); return@post }
            try { call.respond(bridge.review(call.receive())) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to (error.message ?: "Invalid review"))) }
        }
        post("/deliver") {
            if (call.request.headers["Authorization"] != "Bearer $actionToken") { call.respond(HttpStatusCode.Unauthorized); return@post }
            try { call.respond(bridge.deliver(call.receive())) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { call.respond(HttpStatusCode.Conflict, mapOf("error" to (error.message ?: "Delivery not confirmed"))) }
        }
    }
}

/** Expose only this local mock bridge through the chosen HTTPS tunnel when setting up Port. */
fun main(): Unit = runBlocking {
    fun credential(name: String) = requireNotNull(System.getenv(name)?.takeIf { it.length >= 32 }) { "$name is required (32+ characters)" }
    val readToken = credential("JCLAW_PORT_READ_TOKEN")
    val actionToken = credential("JCLAW_PORT_ACTION_TOKEN")
    val signingKey = credential("JCLAW_PORT_SIGNING_KEY")
    val port = System.getenv("JCLAW_PORT_PORT")?.toIntOrNull() ?: 8087
    require(port in 1..65535) { "JCLAW_PORT_PORT must be a valid port" }
    require(readToken != actionToken) { "Read and action credentials must differ" }
    val attempts = PortAttempts(Path.of(System.getenv("JCLAW_PORT_ATTEMPTS") ?: "port-send-attempts.sqlite"))
    var mcp: Mcp? = null
    try {
        val connected = Mcp.boot("calendar-mcp", "organizer-mcp")
        mcp = connected
        val bridge = PortBridge(connected, signingKey, attempts)
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            portRoutes(bridge, readToken, actionToken)
            // This callback also runs inside Ktor's JVM shutdown hook; never call System.exit here.
            monitor.subscribe(ApplicationStopped) { connected.close(); attempts.close() }
        }
        println("j-claw Port bridge: MOCK ONLY, loopback :$port; read-only /mcp, deterministic /context /review /deliver")
        // Keep the runBlocking event loop free for the stdio MCP transport readers.
        server.start(wait = false)
        awaitCancellation()
    } catch (error: Exception) {
        mcp?.close(); attempts.close()
        System.err.println("Port bridge startup failed: ${error.message}")
        // Exit before runBlocking joins SDK reader children that can outlive a failed transport.
        exitProcess(1)
    } finally { mcp?.close(); attempts.close() }
}
