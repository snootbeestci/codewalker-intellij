package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.StepSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SummaryTableTest {

    @Test
    fun `initial state shows em-dashes for every row`() {
        val table = SummaryTable()

        repeat(8) { i ->
            assertEquals(SummaryTable.EMPTY_VALUE, table.valueLabelAt(i).text)
        }
    }

    @Test
    fun `update with all fields populated maps each field to the correct row`() {
        val table = SummaryTable()
        val summary = StepSummary.newBuilder()
            .setBreaking("No")
            .setRisk("Low — clean refactor")
            .setWhatChanged("Renames helper function")
            .setSideEffects("None")
            .setTests("Modified")
            .setReviewerFocus("Verify rename consistency")
            .setSuggestion("Consider deprecation alias")
            .setConfidence("High")
            .build()

        table.update(summary)

        assertEquals("No", table.valueLabelAt(0).text)
        assertEquals("Low — clean refactor", table.valueLabelAt(1).text)
        assertEquals("Renames helper function", table.valueLabelAt(2).text)
        assertEquals("None", table.valueLabelAt(3).text)
        assertEquals("Modified", table.valueLabelAt(4).text)
        assertEquals("Verify rename consistency", table.valueLabelAt(5).text)
        assertEquals("Consider deprecation alias", table.valueLabelAt(6).text)
        assertEquals("High", table.valueLabelAt(7).text)
    }

    @Test
    fun `empty fields render as em-dash`() {
        val table = SummaryTable()
        val summary = StepSummary.newBuilder()
            .setBreaking("Yes")
            .setRisk("")
            .setWhatChanged("Adds retry logic")
            .build()

        table.update(summary)

        assertEquals("Yes", table.valueLabelAt(0).text)
        assertEquals(SummaryTable.EMPTY_VALUE, table.valueLabelAt(1).text)
        assertEquals("Adds retry logic", table.valueLabelAt(2).text)
        assertEquals(SummaryTable.EMPTY_VALUE, table.valueLabelAt(3).text)
        assertEquals(SummaryTable.EMPTY_VALUE, table.valueLabelAt(7).text)
    }

    @Test
    fun `update with null clears all rows`() {
        val table = SummaryTable()
        table.update(
            StepSummary.newBuilder()
                .setBreaking("Yes")
                .setConfidence("High")
                .build()
        )

        table.update(null)

        repeat(8) { i ->
            assertEquals(SummaryTable.EMPTY_VALUE, table.valueLabelAt(i).text)
        }
    }

    @Test
    fun `clear resets all rows after a populated update`() {
        val table = SummaryTable()
        table.update(
            StepSummary.newBuilder()
                .setBreaking("No")
                .setRisk("Medium")
                .setWhatChanged("Refactors auth flow")
                .setConfidence("High")
                .build()
        )

        table.clear()

        repeat(8) { i ->
            assertEquals(SummaryTable.EMPTY_VALUE, table.valueLabelAt(i).text)
        }
    }
}
