package com.indianservers.smartboard.smartboard.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardAuditRegressionTest {
    @Test
    fun corpusRetainsRequiredCoverageAndDifficultyDistribution() {
        val cases = SmartBoardAuditDataset.cases
        assertEquals(560, cases.size)
        AuditCategory.entries.forEach { category ->
            val rows = cases.filter { it.category == category }
            assertEquals(40, rows.size)
            assertEquals(10, rows.count { it.difficulty == AuditDifficulty.EASY })
            assertEquals(12, rows.count { it.difficulty == AuditDifficulty.MEDIUM })
            assertEquals(12, rows.count { it.difficulty == AuditDifficulty.HARD })
            assertEquals(6, rows.count { it.difficulty == AuditDifficulty.EXTREME })
        }
        assertTrue(cases.count { "venn" in it.tags } >= 9)
        assertTrue(cases.count { "delayed" in it.strokeVariant || "fraction-bar" in it.strokeVariant || "reverse" in it.strokeVariant } >= 100)
    }

    @Test
    fun normalizationDoesNotHideFractionStructureFailure() {
        val case = SmartBoardAuditDataset.cases.first {
            it.category == AuditCategory.FRACTIONS_RATIONAL && it.expectedPlainText == "(x+1)/(x-1)"
        }
        val comparison = SmartBoardAuditScoring.compare(case, "(x+1)-(x-1)", .9f)
        assertFalse(comparison.structure)
        assertFalse(comparison.layout)
        assertFalse(comparison.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION))
        assertTrue(AuditErrorType.FRACTION_MISREAD in comparison.errors)
    }

    @Test
    fun harmlessLatexPresentationDifferencesNormalize() {
        assertEquals(
            SmartBoardAuditScoring.normalize("x^{2}+\\pi"),
            SmartBoardAuditScoring.normalize("x^2+pi"),
        )
    }
}
