package jclaw

import com.jbaruch.jclaw.tui.CandidateView
import com.jbaruch.jclaw.tui.DemoOutcome
import com.jbaruch.jclaw.tui.JclawTui
import com.jbaruch.jclaw.tui.StageState
import dev.tamboui.buffer.Buffer
import dev.tamboui.layout.Rect
import dev.tamboui.terminal.Frame
import dev.tamboui.toolkit.element.RenderContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** Render the actual Toolkit elements; no terminal, provider or delivery is involved. */
private fun assistantScreen(tui: JclawTui): List<String> {
    tui.onStart() // Drain the same pre-start render-thread queue used by the app.
    val area = Rect(0, 0, 120, 40)
    val buffer = Buffer.empty(area)
    // This context draws children without an event router or its live render-thread registry.
    val context = object : RenderContext {
        override fun isFocused(elementId: String) = false
        override fun hasFocus() = false
    }
    tui.render().render(Frame.forTesting(buffer), area, context)
    return (0 until area.height()).map { y ->
        (0 until area.width()).joinToString("") { x -> buffer.get(x, y).symbol() }.trimEnd()
    }
}

class TuiAssistantTest : StringSpec({
    "startup presents a personal assistant without a predefined decline flow" {
        val text = assistantScreen(JclawTui(onSubmit = {})).joinToString("\n")
        text.shouldContain("CONVERSATION")
        text.shouldContain("WORKSPACE")
        text.shouldContain("ACTIVITY")
        text.shouldContain("Ask j-claw a question")
        listOf("readCalendar", "Tuesday", "current draft", "Human:", "Delivery:", "candidate 0").forEach {
            text.shouldNotContain(it)
        }
    }

    "the ribbon follows arbitrary actual executions including another visit" {
        val tui = JclawTui(onSubmit = {})
        tui.resetFlow("Summarize my notes")
        tui.stage("searchNotes", StageState.ACTIVE)
        tui.stage("searchNotes", StageState.DONE)
        tui.stage("summarize", StageState.ACTIVE)
        tui.stage("summarize", StageState.DONE)
        tui.stage("searchNotes", StageState.ACTIVE)
        val ribbon = assistantScreen(tui)[1]
        ribbon.shouldContain("searchNotes ✓")
        ribbon.shouldContain("summarize ✓")
        ribbon.shouldContain("searchNotes ●")
        ribbon.shouldNotContain("calendar")
        ribbon.shouldNotContain("verify")
    }

    "a normal follow-up replaces a reviewed candidate with the current answer" {
        val tui = JclawTui(onSubmit = {})
        tui.resetFlow("Decline a meeting")
        tui.candidate(CandidateView("OTHER", "Organizer", "meeting-1", "Old proposal",
            "Old hallway script", "Decline a meeting", 1))
        tui.reviewResult(true, "Old review")
        tui.deliveryConfirmed("old-receipt")
        assistantScreen(tui).joinToString("\n").shouldContain("Old proposal")

        tui.resetFlow("Rewrite this release update")
        tui.stage("applyStyle", StageState.ACTIVE)
        tui.stage("applyStyle", StageState.DONE)
        tui.work("The release is delayed. An update follows tomorrow.")
        tui.outcome(DemoOutcome.CHAT, "Assistant reply complete")
        val text = assistantScreen(tui).joinToString("\n")
        text.shouldContain("The release is delayed")
        text.shouldContain("applyStyle ✓")
        listOf("Old proposal", "Old review", "old-receipt", "Human:", "Delivery:", "HUMAN DECISION").forEach {
            text.shouldNotContain(it)
        }
    }
})
