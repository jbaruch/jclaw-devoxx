package jclaw

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.prompt.llm.LLModel
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineRequest
import jclaw.domain.DeclineReview

/**
 * Native-helper teaching example: a previously identified request becomes a reviewed proposal.
 * The caller supplies models and a matching executor. This graph has no external-action tools
 * and no human approval. The full app's subscription CLI stages are in Strategy.kt.
 */
fun nativeWorkflow(
    draftModel: LLModel,
    reviewModel: LLModel,
    maxRefinements: Int = jclaw.domain.WorkflowPolicy.maxRefinements,
): AIAgentGraphStrategy<DeclineRequest, JclawResult> = strategy("j-claw-native") {
    val currentRequest = createStorageKey<DeclineRequest>("native-request")
    val nextAttempt = createStorageKey<ReviewAttempt>("native-next-attempt")

    val begin by node<DeclineRequest, DeclineRequest> { request ->
        storage.set(currentRequest, request)
        request
    }
    val draft by subgraphWithTask<DeclineRequest, DeclineDeployment>(
        tools = emptyList(), llmModel = draftModel,
    ) { request -> CliCritic.claudeDraftRequest(request) }
    val initialReview by node<DeclineDeployment, ReviewAttempt> { plan ->
        check(plan.fakeCalendarEventId == null) { "Draft claimed an event this stage cannot create" }
        ReviewAttempt(plan, request = storage.getValue(currentRequest))
    }
    val verify by subgraphWithVerification<ReviewAttempt>(
        tools = emptyList(), llmModel = reviewModel,
    ) { attempt ->
        CliCritic.reviewTask(DeclineReview(requireNotNull(attempt.request), attempt.plan)) +
            "\nReturn isCorrect=true only if this exact plan is ready to consider; otherwise provide feedback."
    }
    val prepareRevision by node<CriticResult<ReviewAttempt>, String> { verdict ->
        storage.set(nextAttempt, verdict.input.copy(refinements = verdict.input.refinements + 1))
        "Revise the complete plan using this feedback. Honor all current request constraints. " +
            "Do not create events or claim external actions; fakeCalendarEventId must be null.\n" +
            "REQUEST: ${verdict.input.request}\nPLAN: ${verdict.input.plan}\nFEEDBACK: ${verdict.feedback}"
    }
    val refine by subgraphWithTask<String, DeclineDeployment>(
        tools = emptyList(), llmModel = draftModel,
    ) { task -> task }
    val revisedReview by node<DeclineDeployment, ReviewAttempt> { plan ->
        check(plan.fakeCalendarEventId == null) { "Revision claimed an event this stage cannot create" }
        storage.getValue(nextAttempt).copy(plan = plan)
    }
    val approved by node<CriticResult<ReviewAttempt>, JclawResult> { verdict ->
        JclawResult.ReadyToSend(verdict.input.plan, requireNotNull(verdict.input.request))
    }
    val blocked by node<CriticResult<ReviewAttempt>, JclawResult> { verdict ->
        JclawResult.Blocked("Native critic rejected after $maxRefinements refinements: ${verdict.feedback}", verdict.input.plan)
    }

    edge(nodeStart forwardTo begin)
    edge(begin forwardTo draft)
    edge(draft forwardTo initialReview)
    edge(initialReview forwardTo verify)
    edge(verify forwardTo approved onCondition { it.successful })
    edge(verify forwardTo prepareRevision onCondition { !it.successful && it.input.refinements < maxRefinements })
    edge(verify forwardTo blocked onCondition { !it.successful && it.input.refinements >= maxRefinements })
    edge(prepareRevision forwardTo refine)
    edge(refine forwardTo revisedReview)
    edge(revisedReview forwardTo verify)
    edge(approved forwardTo nodeFinish)
    edge(blocked forwardTo nodeFinish)
}
