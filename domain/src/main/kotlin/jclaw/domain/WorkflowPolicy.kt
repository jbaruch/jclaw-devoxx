package jclaw.domain

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/** One shared policy resource consumed by Koog, Port preparation and the handoff. */
public object WorkflowPolicy {
    public val maxRefinements: Int = JevProtocol.json.parseToJsonElement(
        requireNotNull(javaClass.getResourceAsStream("/jclaw/workflow/policy.json"))
            .bufferedReader().use { it.readText() },
    ).jsonObject.getValue("maxRefinements").jsonPrimitive.int.also { require(it >= 0) }
    public val maxCandidates: Int get() = maxRefinements + 1
}
