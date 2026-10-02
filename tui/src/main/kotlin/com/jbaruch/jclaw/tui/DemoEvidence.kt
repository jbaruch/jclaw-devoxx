package com.jbaruch.jclaw.tui

/** Display values come from application events; this view never authorizes a send. */
data class CandidateView(
    val flavor: String,
    val recipient: String,
    val eventId: String,
    val message: String,
    val hallwayScript: String,
    val instruction: String,
    val attempt: Int,
    val candidateId: String? = null,
)

enum class DemoOutcome(val label: String) {
    STARTING("STARTING"), READY("READY"), RUNNING("RUNNING"), REVIEWING("CRITIC REVIEW"),
    REFINING("REFINING"), PROPOSAL("REVIEWED PROPOSAL"), HUMAN("AWAITING HUMAN"),
    HELD("HELD"), SENDING("SENDING TO MOCK"), DELIVERED("DELIVERY CONFIRMED"),
    BLOCKED("BLOCKED"), UNCONFIRMED("DELIVERY UNCONFIRMED"), CHAT("CHAT REPLY"),
}
