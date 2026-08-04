package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionClassifier
import com.indianservers.smartboard.smartboard.recognition.SafeLatexPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs against Android's ICU regex engine, whose syntax is stricter than the desktop JVM engine
 * used by local unit tests.
 */
class SmartBoardAndroidMathRegexTest {
    @Test
    fun recognitionPatternsCompileAndClassifyOnAndroid() {
        val cases = mapOf(
            "[1,2;3,4]" to MathExpressionType.MATRIX,
            "\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}" to MathExpressionType.MATRIX,
            "\\begin{cases}x+y=3\\\\x-y=1\\end{cases}" to MathExpressionType.SYSTEM,
            "\\vec{v}=(1,2,3)" to MathExpressionType.VECTOR,
            "\\frac{d}{dx}x^2" to MathExpressionType.DERIVATIVE,
            "\\int_0^1 x dx" to MathExpressionType.INTEGRAL,
            "\\lim_{x\\to0}sin(x)/x" to MathExpressionType.LIMIT,
            "Sin(45)" to MathExpressionType.FUNCTION,
        )
        cases.forEach { (source, expected) ->
            assertEquals(source, expected, MathRecognitionClassifier.detect(source))
        }
        assertTrue(SafeLatexPreview.validate("\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}").isSuccess)
        assertEquals("[1,2;3,4]", SmartBoardLatexAdapter.toEngineExpression("\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}"))
    }
}
