package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.canvas.SmartBoardStrokeGeometry
import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.MathHandwritingRecognitionProvider
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionClassifier
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import com.indianservers.smartboard.smartboard.recognition.MathematicsSubjectHandler
import com.indianservers.smartboard.smartboard.recognition.SafeLatexPreview
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSubjectRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardRecognitionTest {
    @Test
    fun recognitionRequestAddsPaddingPreservesVectorsAndHasStableFingerprint() {
        val stroke = stroke("s1")
        val first = MathRecognitionRequestBuilder.build("board", listOf(stroke), 10L, padding = 15f)
        val second = MathRecognitionRequestBuilder.build("board", listOf(stroke), 11L, padding = 15f)

        assertEquals(listOf("s1"), first.strokeIds)
        assertEquals(stroke.points, first.strokes.single().points)
        assertTrue(first.bounds.left < stroke.bounds.left)
        assertEquals(MathRecognitionRequestBuilder.fingerprint(first), MathRecognitionRequestBuilder.fingerprint(second))
    }

    @Test
    fun mathematicsHandlerMapsProviderResultAndRouterRejectsUnimplementedSubjects() = runBlocking {
        val expected = MathRecognitionResult(
            latex = "x^2=4",
            normalizedExpression = "x^2=4",
            plainText = "x squared equals four",
            confidence = .9f,
            alternatives = listOf(MathRecognitionAlternative("x^2=9", .2f)),
            detectedType = MathExpressionType.EQUATION,
            warnings = emptyList(),
        )
        val provider = object : MathHandwritingRecognitionProvider {
            override val id = "test"
            override val productionReady = true
            override suspend fun recognize(input: MathRecognitionInput, options: MathRecognitionOptions) = expected
        }
        val handler = MathematicsSubjectHandler(provider)
        val input = MathRecognitionRequestBuilder.build("board", listOf(stroke("s1")), 1L)

        assertEquals(expected, handler.analyze(input).recognition)
        assertEquals(handler, SmartBoardSubjectRouter.handler(SmartBoardSubject.MATHEMATICS, handler))
        assertThrows(IllegalStateException::class.java) {
            SmartBoardSubjectRouter.handler(SmartBoardSubject.PHYSICS, handler)
        }
        Unit
    }

    @Test
    fun classifierCoversPhase1FamiliesAndMalformedLatexIsRejectedSafely() {
        assertEquals(MathExpressionType.INEQUALITY, MathRecognitionClassifier.detect("x >= 2"))
        assertEquals(MathExpressionType.CALCULUS, MathRecognitionClassifier.detect("\\int x dx"))
        assertEquals(MathExpressionType.MATRIX, MathRecognitionClassifier.detect("[1,2;3,4]"))
        assertEquals(MathExpressionType.FUNCTION, MathRecognitionClassifier.detect("Sin(45)"))
        assertTrue(SafeLatexPreview.validate("\\frac{1}{2}").isSuccess)
        assertFalse(SafeLatexPreview.validate("\\frac{1{2}").isSuccess)
        assertFalse(SafeLatexPreview.validate("\\input{secret}").isSuccess)
        assertFalse(SafeLatexPreview.validate("\\newcommand{\\x}{secret}").isSuccess)
        assertFalse(SafeLatexPreview.validate("\\unknownmath{x}").isSuccess)
        assertTrue(SafeLatexPreview.validate("\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}").isSuccess)
    }

    private fun stroke(id: String): StrokeElement {
        val points = listOf(StrokePoint(10f, 10f, 1f, 1L), StrokePoint(40f, 40f, 1f, 2L))
        return StrokeElement(id, points, StrokeTool.PEN, 3f, 1f, 0xff000000, SmartBoardStrokeGeometry.bounds(points, 3f), 1L)
    }
}
