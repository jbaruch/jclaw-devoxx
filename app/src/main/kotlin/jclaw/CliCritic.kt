package jclaw

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliAgentStructuredResponse
import ai.koog.agents.cli.claude.ClaudePermissionMode
import ai.koog.agents.cli.transport.ProcessCliTransport
import jclaw.domain.DeclineCritique
import jclaw.domain.DeclineDeployment
import jclaw.domain.DeclineRequest
import jclaw.domain.DeclineReview
import jclaw.domain.Scenario
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/** Gemini identifies the obligation; subscription CLIs draft, refine, and judge. */
object CliCritic {
    val draftModel = System.getenv("JCLAW_CLAUDE_MODEL") ?: "claude-opus-4-6"
    private val claudeFlags = listOf(
        "--model", draftModel,
        "--safe-mode", "--strict-mcp-config", "--tools=",
        "--no-session-persistence", "--settings", """{"forceLoginMethod":"claudeai"}""",
    )

    fun claudeDrafter(): CliAIAgent<DeclineRequest, CliAgentStructuredResponse<DeclineDeployment>> =
        CliAIAgent.claude(
            transport = SubscriptionCliTransport,
            outputClass = DeclineDeployment::class,
            apiKey = null,
            name = "claude-drafter",
            workspace = cliWorkspace("claude-draft").toString(),
            timeout = 3.minutes,
            permissionMode = ClaudePermissionMode.DontAsk,
            additionalFlags = claudeFlags,
            systemPrompt = Persona.PROMPT,
            generateRequest = ::claudeDraftRequest,
        )

    internal fun claudeDraftRequest(request: DeclineRequest): String =
        """
        Draft the best plan to get Baruch out of this obligation. Choose a flavor,
        write the message to the organizer, and write a brief hallway script.
        Honor the user's current instruction. Account for recently used flavors and
        known attendees. Choose a different approach from previouslyProposedFlavors;
        those were suggestions the user wants to move past, not sent-history records.
        You are drafting only: no calendar event has been created, so set
        fakeCalendarEventId to null. Do not claim you took any external action.
        Write concise, usable text rather than the perfect excuse. A public talk
        reasonably needs preparation; you need not invent a separate emergency.
        messageToOrganizer and hallwayScript are literal text for the organizer:
        no internal notes, avoided-flavor lists, instructions or placeholders.
        The application shows the avoided flavors separately. Request an exception
        or an alternative; do not claim the organizer has already agreed.

        CONTEXT: ${Scenario.USER_CONTEXT}
        OBLIGATION: ${request.eventId}
        REQUEST: $request
        """.trimIndent()

    fun claudeRefiner(): CliAIAgent<String, CliAgentStructuredResponse<DeclineDeployment>> =
        CliAIAgent.claude(
            transport = SubscriptionCliTransport,
            outputClass = DeclineDeployment::class,
            apiKey = null,
            name = "claude-refiner",
            workspace = cliWorkspace("claude-refine").toString(),
            timeout = 3.minutes,
            permissionMode = ClaudePermissionMode.DontAsk,
            additionalFlags = claudeFlags,
            systemPrompt = Persona.PROMPT,
            generateRequest = { feedback: String ->
                """
                Revise the proposed plan using the judge's feedback. Return the complete
                revised plan. Keep honoring the user's instruction and avoid the request's
                previouslyProposedFlavors; do not return to a suggestion the user moved past.
                No calendar event has been created, so set
                fakeCalendarEventId to null. Do not claim you took any external action.
                Fix the concrete blocker with the smallest useful change. Preserve
                an acceptable reason; do not chase a perfect excuse. Keep both scripts
                concise and ready to use, without internal notes or bracketed blanks.
                The application shows avoided flavors separately. If permission has
                not been granted, the hallway script must describe a pending request.

                CONTEXT: ${Scenario.USER_CONTEXT}
                PREVIOUS PLAN AND FEEDBACK:
                $feedback
                """.trimIndent()
            },
        )

    internal const val CODEX_SYSTEM_PROMPT = "You are a pragmatic independent reviewer of a proposed plan. " +
        "Decide whether it is a usable proposal, not whether it is perfect. " +
        "Return the requested structured result. Your approval is not permission to send."

    internal fun reviewTask(review: DeclineReview): String =
        """
        Baruch wants to get out of this obligation. Is this a usable proposal?
        Assess the message and hallway script against the following acceptance criteria.
        Judge the supplied plan and context; you have no tools or external actions.
        The application separately displays recentlyUsedFlavors and previouslyProposedFlavors
        to Baruch, satisfying the request to say which reasons are avoided. Those review
        notes must stay outside the literal organizer message and hallway script. Do not
        reject the candidate because that separate application display is not inside it.

        APPROVAL STANDARD (the same with or without a human reviewer):
        Approve a coherent, reasonably plausible proposal. CREDIBLE is sufficient;
        AIRTIGHT is not required. The organizer may say no: asking for an exemption
        or alternative completion is a valid plan. Approval ends automatic quality
        review; the application separately controls whether any action is permitted.
        A public talk reasonably needs preparation. Treat that as an ordinary inference,
        not a new fact that requires independent proof of workload or preparation time.
        Preparation for a deliverable is distinct from merely claiming another meeting
        at the same time. Judge the stated reason, not every hypothetical interpretation.
        Do not reject for optional wording improvements, mild uncertainty, a better
        imaginable excuse, or the absence of a guarantee that the organizer will agree.

        REJECT ONLY FOR A CONCRETE BLOCKER:
        - Wrong target/organizer or a material contradiction of supplied facts.
        - Reusing a sent or explicitly excluded reason, even under a different label.
        - Ignoring an explicit user constraint or the latest human feedback.
        - Claiming an external action or permission that has not happened.
        - Outbound text containing internal notes, placeholders or obvious insults.
        - An unrelated invented emergency, or a plainly implausible reason.
        A minor flavor-label mismatch alone is advice, not a blocker for an otherwise
        usable, fresh reason. A plain, respectful request for an exception can pass.
        If you reject, name at most two concrete blockers and the smallest repair;
        do not add new requirements or ask for proof of ordinary preparation needs.
        Keep feedback to two short sentences. If only optional improvements remain,
        approve and describe them as optional. Never force a rejection for demo drama.

        OBLIGATION: ${review.request.eventId}
        CONTEXT: ${Scenario.USER_CONTEXT}

        CURRENT REQUEST (including the user's latest constraints):
        ${review.request}

        Do not reuse recentlyUsedFlavors or previouslyProposedFlavors. Check the
        candidate against userInstruction; an approval of an earlier candidate
        does not approve this one.

        PROPOSED PLAN:
        ${review.plan}
        """.trimIndent()

    internal fun codexRequest(review: DeclineReview): String = reviewTask(review) +
        "\nSet approved=true when this exact plan passes the usable-proposal standard. " +
        "Set approved=false only for a concrete blocker above. Select the appropriate tier."

    /** The app-supplied prompt, before Codex adds its own CLI instructions. */
    internal fun codexPrompt(review: DeclineReview): String =
        "$CODEX_SYSTEM_PROMPT\n\n${codexRequest(review)}"

    fun codex(): CliAIAgent<DeclineReview, DeclineCritique> =
        TypedCodex.agent(
            serializer = serializer<DeclineCritique>(),
            systemPrompt = CODEX_SYSTEM_PROMPT,
            request = ::codexRequest,
        )
}

internal fun cliWorkspace(role: String): Path =
    Files.createTempDirectory("jclaw-$role-").also { it.toFile().deleteOnExit() }

/**
 * A null API-key argument does not remove an inherited API key. Strip API billing
 * and alternate-provider settings while retaining each CLI's subscription login.
 * env receives separate argv entries; no prompt or credential is sent through a shell.
 */
internal object SubscriptionCliTransport : ProcessCliTransport() {
    private val removedVariables = listOf(
        "OPENAI_API_KEY", "CODEX_API_KEY", "OPENAI_BASE_URL",
        "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_BASE_URL",
        "CLAUDE_CODE_USE_BEDROCK", "CLAUDE_CODE_USE_VERTEX", "CLAUDE_CODE_USE_FOUNDRY",
    )

    override fun buildCommand(
        command: List<String>,
        workspace: String,
        env: Map<String, String>,
    ): List<String> = listOf("/usr/bin/env") +
        removedVariables.flatMap { listOf("-u", it) } + command
}
