package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.recognition.normalizeTexTellerLatex
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineMathRecognitionNormalizationTest {
    @Test
    fun stripsFormulaDisplayWrappersWithoutChangingMatrixBrackets() {
        assertEquals("x+2=5", normalizeTexTellerLatex("\\[x+2=5\\]"))
        assertEquals("\\frac{1}{2}", normalizeTexTellerLatex("$$\\frac{1}{2}$$"))
        assertEquals("[1,2;3,4]", normalizeTexTellerLatex("[1,2;3,4]"))
    }

    @Test
    fun stripsDisplayStyleAfterOuterWrapper() {
        assertEquals("\\sin(45)", normalizeTexTellerLatex("\\[\\displaystyle \\sin(45)\\]"))
        assertEquals("4.5", normalizeTexTellerLatex("\\[\u200B4.5\uFEFF\\]"))
    }

    @Test
    fun repairsTangentFunctionGlyphConfusionWithoutChangingOrdinaryWords() {
        assertEquals("\\tan(x)=1", normalizeTexTellerLatex("ton(x)=1"))
        assertEquals("\\tan\\left(x\\right)", normalizeTexTellerLatex("\\ton\\left(x\\right)"))
        assertEquals("one ton", normalizeTexTellerLatex("one ton"))
    }

    @Test
    fun removesOnlyRedundantWholeExpressionLatexGrouping() {
        assertEquals("3^{2x}=27", normalizeTexTellerLatex("{3^{2x}}=27"))
        assertEquals("x+1", normalizeTexTellerLatex("{x+1}"))
        assertEquals("{x+1}y", normalizeTexTellerLatex("{x+1}y"))
        assertEquals("{x+1", normalizeTexTellerLatex("{x+1"))
    }
}
