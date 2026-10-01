package jclaw

import jclaw.domain.DeclineDeployment
import jclaw.domain.ExcuseFlavor
import jclaw.domain.DeclineRequest
import jclaw.domain.DeclineReview
import jclaw.domain.Scenario
import kotlinx.coroutines.runBlocking

/** A single neutral review of a fixture; the verdict is the model's, not an assertion. */
fun main(): Unit = runBlocking {
    val draft = DeclineDeployment(
        flavor = ExcuseFlavor.DEADLINE,
        fakeCalendarEventId = null,
        messageToOrganizer = "Dana, I need to miss Basic AI Proficiency Training because " +
            "I have an urgent delivery deadline that afternoon. Could I get an exemption?",
        hallwayScript = "I had to focus on a delivery deadline.",
    )

    println("Asking Codex on subscription to judge a sample draft...")
    val request = DeclineRequest(Scenario.EVENT_ID, Scenario.BURNED, Scenario.ATTENDEES, Scenario.ORGANIZER)
    val out = CliCritic.codex().run(DeclineReview(request, draft))
    println("\nTyped Codex judgment")
    println("tier:     ${out.tier} (${out.tier::class.simpleName})")
    println("approved: ${out.approved}")
    println("feedback: ${out.feedback}")
}
