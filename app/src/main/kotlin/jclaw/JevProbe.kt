package jclaw

import jclaw.domain.JevProtocol
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/** Exercise the real JVM/Ktor adapter and MCP calendar; no other providers or writes. */
fun main(args: Array<String>): Unit = runBlocking {
    val message = args.joinToString(" ").ifBlank {
        "Get me out of the Basic AI Proficiency Training on Tuesday, run by Dana from People Ops. Don't reuse an excuse I've already used on her - tell me which ones you're avoiding."
    }
    requireNotNull(JevClient.configured()) { "Jev probe requires JCLAW_DECIDER=jev" }.use { client ->
        Mcp.boot("calendar-mcp").use { mcp ->
            val request = JevProtocol.request(message, emptyList(), mcp.calendar())
            val start = kotlin.time.TimeSource.Monotonic.markNow()
            val response = client.evaluate(request)
            val decision = JevProtocol.decide(response, request)
            DecisionEvidence(response, decision, start.elapsedNow().inWholeMilliseconds).lines().forEach(::println)
            mcp.close()
        }
    }
    exitProcess(0)
}
