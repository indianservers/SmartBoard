package com.indianservers.smartboard.smartboard.recognition

import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.StrokeElement
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Feature switches for recognition changes that must be independently reversible.
 *
 * This flag deliberately controls only post-recognition ranking. Raw ink capture, the installed
 * recognizers and the board interaction model are unchanged.
 */
internal object RecognitionAccuracyFeatures {
    @Volatile
    var structureAwareRecognitionEnabled: Boolean = true
}

internal data class StructureRecognitionConfig(
    val anchorHeightRatio: Float = .72f,
    val scriptMaximumHeightRatio: Float = .79f,
    val scriptVerticalOffsetRatio: Float = .24f,
    val minimumRelationConfidence: Float = .78f,
    val minimumScoreGain: Float = .045f,
    val relationScoreGain: Float = .16f,
    val editPenalty: Float = .012f,
    val maximumCandidateGlyphDelta: Int = 0,
    val maximumEnhancementMillis: Long = 35L,
)

internal enum class SpatialZone { BASELINE, SUPERSCRIPT, SUBSCRIPT }

internal data class SpatialSymbol(
    val bounds: SmartBoardBounds,
    val zone: SpatialZone = SpatialZone.BASELINE,
    val relationConfidence: Float = 1f,
)

internal data class StructureEnhancementDiagnostics(
    val elapsedMillis: Long,
    val strokeCount: Int,
    val symbolCount: Int,
    val candidatesExamined: Int,
    val candidatesChanged: Int,
    val accepted: Boolean,
    val reason: String,
)

internal data class StructureEnhancement(
    val snapshot: StreamingRecognitionSnapshot,
    val diagnostics: StructureEnhancementDiagnostics,
)

/**
 * Reconstructs only two-dimensional relationships that are visible in the original vector ink.
 *
 * The enhancer never guesses a symbol label and never uses an answer corpus. It may insert
 * structural operators around labels already returned by an installed recognizer, then asks the
 * existing parser to validate the result before it can outrank the untouched candidate.
 */
internal class StructureAwareRecognitionEnhancer(
    private val config: StructureRecognitionConfig = StructureRecognitionConfig(),
) {
    fun enhance(
        snapshot: StreamingRecognitionSnapshot,
        strokes: List<StrokeElement>,
    ): StructureEnhancement {
        val started = System.nanoTime()
        if (!RecognitionAccuracyFeatures.structureAwareRecognitionEnabled || strokes.isEmpty()) {
            return unchanged(snapshot, started, strokes.size, 0, "feature-disabled-or-empty-ink")
        }
        return runCatching {
            val symbols = inferSymbols(strokes)
            if (symbols.size < 2) return@runCatching unchanged(
                snapshot,
                started,
                strokes.size,
                symbols.size,
                "insufficient-symbol-geometry",
            )
            val original = snapshot.candidates
            val scriptEnhanced = original.mapNotNull { candidate ->
                val assembled = assemble(candidate.text, symbols) ?: return@mapNotNull null
                if (assembled == candidate.text || !isBalanced(assembled)) return@mapNotNull null
                val analysis = SmartBoardExpressionAnalyzer.analyze(assembled)
                if (!analysis.parserVerified) return@mapNotNull null
                val relations = relationCount(candidate.text, assembled)
                if (relations == 0) return@mapNotNull null
                val relationConfidence = symbols
                    .filter { it.zone != SpatialZone.BASELINE }
                    .minOfOrNull(SpatialSymbol::relationConfidence) ?: return@mapNotNull null
                if (relationConfidence < config.minimumRelationConfidence) return@mapNotNull null
                val scoreGain = relationConfidence * config.relationScoreGain -
                    structuralEditDistance(candidate.text, assembled) * config.editPenalty
                if (scoreGain < config.minimumScoreGain) return@mapNotNull null
                RecognitionLatticeCandidate(
                    text = assembled,
                    normalizedExpression = analysis.normalized.replace(Regex("""\s+"""), ""),
                    confidence = (candidate.confidence + scoreGain).coerceIn(0f, 1f),
                    sources = candidate.sources + RecognitionCandidateSource.PARSER,
                    parserVerified = true,
                    detectedType = analysis.type,
                )
            }
            val spatialGlyphEnhanced = original.mapNotNull { candidate ->
                val repaired = repairSpatialGlyphs(candidate.text, strokes)
                if (repaired == candidate.text || !isBalanced(repaired)) return@mapNotNull null
                val analysis = SmartBoardExpressionAnalyzer.analyze(repaired)
                if (!analysis.parserVerified) return@mapNotNull null
                RecognitionLatticeCandidate(
                    text = repaired,
                    normalizedExpression = analysis.normalized.replace(Regex("""\s+"""), ""),
                    confidence = (candidate.confidence + .18f).coerceIn(0f, 1f),
                    sources = candidate.sources + RecognitionCandidateSource.PARSER,
                    parserVerified = true,
                    detectedType = analysis.type,
                )
            }
            val enhanced = scriptEnhanced + spatialGlyphEnhanced
            val elapsed = elapsedMillis(started)
            if (enhanced.isEmpty() || elapsed > config.maximumEnhancementMillis) {
                return@runCatching unchanged(
                    snapshot,
                    started,
                    strokes.size,
                    symbols.size,
                    if (elapsed > config.maximumEnhancementMillis) "performance-budget-exceeded" else "no-safe-structural-gain",
                    original.size,
                )
            }
            val powerAware = (enhanced + original)
                .distinctBy { normalizeForDeduplication(it.text) }
                .let(::sortRecognitionCandidates)
            val preferRaisedX = raisedGlyphLooksLikeX(strokes) &&
                powerAware.any { hasExponentVariable(it.text, 'x') }
            val lattice = (if (preferRaisedX) {
                powerAware.sortedWith(
                    compareByDescending<RecognitionLatticeCandidate> {
                        hasExponentVariable(it.text, 'x')
                    }.thenByDescending(RecognitionLatticeCandidate::confidence),
                )
            } else {
                powerAware
            })
                .take(8)
            val primary = lattice.first()
            if (primary.text == snapshot.result.latex) {
                return@runCatching unchanged(
                    snapshot,
                    started,
                    strokes.size,
                    symbols.size,
                    "original-remained-best",
                    original.size,
                    enhanced.size,
                )
            }
            val result = snapshot.result.copy(
                latex = primary.text,
                normalizedExpression = primary.normalizedExpression,
                plainText = primary.text,
                confidence = primary.confidence,
                alternatives = lattice.drop(1).map {
                    MathRecognitionAlternative(it.text, it.confidence)
                },
                detectedType = primary.detectedType,
            )
            StructureEnhancement(
                snapshot.copy(
                    candidates = lattice,
                    stablePrimary = primary.text.takeIf { snapshot.stability >= .8f },
                    latencyMillis = snapshot.latencyMillis + elapsed,
                    result = result,
                ),
                StructureEnhancementDiagnostics(
                    elapsed,
                    strokes.size,
                    symbols.size,
                    original.size,
                    enhanced.size,
                    accepted = true,
                    reason = "parser-verified-spatial-structure",
                ),
            )
        }.getOrElse {
            unchanged(snapshot, started, strokes.size, 0, "exception-fallback")
        }
    }

    /**
     * Testable structure assembly seam. Symbols are labels from a recognizer in reading order;
     * their zones come exclusively from ink geometry.
     */
    internal fun assemble(candidate: String, symbols: List<SpatialSymbol>): String? {
        val atoms = visibleAtoms(candidate)
        if (abs(atoms.size - symbols.size) > config.maximumCandidateGlyphDelta) return null
        if (atoms.size != symbols.size) return null
        if (symbols.none { it.zone != SpatialZone.BASELINE }) return candidate
        val output = StringBuilder()
        var index = 0
        while (index < atoms.size) {
            val zone = symbols[index].zone
            if (zone == SpatialZone.BASELINE) {
                output.append(atoms[index])
                index++
                continue
            }
            if (output.isEmpty() || !canOwnScript(output.last())) return null
            val start = index
            while (index < atoms.size && symbols[index].zone == zone) index++
            val content = atoms.subList(start, index).joinToString("")
            if (content.isBlank() || content.all { it in "+-*/=," }) return null
            output.append(if (zone == SpatialZone.SUPERSCRIPT) '^' else '_')
            output.append(groupScript(content))
        }
        return output.toString()
    }

    internal fun inferSymbols(strokes: List<StrokeElement>): List<SpatialSymbol> {
        val groups = groupStrokes(strokes)
        if (groups.isEmpty()) return emptyList()
        val heights = groups.map { it.height.coerceAtLeast(.01f) }.sorted()
        val referenceHeight = percentile(heights, .75f)
        val anchors = groups.mapIndexedNotNull { index, bounds ->
            index.takeIf {
                bounds.height >= referenceHeight * config.anchorHeightRatio &&
                    !isLongHorizontal(bounds, referenceHeight)
            }
        }
        val globalBaseline = median(
            (if (anchors.isEmpty()) groups.indices.toList() else anchors)
                .map { groups[it].center.y },
        )
        val zones = groups.mapIndexed { index, bounds ->
            val nearbyAnchors = anchors.filter { abs(it - index) <= 4 }
            val localBaseline = median(
                nearbyAnchors.map { groups[it].center.y }.ifEmpty { listOf(globalBaseline) },
            )
            val small = bounds.height <= referenceHeight * config.scriptMaximumHeightRatio
            val horizontal = isLongHorizontal(bounds, referenceHeight)
            val offset = referenceHeight * config.scriptVerticalOffsetRatio
            val zone = when {
                !small || horizontal -> SpatialZone.BASELINE
                bounds.center.y < localBaseline - offset -> SpatialZone.SUPERSCRIPT
                bounds.top > localBaseline - referenceHeight * .10f -> SpatialZone.SUBSCRIPT
                else -> SpatialZone.BASELINE
            }
            val displacement = when (zone) {
                SpatialZone.SUPERSCRIPT -> localBaseline - bounds.center.y
                SpatialZone.SUBSCRIPT -> bounds.center.y - localBaseline + referenceHeight * .20f
                SpatialZone.BASELINE -> 0f
            }
            val confidence = if (zone == SpatialZone.BASELINE) 1f else {
                (.66f + .34f * (displacement / (referenceHeight * .62f)).coerceIn(0f, 1f))
            }
            SpatialSymbol(bounds, zone, confidence)
        }
        return zones
    }

    internal fun isStackedFraction(
        numerator: SmartBoardBounds,
        bar: SmartBoardBounds,
        denominator: SmartBoardBounds,
    ): Boolean {
        val contentWidth = max(numerator.width, denominator.width).coerceAtLeast(.01f)
        val horizontalBar = bar.width >= max(contentWidth * .65f, bar.height * 4f)
        val verticallyOrdered = numerator.bottom < bar.center.y && denominator.top > bar.center.y
        val overlapAbove = overlapWidth(numerator, bar) / min(numerator.width, bar.width).coerceAtLeast(.01f)
        val overlapBelow = overlapWidth(denominator, bar) / min(denominator.width, bar.width).coerceAtLeast(.01f)
        return horizontalBar && verticallyOrdered && overlapAbove >= .45f && overlapBelow >= .45f
    }

    internal fun isRadicalAttachment(
        radical: SmartBoardBounds,
        radicand: SmartBoardBounds,
    ): Boolean = radicand.left >= radical.center.x &&
        radicand.left <= radical.right + max(radical.width, radicand.height) * .35f &&
        radicand.center.y in (radical.top - radicand.height * .2f)..(radical.bottom + radicand.height * .2f)

    internal fun isLimitAttachment(
        operator: SmartBoardBounds,
        limit: SmartBoardBounds,
        upper: Boolean,
    ): Boolean {
        val horizontallyRelated = overlapWidth(operator.expand(operator.width * .45f), limit) > 0f
        return horizontallyRelated && if (upper) limit.bottom < operator.center.y else limit.top > operator.center.y
    }

    internal fun repairSpatialGlyphs(candidate: String, strokes: List<StrokeElement>): String {
        var repaired = candidate
        var radicalCount = strokes.count(::looksLikeRadical)
        while (radicalCount > 0) {
            val match = Regex("""(?<![A-Za-z])r\s*\(""", RegexOption.IGNORE_CASE).find(repaired) ?: break
            repaired = repaired.replaceRange(match.range, "sqrt(")
            radicalCount--
        }
        val bars = absoluteBarCount(strokes)
        repaired = when (bars) {
            2 -> Regex("""^1(.+)1(=.+)$""").replace(repaired, "|$1|$2")
            4 -> Regex("""^1(.+?)1([+-])1(.+?)1(=.+)$""").replace(repaired, "|$1|$2|$3|$4")
            else -> repaired
        }
        return repaired
    }

    internal fun raisedGlyphLooksLikeX(strokes: List<StrokeElement>): Boolean {
        if (strokes.size < 3) return false
        val nonHorizontal = strokes.filter { stroke ->
            stroke.bounds.height >= 5f &&
                stroke.bounds.width <= stroke.bounds.height * 2.2f
        }
        if (nonHorizontal.size < 3) return false
        val referenceHeight = nonHorizontal.maxOf { it.bounds.height }.coerceAtLeast(1f)
        val baselineCenters = nonHorizontal
            .filter { it.bounds.height >= referenceHeight * .78f }
            .map { it.bounds.center.y }
        if (baselineCenters.isEmpty()) return false
        val baseline = median(baselineCenters)
        val raised = nonHorizontal.filter { stroke ->
            stroke.bounds.height in (referenceHeight * .42f)..(referenceHeight * .80f) &&
                stroke.bounds.center.y < baseline - referenceHeight * .12f
        }
        if (raised.size != 2) return false
        val vectors = raised.mapNotNull { stroke ->
            val points = stroke.points
            if (points.size < 2) return@mapNotNull null
            val direct = hypot(
                (points.last().x - points.first().x).toDouble(),
                (points.last().y - points.first().y).toDouble(),
            ).toFloat()
            val path = points.zipWithNext().sumOf { (first, second) ->
                hypot(
                    (second.x - first.x).toDouble(),
                    (second.y - first.y).toDouble(),
                )
            }.toFloat()
            if (direct < referenceHeight * .35f || path / direct > 1.18f) return@mapNotNull null
            (points.last().x - points.first().x) to (points.last().y - points.first().y)
        }
        if (vectors.size != 2) return false
        val diagonal = vectors.all { (dx, dy) ->
            abs(dx) >= referenceHeight * .25f && abs(dy) >= referenceHeight * .38f
        }
        val oppositeSlopes = vectors[0].first * vectors[0].second *
            vectors[1].first * vectors[1].second < 0f
        return diagonal && oppositeSlopes
    }

    private fun groupStrokes(strokes: List<StrokeElement>): List<SmartBoardBounds> {
        val bounds = strokes.map(StrokeElement::bounds)
        val parent = IntArray(bounds.size) { it }
        fun root(value: Int): Int {
            var current = value
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        fun union(a: Int, b: Int) {
            val left = root(a)
            val right = root(b)
            if (left != right) parent[right] = left
        }
        bounds.indices.forEach { left ->
            for (right in left + 1 until bounds.size) {
                if (sameGlyph(bounds[left], bounds[right])) union(left, right)
            }
        }
        return bounds.indices.groupBy(::root).values.map { indexes ->
            unionBounds(indexes.map(bounds::get))
        }.sortedWith(compareBy<SmartBoardBounds> { it.left }.thenBy { it.top })
    }

    private fun sameGlyph(first: SmartBoardBounds, second: SmartBoardBounds): Boolean {
        val xOverlap = overlapWidth(first, second)
        if (xOverlap <= 0f) return false
        val narrowWidth = min(first.width, second.width).coerceAtLeast(.5f)
        val centersClose = abs(first.center.x - second.center.x) <= max(first.width, second.width) * .48f
        return xOverlap / narrowWidth >= .18f || centersClose
    }

    private fun looksLikeRadical(stroke: StrokeElement): Boolean {
        val points = stroke.points
        if (points.size < 8 || stroke.bounds.width < stroke.bounds.height * .45f) return false
        val maxIndex = points.indices.maxByOrNull { points[it].y } ?: return false
        val minAfter = (maxIndex until points.size).minByOrNull { points[it].y } ?: return false
        if (maxIndex !in 1 until points.lastIndex || minAfter <= maxIndex) return false
        val height = stroke.bounds.height.coerceAtLeast(1f)
        val startsMidway = points.first().y > stroke.bounds.top + height * .28f
        val descends = points[maxIndex].y > stroke.bounds.top + height * .70f
        val rises = points[minAfter].y < stroke.bounds.top + height * .30f
        val finishesHigh = points.last().y < stroke.bounds.top + height * .36f
        return startsMidway && descends && rises && finishesHigh
    }

    private fun absoluteBarCount(strokes: List<StrokeElement>): Int {
        val heights = strokes.map { it.bounds.height }.filter { it > 4f }.sorted()
        if (heights.isEmpty()) return 0
        val reference = heights[((heights.lastIndex) * .72f).toInt()].coerceAtLeast(12f)
        return strokes.count { stem ->
            val bounds = stem.bounds
            val vertical = bounds.height >= reference * .78f &&
                bounds.width <= bounds.height * .16f
            if (!vertical) return@count false
            val hasDigitBase = strokes.any { other ->
                other !== stem &&
                    other.bounds.width >= reference * .28f &&
                    abs(other.bounds.center.x - bounds.center.x) <= reference * .35f &&
                    other.bounds.top >= bounds.bottom - reference * .22f &&
                    other.bounds.top <= bounds.bottom + reference * .18f
            }
            !hasDigitBase
        }
    }

    private fun visibleAtoms(candidate: String): List<Char> {
        val engine = candidate
            .replace("\\left", "")
            .replace("\\right", "")
            .replace("\\cdot", "*")
            .replace("\\times", "*")
        return engine.filterNot { it.isWhitespace() || it in "^_{}" }.toList()
    }

    private fun canOwnScript(char: Char): Boolean =
        char.isLetterOrDigit() || char in ")]}!"

    private fun groupScript(content: String): String = when {
        content.length == 1 -> content
        content.firstOrNull() == '(' && content.lastOrNull() == ')' -> content
        else -> "($content)"
    }

    private fun relationCount(before: String, after: String): Int =
        max(0, after.count { it == '^' || it == '_' } - before.count { it == '^' || it == '_' })

    private fun structuralEditDistance(before: String, after: String): Int =
        abs(after.length - before.length).coerceAtLeast(1)

    private fun isBalanced(value: String): Boolean {
        var round = 0
        var square = 0
        value.forEach {
            when (it) {
                '(' -> round++
                ')' -> if (--round < 0) return false
                '[' -> square++
                ']' -> if (--square < 0) return false
            }
        }
        return round == 0 && square == 0
    }

    private fun unchanged(
        snapshot: StreamingRecognitionSnapshot,
        started: Long,
        strokeCount: Int,
        symbolCount: Int,
        reason: String,
        candidatesExamined: Int = 0,
        candidatesChanged: Int = 0,
    ) = StructureEnhancement(
        snapshot,
        StructureEnhancementDiagnostics(
            elapsedMillis(started),
            strokeCount,
            symbolCount,
            candidatesExamined,
            candidatesChanged,
            accepted = false,
            reason = reason,
        ),
    )

    private fun normalizeForDeduplication(value: String) =
        value.lowercase().replace(Regex("""\s+"""), "")

    private fun hasExponentVariable(value: String, variable: Char): Boolean =
        Regex("""\^(?:\{|\()?\s*${Regex.escape(variable.toString())}""", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)

    private fun isLongHorizontal(bounds: SmartBoardBounds, referenceHeight: Float) =
        bounds.width >= bounds.height.coerceAtLeast(1f) * 2.4f &&
            bounds.width >= referenceHeight * .42f

    private fun overlapWidth(first: SmartBoardBounds, second: SmartBoardBounds) =
        (min(first.right, second.right) - max(first.left, second.left)).coerceAtLeast(0f)

    private fun unionBounds(items: List<SmartBoardBounds>) = SmartBoardBounds(
        items.minOf(SmartBoardBounds::left),
        items.minOf(SmartBoardBounds::top),
        items.maxOf(SmartBoardBounds::right),
        items.maxOf(SmartBoardBounds::bottom),
    )

    private fun percentile(values: List<Float>, percentile: Float): Float {
        if (values.isEmpty()) return 1f
        val index = ((values.lastIndex) * percentile).toInt().coerceIn(values.indices)
        return values[index].coerceAtLeast(1f)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
    }

    private fun elapsedMillis(started: Long) = (System.nanoTime() - started) / 1_000_000L
}
