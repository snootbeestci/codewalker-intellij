package com.snootbeestci.codewalker.toolwindow

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JLabel

class CollapsibleSectionTest {

    @Test
    fun `default collapsed hides content`() {
        val content = JLabel("body")

        val section = CollapsibleSection("Detail", content)

        assertFalse(section.isExpanded())
        assertFalse(content.isVisible)
    }

    @Test
    fun `expanded=true shows content`() {
        val content = JLabel("body")

        val section = CollapsibleSection("Detail", content, expanded = true)

        assertTrue(section.isExpanded())
        assertTrue(content.isVisible)
    }

    @Test
    fun `expand on a collapsed section shows content`() {
        val content = JLabel("body")
        val section = CollapsibleSection("Detail", content)

        section.expand()

        assertTrue(section.isExpanded())
        assertTrue(content.isVisible)
    }

    @Test
    fun `collapse on an expanded section hides content`() {
        val content = JLabel("body")
        val section = CollapsibleSection("Detail", content, expanded = true)

        section.collapse()

        assertFalse(section.isExpanded())
        assertFalse(content.isVisible)
    }

    @Test
    fun `repeated expand stays expanded`() {
        val content = JLabel("body")
        val section = CollapsibleSection("Detail", content, expanded = true)

        section.expand()

        assertTrue(section.isExpanded())
        assertTrue(content.isVisible)
    }

    @Test
    fun `repeated collapse stays collapsed`() {
        val content = JLabel("body")
        val section = CollapsibleSection("Detail", content)

        section.collapse()

        assertFalse(section.isExpanded())
        assertFalse(content.isVisible)
    }
}
