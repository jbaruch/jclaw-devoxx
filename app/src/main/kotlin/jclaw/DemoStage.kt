package jclaw

/** Capabilities accumulate on main; round branches are derived only after review. */
data class DemoStage(val number: Int) {
    init { require(number in 1..7) { "Round must be between 1 and 7" } }
    val tools get() = number >= 2
    val memory get() = number >= 3
    val skills get() = number >= 4
    val workflow get() = number >= 5
    val human get() = number >= 6
    val title get() = listOf("CHATBOT", "TOOLS / MCP", "MEMORY", "SKILLS", "WORKFLOWS", "GUARDRAILS", "OBSERVABILITY")[number - 1]
    fun telemetryMetadata(decider: String) = buildMap {
        put("model", Models.flash.id)
        if (workflow) { put("decider", decider); put("drafter", CliCritic.draftModel); put("critic", TypedCodex.model) }
    }
    fun telemetryTags() = if (workflow) arrayOf("critic:codex", "drafter:claude-code") else arrayOf("conversational-agent")
    companion object {
        fun configured() = DemoStage(System.getenv("JCLAW_ROUND")?.toIntOrNull() ?: 6)
    }
}
