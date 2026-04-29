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

class SeverityParserTest {

    @Test
    fun `breaking yes is red`() {
        assertEquals(Severity.RED, SeverityParser.forBreaking("Yes"))
    }

    @Test
    fun `breaking no is green`() {
        assertEquals(Severity.GREEN, SeverityParser.forBreaking("No"))
    }

    @Test
    fun `breaking empty is neutral`() {
        assertEquals(Severity.NEUTRAL, SeverityParser.forBreaking(""))
    }

    @Test
    fun `breaking placeholder is neutral`() {
        assertEquals(Severity.NEUTRAL, SeverityParser.forBreaking("—"))
    }

    @Test
    fun `breaking with trailing prose is yes`() {
        assertEquals(Severity.RED, SeverityParser.forBreaking("Yes — removes a public method"))
    }

    @Test
    fun `breaking case-insensitive`() {
        assertEquals(Severity.RED, SeverityParser.forBreaking("YES"))
        assertEquals(Severity.GREEN, SeverityParser.forBreaking("no"))
    }

    @Test
    fun `breaking unknown defaults to green`() {
        assertEquals(Severity.GREEN, SeverityParser.forBreaking("maybe"))
    }

    @Test
    fun `risk levels parse correctly`() {
        assertEquals(Severity.GREEN, SeverityParser.forRisk("Low"))
        assertEquals(Severity.AMBER, SeverityParser.forRisk("Medium"))
        assertEquals(Severity.RED, SeverityParser.forRisk("High"))
    }

    @Test
    fun `risk with explanation parses on first word`() {
        assertEquals(Severity.AMBER, SeverityParser.forRisk("Medium — touches payment flow"))
        assertEquals(Severity.RED, SeverityParser.forRisk("High, breaks API contract"))
    }

    @Test
    fun `risk empty is neutral`() {
        assertEquals(Severity.NEUTRAL, SeverityParser.forRisk(""))
    }

    @Test
    fun `risk placeholder is neutral`() {
        assertEquals(Severity.NEUTRAL, SeverityParser.forRisk("—"))
    }

    @Test
    fun `risk unknown defaults to green`() {
        assertEquals(Severity.GREEN, SeverityParser.forRisk("Probably low"))
    }

    @Test
    fun `tests added or modified is green`() {
        assertEquals(Severity.GREEN, SeverityParser.forTests("Added"))
        assertEquals(Severity.GREEN, SeverityParser.forTests("Modified"))
    }

    @Test
    fun `tests missing is amber`() {
        assertEquals(Severity.AMBER, SeverityParser.forTests("Missing"))
    }

    @Test
    fun `tests empty is neutral`() {
        assertEquals(Severity.NEUTRAL, SeverityParser.forTests(""))
    }

    @Test
    fun `worst red dominates`() {
        assertEquals(
            Severity.RED,
            SeverityParser.worst(Severity.GREEN, Severity.AMBER, Severity.RED),
        )
    }

    @Test
    fun `worst amber when no red`() {
        assertEquals(
            Severity.AMBER,
            SeverityParser.worst(Severity.GREEN, Severity.AMBER, Severity.GREEN),
        )
    }

    @Test
    fun `worst green when all green`() {
        assertEquals(
            Severity.GREEN,
            SeverityParser.worst(Severity.GREEN, Severity.GREEN, Severity.GREEN),
        )
    }

    @Test
    fun `worst neutral when all neutral`() {
        assertEquals(
            Severity.NEUTRAL,
            SeverityParser.worst(Severity.NEUTRAL, Severity.NEUTRAL, Severity.NEUTRAL),
        )
    }

    @Test
    fun `worst ignores neutral fields`() {
        assertEquals(
            Severity.AMBER,
            SeverityParser.worst(Severity.NEUTRAL, Severity.AMBER, Severity.NEUTRAL),
        )
    }
}
