package com.indianservers.smartboard.smartboard.recognition

import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureAwareRecognitionEnhancerTest {
    private val enhancer = StructureAwareRecognitionEnhancer()

    @After
    fun restoreFeatureFlag() {
        RecognitionAccuracyFeatures.structureAwareRecognitionEnabled = true
    }

    @Test
    fun singleDigitSuperscript() {
        assertEquals("x^2", assemble("x2", baseline(), superscript()))
    }

    @Test
    fun multiDigitSuperscriptIsGrouped() {
        assertEquals("x^(10)", assemble("x10", baseline(), superscript(), superscript()))
    }

    @Test
    fun signedSuperscriptIsGrouped() {
        assertEquals("x^(-2)", assemble("x-2", baseline(), superscript(), superscript()))
    }

    @Test
    fun fractionalSuperscriptIsGrouped() {
        assertEquals("a^(3/2)", assemble("a3/2", baseline(), superscript(), superscript(), superscript()))
    }

    @Test
    fun parenthesizedPowerAttachesToClosingContainer() {
        assertEquals("(x+3)^2", assemble("(x+3)2", *baselineSymbols(5), superscript()))
    }

    @Test
    fun nestedPowerKeepsBothAttachments() {
        assertEquals("(x^2)^3", assemble("(x2)3", baseline(), baseline(), superscript(), baseline(), superscript()))
    }

    @Test
    fun simpleSubscript() {
        assertEquals("x_i", assemble("xi", baseline(), subscript()))
    }

    @Test
    fun groupedSubscript() {
        assertEquals("a_(n+1)", assemble("an+1", baseline(), subscript(), subscript(), subscript()))
    }

    @Test
    fun mixedSubscriptAndSuperscript() {
        assertEquals("x_i^2", assemble("xi2", baseline(), subscript(), superscript()))
    }

    @Test
    fun logarithmBaseUsesSubscriptGeometry() {
        assertEquals("log_a(x)", assemble("loga(x)", *baselineSymbols(3), subscript(), *baselineSymbols(3)))
    }

    @Test
    fun parenthesizedExponentRemainsOneGroup() {
        assertEquals("e^(x+y)", assemble("e(x+y)", baseline(), *superscriptSymbols(5)))
    }

    @Test
    fun stackedFractionRequiresBarAndVerticalOrdering() {
        assertTrue(
            enhancer.isStackedFraction(
                box(20f, 0f, 40f, 15f),
                box(10f, 20f, 50f, 22f),
                box(18f, 28f, 42f, 44f),
            ),
        )
    }

    @Test
    fun sideBySideDivisionIsNotAStackedFraction() {
        assertFalse(
            enhancer.isStackedFraction(
                box(0f, 10f, 12f, 30f),
                box(18f, 5f, 22f, 35f),
                box(28f, 10f, 40f, 30f),
            ),
        )
    }

    @Test
    fun radicalAttachesOnlyToOverbarRegion() {
        val radical = box(0f, 0f, 25f, 40f)
        assertTrue(enhancer.isRadicalAttachment(radical, box(15f, 7f, 42f, 36f)))
        assertFalse(enhancer.isRadicalAttachment(radical, box(70f, 7f, 92f, 36f)))
    }

    @Test
    fun radicalGlyphRepairsOcrLetterRWithoutAnswerLookup() {
        val radical = polyline(
            "radical",
            listOf(0f to 24f, 6f to 38f, 13f to 4f, 31f to 4f),
        )
        val content = stroke("content", 38f, 6f, 58f, 38f)
        assertEquals(
            "sqrt(x+5)=7",
            enhancer.repairSpatialGlyphs("r(x+5)=7", listOf(radical, content)),
        )
    }

    @Test
    fun pairedVerticalBarsRepairAbsoluteValueOnTheLeftSideOnly() {
        val strokes = listOf(
            stroke("left-bar", 0f, 0f, 1f, 45f),
            stroke("x", 12f, 2f, 28f, 44f),
            stroke("minus", 34f, 22f, 49f, 23f),
            stroke("three", 56f, 2f, 72f, 44f),
            stroke("right-bar", 82f, 0f, 83f, 45f),
        )
        assertEquals("|x-3|=7", enhancer.repairSpatialGlyphs("1x-31=7", strokes))
    }

    @Test
    fun summationUpperAndLowerLimitsAreSpatiallyDistinct() {
        val operator = box(20f, 20f, 50f, 70f)
        assertTrue(enhancer.isLimitAttachment(operator, box(26f, 0f, 44f, 15f), upper = true))
        assertTrue(enhancer.isLimitAttachment(operator, box(24f, 76f, 47f, 91f), upper = false))
        assertFalse(enhancer.isLimitAttachment(operator, box(80f, 0f, 98f, 15f), upper = true))
    }

    @Test
    fun productLimitUsesTheSameGeneralRelation() {
        val operator = box(10f, 30f, 42f, 80f)
        assertTrue(enhancer.isLimitAttachment(operator, box(16f, 4f, 34f, 22f), upper = true))
        assertFalse(enhancer.isLimitAttachment(operator, box(14f, 42f, 34f, 60f), upper = true))
    }

    @Test
    fun geometryInferenceFindsRaisedAndLoweredGroups() {
        val strokes = listOf(
            stroke("x", 0f, 30f, 12f, 74f),
            stroke("power", 20f, 2f, 30f, 24f),
            stroke("sub", 38f, 58f, 48f, 80f),
            stroke("y", 56f, 30f, 68f, 74f),
        )
        assertEquals(
            listOf(SpatialZone.BASELINE, SpatialZone.SUPERSCRIPT, SpatialZone.SUBSCRIPT, SpatialZone.BASELINE),
            enhancer.inferSymbols(strokes).map(SpatialSymbol::zone),
        )
    }

    @Test
    fun translatedAndScaledInkProducesTheSameRelations() {
        val original = listOf(
            stroke("a", 0f, 30f, 12f, 74f),
            stroke("power", 20f, 2f, 30f, 24f),
            stroke("b", 38f, 30f, 50f, 74f),
        )
        val transformed = original.mapIndexed { index, item ->
            stroke(
                "scaled-$index",
                item.bounds.left * 2.5f + 180f,
                item.bounds.top * 2.5f + 90f,
                item.bounds.right * 2.5f + 180f,
                item.bounds.bottom * 2.5f + 90f,
            )
        }
        assertEquals(
            enhancer.inferSymbols(original).map(SpatialSymbol::zone),
            enhancer.inferSymbols(transformed).map(SpatialSymbol::zone),
        )
    }

    @Test
    fun twoStraightCrossingRaisedStrokesProvideExponentXEvidence() {
        val strokes = listOf(
            stroke("base", 0f, 30f, 15f, 74f),
            polyline("x-down", listOf(22f to 4f, 38f to 26f)),
            polyline("x-up", listOf(38f to 4f, 22f to 26f)),
            stroke("rhs", 50f, 30f, 65f, 74f),
        )
        assertTrue(enhancer.raisedGlyphLooksLikeX(strokes))
    }

    @Test
    fun raisedForkAndDescenderAreNotMisclassifiedAsExponentX() {
        val strokes = listOf(
            stroke("base", 0f, 30f, 15f, 74f),
            polyline("fork", listOf(22f to 4f, 30f to 17f, 38f to 4f)),
            polyline("descender", listOf(30f to 17f, 27f to 30f)),
            stroke("rhs", 50f, 30f, 65f, 74f),
        )
        assertFalse(enhancer.raisedGlyphLooksLikeX(strokes))
    }

    @Test
    fun alignedSmallDigitIsNotPromotedWithoutVerticalEvidence() {
        assertEquals("x2", assemble("x2", baseline(), baseline()))
    }

    @Test
    fun horizontalMinusRemainsOnBaseline() {
        val symbols = enhancer.inferSymbols(
            listOf(
                stroke("x", 0f, 30f, 12f, 74f),
                stroke("minus", 20f, 51f, 43f, 53f),
                stroke("one", 52f, 30f, 64f, 74f),
            ),
        )
        assertEquals(SpatialZone.BASELINE, symbols[1].zone)
    }

    @Test
    fun candidateAndGeometryCountMustAgree() {
        assertNull(enhancer.assemble("x20", listOf(baseline(), superscript())))
    }

    @Test
    fun conservativeGatePromotesParserVerifiedStructureAndRetainsOriginal() {
        val source = snapshot("(x+3)2")
        val strokes = listOf(
            stroke("open", 0f, 30f, 9f, 74f),
            stroke("x", 18f, 30f, 30f, 74f),
            stroke("plus", 38f, 30f, 50f, 74f),
            stroke("three", 58f, 30f, 70f, 74f),
            stroke("close", 78f, 30f, 87f, 74f),
            stroke("power", 95f, 2f, 105f, 24f),
        )
        val result = enhancer.enhance(source, strokes)
        assertEquals("(x+3)^2", result.snapshot.result.latex)
        assertTrue(result.snapshot.result.alternatives.any { it.latex == "(x+3)2" })
        assertTrue(result.diagnostics.accepted)
    }

    @Test
    fun featureFlagProvidesExactFallback() {
        val source = snapshot("x2")
        RecognitionAccuracyFeatures.structureAwareRecognitionEnabled = false
        val result = enhancer.enhance(
            source,
            listOf(stroke("x", 0f, 30f, 12f, 74f), stroke("two", 20f, 2f, 30f, 24f)),
        )
        assertSame(source, result.snapshot)
        assertFalse(result.diagnostics.accepted)
    }

    @Test
    fun enhancementStaysWithinInteractiveAverageBudget() {
        val source = snapshot("(x+3)2")
        val strokes = listOf(
            stroke("open", 0f, 30f, 9f, 74f),
            stroke("x", 18f, 30f, 30f, 74f),
            stroke("plus", 38f, 30f, 50f, 74f),
            stroke("three", 58f, 30f, 70f, 74f),
            stroke("close", 78f, 30f, 87f, 74f),
            stroke("power", 95f, 2f, 105f, 24f),
        )
        repeat(5) { enhancer.enhance(source, strokes) }
        val started = System.nanoTime()
        repeat(100) { enhancer.enhance(source, strokes) }
        val averageMillis = (System.nanoTime() - started) / 1_000_000.0 / 100.0
        assertTrue("Average enhancement took $averageMillis ms", averageMillis < 35.0)
    }

    private fun assemble(candidate: String, vararg symbols: SpatialSymbol) =
        enhancer.assemble(candidate, symbols.toList())

    private fun baseline() = SpatialSymbol(box(0f, 25f, 12f, 70f))
    private fun superscript() =
        SpatialSymbol(box(0f, 0f, 9f, 22f), SpatialZone.SUPERSCRIPT, .95f)
    private fun subscript() =
        SpatialSymbol(box(0f, 58f, 9f, 80f), SpatialZone.SUBSCRIPT, .95f)
    private fun baselineSymbols(count: Int) = Array(count) { baseline() }
    private fun superscriptSymbols(count: Int) = Array(count) { superscript() }

    private fun box(left: Float, top: Float, right: Float, bottom: Float) =
        SmartBoardBounds(left, top, right, bottom)

    private fun stroke(
        id: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): StrokeElement {
        val points = listOf(
            StrokePoint(left, top, .7f, 1L),
            StrokePoint(right, bottom, .7f, 2L),
        )
        return StrokeElement(
            id,
            points,
            StrokeTool.PEN,
            3f,
            1f,
            0xFFFFFFFF,
            SmartBoardBounds.from(points.map(StrokePoint::position)),
            2L,
        )
    }

    private fun polyline(id: String, coordinates: List<Pair<Float, Float>>): StrokeElement {
        val points = coordinates.flatMapIndexed { index, (x, y) ->
            if (index == coordinates.lastIndex) {
                listOf(StrokePoint(x, y, .7f, index.toLong()))
            } else {
                val (nextX, nextY) = coordinates[index + 1]
                List(5) { step ->
                    val amount = step / 5f
                    StrokePoint(
                        x + (nextX - x) * amount,
                        y + (nextY - y) * amount,
                        .7f,
                        (index * 5L) + step,
                    )
                }
            }
        }
        return StrokeElement(
            id,
            points,
            StrokeTool.PEN,
            3f,
            1f,
            0xFFFFFFFF,
            SmartBoardBounds.from(points.map(StrokePoint::position)),
            points.last().timestampMillis,
        )
    }

    private fun snapshot(text: String): StreamingRecognitionSnapshot {
        val candidate = RecognitionLatticeCandidate(
            text,
            text,
            .58f,
            setOf(RecognitionCandidateSource.DIGITAL_INK),
            parserVerified = true,
            detectedType = MathExpressionType.ALGEBRAIC_EXPRESSION,
        )
        return StreamingRecognitionSnapshot(
            "test",
            listOf(candidate),
            null,
            .45f,
            10L,
            MathRecognitionResult(
                latex = text,
                normalizedExpression = text,
                plainText = text,
                confidence = .58f,
                alternatives = emptyList(),
                detectedType = MathExpressionType.ALGEBRAIC_EXPRESSION,
                warnings = emptyList(),
            ),
        )
    }
}
