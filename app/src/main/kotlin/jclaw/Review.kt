package jclaw

import jclaw.domain.DeclineCritique
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineRequest
import kotlinx.serialization.Serializable

/** Attempt count travels with this request, never in mutable strategy-wide state. */
@Serializable
data class ReviewAttempt(
    val plan: DeclineDeployment,
    val refinements: Int = 0,
    val request: DeclineRequest? = null,
)

@Serializable
enum class ReviewRoute { APPROVE, REFINE, BLOCK, HOLD }

enum class CriticSource(val title: String) { CODEX("Codex"), PORT_JUDGE("Port Judge"), HUMAN("Human") }

data class CriticVerdict(val approved: Boolean, val feedback: String, val source: CriticSource)

@Serializable
data class ReviewDecision(
    val attempt: ReviewAttempt,
    val route: ReviewRoute,
    val feedback: String,
    val critique: DeclineCritique? = null,
)

fun reviewDecision(
    attempt: ReviewAttempt,
    critique: DeclineCritique?,
    maxRefinements: Int = jclaw.domain.WorkflowPolicy.maxRefinements,
    source: CriticSource = CriticSource.CODEX,
): ReviewDecision = if (critique == null) {
    ReviewDecision(attempt, ReviewRoute.BLOCK, "${source.title} returned no valid verdict. Nothing can be sent.")
} else reviewDecision(attempt, CriticVerdict(critique.approved, critique.feedback, source), maxRefinements)
    .copy(critique = critique)

/** Both critics spend the same request-scoped refinement budget. */
fun reviewDecision(attempt: ReviewAttempt, verdict: CriticVerdict, maxRefinements: Int = jclaw.domain.WorkflowPolicy.maxRefinements): ReviewDecision = when {
    verdict.approved -> ReviewDecision(attempt, ReviewRoute.APPROVE, verdict.feedback)
    attempt.refinements < maxRefinements -> ReviewDecision(attempt, ReviewRoute.REFINE, verdict.feedback)
    else -> ReviewDecision(attempt, ReviewRoute.BLOCK,
        "${verdict.source.title} rejected the plan after $maxRefinements refinements: ${verdict.feedback}")
}

enum class PipelineStageState { STARTED, COMPLETED, FAILED }
