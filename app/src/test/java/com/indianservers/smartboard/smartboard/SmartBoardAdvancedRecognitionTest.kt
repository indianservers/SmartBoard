package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.CorrectionGestureType
import com.indianservers.smartboard.smartboard.recognition.MultimodalMathRecognitionEngine
import com.indianservers.smartboard.smartboard.recognition.MathHandwritingRecognitionProvider
import com.indianservers.smartboard.smartboard.recognition.MathImageRecognitionProvider
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.RecognitionBenchmarkCase
import com.indianservers.smartboard.smartboard.recognition.RecognitionBenchmarkCorpus
import com.indianservers.smartboard.smartboard.recognition.RecognitionBenchmarkInputKind
import com.indianservers.smartboard.smartboard.recognition.RecognitionBenchmarkPrediction
import com.indianservers.smartboard.smartboard.recognition.RecognitionBenchmarkRecorder
import com.indianservers.smartboard.smartboard.recognition.RecognitionCandidateSource
import com.indianservers.smartboard.smartboard.recognition.SmartBoardCorrectionGestureDetector
import com.indianservers.smartboard.smartboard.recognition.SmartBoardRecognitionBenchmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardAdvancedRecognitionTest {
    @Test
    fun benchmarkComputesSemanticTopThreeCalibrationLatencyAndCorrectionMetrics() {
        val ink = line("ink", 0f, 0f, 20f, 20f)
        val corpus = RecognitionBenchmarkCorpus(
            name = "smoke",
            version = "1.0",
            cases = listOf(
                RecognitionBenchmarkCase(
                    "exact", SmartBoardSubject.MATHEMATICS, RecognitionBenchmarkInputKind.DIGITAL_INK,
                    listOf(ink), byteArrayOf(), "x^2", "x^2", setOf("algebra"),
                ),
                RecognitionBenchmarkCase(
                    "top3", SmartBoardSubject.MATHEMATICS, RecognitionBenchmarkInputKind.FUSED,
                    listOf(ink), byteArrayOf(1), "\\frac{x}{2}", "((x)/(2))", setOf("fraction"), 1,
                ),
            ),
        )
        val predictions = listOf(
            RecognitionBenchmarkPrediction("exact", "x^2", emptyList(), .9f, 40, 0),
            RecognitionBenchmarkPrediction("top3", "x/3", listOf("\\frac{x}{2}"), .6f, 120, 1),
        )
        val metrics = SmartBoardRecognitionBenchmark.metrics(corpus, predictions)
        assertEquals(2, metrics.caseCount)
        assertEquals(.5, metrics.exactLatexAccuracy, .0001)
        assertEquals(1.0, metrics.topThreeRecall, .0001)
        assertEquals(40, metrics.medianLatencyMillis)
        assertTrue(metrics.meanSymbolAccuracy > .5)
        val recorder = RecognitionBenchmarkRecorder("consented", "1.0", maximumCases = 2)
        corpus.cases.forEach(recorder::record)
        assertEquals(2, recorder.size())
        assertEquals(corpus.cases.map { it.id }, recorder.snapshot().cases.map { it.id })
    }

    @Test
    fun fusionRewardsProviderAgreementParserValidityAndPreviousStability() {
        val engine = MultimodalMathRecognitionEngine(
            digitalInk = object : MathHandwritingRecognitionProvider {
                override val id = "unused"
                override val productionReady = true
                override suspend fun recognize(input: MathRecognitionInput, options: MathRecognitionOptions) =
                    result("x", .8f, "y")
            },
            image = object : MathImageRecognitionProvider {
                override val id = "unused"
                override suspend fun recognize(png: ByteArray, maximumAlternatives: Int) =
                    result("x", .8f, "y")
            },
        )
        val digital = result("x^2+1", .82f, "x^2-1")
        val image = result("x^2+1", .76f, "x^2+l")
        val snapshot = engine.fuse("fingerprint", digital, image, previousPrimary = "x^2+1", latencyMillis = 80)
        val primary = snapshot.candidates.first()
        assertEquals("x^2+1", primary.text)
        assertTrue(primary.parserVerified)
        assertTrue(RecognitionCandidateSource.DIGITAL_INK in primary.sources)
        assertTrue(RecognitionCandidateSource.RASTER_IMAGE in primary.sources)
        assertTrue(RecognitionCandidateSource.PARSER in primary.sources)
        assertTrue(RecognitionCandidateSource.PREVIOUS_STABLE in primary.sources)
        assertEquals("x^2+1", snapshot.stablePrimary)
        assertTrue(snapshot.stability >= .9f)

        val sanitized = engine.fuse(
            "sanitized",
            result("\u200B\\[x+y=5\\]\uFEFF", .8f, "x+y=6"),
            null,
        )
        assertEquals("x+y=5", sanitized.result.latex)
        assertTrue(sanitized.candidates.first().parserVerified)
    }

    @Test
    fun correctionDetectorFindsScribbleAndStrikethroughWithoutDeletingAutomatically() {
        val target = line("target", 10f, 20f, 90f, 20f, createdAt = 1)
        val strike = line("strike", 0f, 21f, 100f, 21f, points = 8, createdAt = 100)
        val strikeSuggestion = SmartBoardCorrectionGestureDetector.detect(strike, listOf(target))
        assertNotNull(strikeSuggestion)
        assertEquals(CorrectionGestureType.STRIKETHROUGH_ERASE, strikeSuggestion?.type)
        assertEquals(setOf("target"), strikeSuggestion?.targetStrokeIds)

        val scribblePoints = listOf(
            p(10f, 10f), p(80f, 30f), p(15f, 35f), p(85f, 15f), p(20f, 12f), p(80f, 38f),
            p(18f, 32f), p(82f, 10f), p(25f, 36f), p(75f, 14f), p(30f, 34f), p(70f, 16f),
        )
        val scribble = stroke("scribble", scribblePoints, 200)
        val scribbleSuggestion = SmartBoardCorrectionGestureDetector.detect(scribble, listOf(target))
        assertEquals(CorrectionGestureType.SCRIBBLE_ERASE, scribbleSuggestion?.type)
        assertTrue(target.id in scribbleSuggestion?.targetStrokeIds.orEmpty())
    }

    @Test
    fun correctionDetectorDoesNotTreatNearbyMathematicalInkAsScribbleErase() {
        val nearbyInk = line("nearby", 40f, 20f, 55f, 20f, createdAt = 1)
        val symbolPoints = listOf(
            p(10f, 10f), p(20f, 5f), p(30f, 10f), p(35f, 18f), p(30f, 26f),
            p(20f, 30f), p(10f, 26f), p(15f, 22f), p(25f, 22f), p(35f, 32f),
            p(30f, 42f), p(20f, 48f), p(10f, 44f), p(18f, 38f),
        )

        val suggestion = SmartBoardCorrectionGestureDetector.detect(
            stroke("math-symbol", symbolPoints, 200),
            listOf(nearbyInk),
        )

        assertEquals(null, suggestion)
    }

    private fun result(primary: String, confidence: Float, alternative: String) = MathRecognitionResult(
        primary,
        primary,
        primary,
        confidence,
        listOf(MathRecognitionAlternative(alternative, confidence - .1f)),
        MathExpressionType.ALGEBRAIC_EXPRESSION,
        emptyList(),
    )

    private fun p(x: Float, y: Float) = SmartBoardPoint(x, y)

    private fun line(
        id: String,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        points: Int = 2,
        createdAt: Long = 1,
    ): StrokeElement {
        val values = (0 until points).map { index ->
            val ratio = index.toFloat() / (points - 1).coerceAtLeast(1)
            p(x1 + (x2 - x1) * ratio, y1 + (y2 - y1) * ratio)
        }
        return stroke(id, values, createdAt)
    }

    private fun stroke(id: String, points: List<SmartBoardPoint>, createdAt: Long) = StrokeElement(
        id,
        points.mapIndexed { index, point -> StrokePoint(point.x, point.y, 1f, createdAt + index * 20) },
        StrokeTool.PEN,
        3f,
        1f,
        0xff000000,
        SmartBoardBounds.from(points, 2f),
        createdAt,
    )
}
