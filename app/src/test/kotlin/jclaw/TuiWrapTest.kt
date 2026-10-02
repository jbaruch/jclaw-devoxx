package jclaw

import com.jbaruch.jclaw.tui.JclawTui
import dev.tamboui.text.CharWidth
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TuiWrapTest : StringSpec({
    "long wide-glyph tokens fit the actual terminal columns without losing text" {
        val token = "資料😀".repeat(15)
        val rows = JclawTui.wrap(token, 11)
        rows.all { CharWidth.of(it) <= 11 } shouldBe true
        rows.joinToString("") shouldBe token
    }
    "wrapping preserves paragraph gaps and fits long receipt identifiers" {
        JclawTui.wrap("one two\n\nthree", 5) shouldBe listOf("one", "two", "", "three")
        val id = "0123456789abcdef".repeat(4)
        val rows = JclawTui.wrap(id, 23)
        rows.all { CharWidth.of(it) <= 23 } shouldBe true
        rows.joinToString("") shouldBe id
    }
})
