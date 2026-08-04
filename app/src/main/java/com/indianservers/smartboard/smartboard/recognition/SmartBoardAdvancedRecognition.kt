package com.indianservers.smartboard.smartboard.recognition

import com.indianservers.smartboard.input.CasPhotoMathRecognizer
import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer
import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.StrokeElement
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val SMART_BOARD_RECOGNITION_BENCHMARK_SCHEMA = 1

enum class RecognitionBenchmarkInputKind { DIGITAL_INK, RASTER_IMAGE, FUSED }

data class RecognitionBenchmarkCase(
    val id: String,
    val subject: SmartBoardSubject,
    val inputKind: RecognitionBenchmarkInputKind,
    val strokes: List<StrokeElement>,
    val rasterPng: ByteArray,
    val expectedLatex: String,
    val expectedSemanticExpression: String,
    val tags: Set<String> = emptySet(),
    val expectedCorrectionActions: Int = 0,
) {
    init {
        require(id.isNotBlank() && expectedLatex.isNotBlank() && expectedSemanticExpression.isNotBlank())
        require(strokes.isNotEmpty() || rasterPng.isNotEmpty())
        require(expectedCorrectionActions >= 0)
    }
}

data class RecognitionBenchmarkCorpus(
    val schemaVersion: Int = SMART_BOARD_RECOGNITION_BENCHMARK_SCHEMA,
    val name: String,
    val version: String,
    val cases: List<RecognitionBenchmarkCase>,
) {
    init {
        require(schemaVersion == SMART_BOARD_RECOGNITION_BENCHMARK_SCHEMA)
        require(name.isNotBlank() && version.isNotBlank() && cases.isNotEmpty())
        require(cases.map(RecognitionBenchmarkCase::id).distinct().size == cases.size)
    }
}

data class RecognitionBenchmarkPrediction(
    val caseId: String,
    val primary: String,
    val alternatives: List<String>,
    val confidence: Float,
    val latencyMillis: Long,
    val correctionActions: Int,
)

data class RecognitionBenchmarkMetrics(
    val caseCount: Int,
    val exactLatexAccuracy: Double,
    val semanticAccuracy: Double,
    val meanSymbolAccuracy: Double,
    val topThreeRecall: Double,
    val confidenceBrierScore: Double,
    val medianLatencyMillis: Long,
    val p95LatencyMillis: Long,
    val medianCorrectionActions: Int,
)

data class RecognitionBenchmarkTargets(
    val commonMathSemanticAccuracy: Double = .98,
    val advancedMathSemanticAccuracy: Double = .95,
    val topThreeRecall: Double = .99,
    val maximumBrierScore: Double = .03,
    val maximumMedianLatencyMillis: Long = 150,
    val maximumP95LatencyMillis: Long = 500,
    val maximumMedianCorrectionActions: Int = 1,
)

class RecognitionBenchmarkRecorder(
    private val corpusName: String,
    private val corpusVersion: String,
    private val maximumCases: Int = 10_000,
) {
    private val cases = linkedMapOf<String, RecognitionBenchmarkCase>()

    init {
        require(corpusName.isNotBlank() && corpusVersion.isNotBlank())
        require(maximumCases in 1..100_000)
    }

    @Synchronized
    fun record(case: RecognitionBenchmarkCase) {
        require(cases.size < maximumCases || case.id in cases) { "Benchmark recorder is full." }
        cases[case.id] = case
    }

    @Synchronized
    fun snapshot(): RecognitionBenchmarkCorpus {
        require(cases.isNotEmpty()) { "Record at least one consented benchmark case." }
        return RecognitionBenchmarkCorpus(
            name = corpusName,
            version = corpusVersion,
            cases = cases.values.toList(),
        )
    }

    @Synchronized
    fun size() = cases.size
}

object SmartBoardRecognitionBenchmark {
    suspend fun run(
        corpus: RecognitionBenchmarkCorpus,
        recognize: suspend (RecognitionBenchmarkCase) -> RecognitionBenchmarkPrediction,
    ): Pair<List<RecognitionBenchmarkPrediction>, RecognitionBenchmarkMetrics> {
        val predictions = corpus.cases.map { case -> recognize(case) }
        return predictions to metrics(corpus, predictions)
    }

    fun metrics(
        corpus: RecognitionBenchmarkCorpus,
        predictions: List<RecognitionBenchmarkPrediction>,
    ): RecognitionBenchmarkMetrics {
        require(predictions.map(RecognitionBenchmarkPrediction::caseId).toSet() == corpus.cases.map(RecognitionBenchmarkCase::id).toSet())
        val byId = predictions.associateBy(RecognitionBenchmarkPrediction::caseId)
        val evaluations = corpus.cases.map { case ->
            val prediction = requireNotNull(byId[case.id])
            val expectedLatex = canonical(case.expectedLatex)
            val primary = canonical(prediction.primary)
            val expectedSemantic = semantic(case.expectedSemanticExpression)
            val predictedSemantic = semantic(prediction.primary)
            val correct = predictedSemantic == expectedSemantic
            Evaluation(
                exact = primary == expectedLatex,
                semantic = correct,
                symbolAccuracy = 1.0 - levenshtein(primary, expectedLatex).toDouble() / maxOf(primary.length, expectedLatex.length, 1),
                topThree = (listOf(prediction.primary) + prediction.alternatives.take(2)).any { semantic(it) == expectedSemantic },
                brier = (prediction.confidence.toDouble() - if (correct) 1.0 else 0.0).pow(2),
                latency = prediction.latencyMillis,
                corrections = prediction.correctionActions,
            )
        }
        fun mean(selector: (Evaluation) -> Double) = evaluations.map(selector).average()
        return RecognitionBenchmarkMetrics(
            caseCount = evaluations.size,
            exactLatexAccuracy = evaluations.count(Evaluation::exact).toDouble() / evaluations.size,
            semanticAccuracy = evaluations.count(Evaluation::semantic).toDouble() / evaluations.size,
            meanSymbolAccuracy = mean(Evaluation::symbolAccuracy),
            topThreeRecall = evaluations.count(Evaluation::topThree).toDouble() / evaluations.size,
            confidenceBrierScore = mean(Evaluation::brier),
            medianLatencyMillis = percentile(evaluations.map(Evaluation::latency), .5),
            p95LatencyMillis = percentile(evaluations.map(Evaluation::latency), .95),
            medianCorrectionActions = percentile(evaluations.map { it.corrections.toLong() }, .5).toInt(),
        )
    }

    private data class Evaluation(
        val exact: Boolean,
        val semantic: Boolean,
        val symbolAccuracy: Double,
        val topThree: Boolean,
        val brier: Double,
        val latency: Long,
        val corrections: Int,
    )

    private fun canonical(value: String) = value.replace(Regex("""\s+"""), "").trim()
    private fun semantic(value: String) = runCatching { SmartBoardLatexAdapter.toEngineExpression(value) }
        .getOrDefault(value).replace(Regex("""\s+"""), "").removeSurrounding("(").removeSurrounding(")")

    private fun percentile(values: List<Long>, percentile: Double): Long {
        val sorted = values.sorted()
        val index = ((sorted.lastIndex * percentile).toInt()).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            previous = current
        }
        return previous.last()
    }
}

enum class RecognitionCandidateSource { DIGITAL_INK, RASTER_IMAGE, PARSER, PREVIOUS_STABLE }

data class RecognitionLatticeCandidate(
    val text: String,
    val normalizedExpression: String,
    val confidence: Float,
    val sources: Set<RecognitionCandidateSource>,
    val parserVerified: Boolean,
    val detectedType: MathExpressionType,
)

data class StreamingRecognitionSnapshot(
    val requestFingerprint: String,
    val candidates: List<RecognitionLatticeCandidate>,
    val stablePrimary: String?,
    val stability: Float,
    val latencyMillis: Long,
    val result: MathRecognitionResult,
)

interface MathImageRecognitionProvider {
    val id: String
    suspend fun recognize(png: ByteArray, maximumAlternatives: Int = 6): MathRecognitionResult
}

class MlKitImageMathRecognitionAdapter : MathImageRecognitionProvider {
    override val id = "existing-mlkit-image-text"

    override suspend fun recognize(png: ByteArray, maximumAlternatives: Int): MathRecognitionResult =
        suspendCancellableCoroutine { continuation ->
            CasPhotoMathRecognizer.recognize(
                png,
                onSuccess = { recognized ->
                    if (!continuation.isActive) return@recognize
                    val candidates = recognized.candidates.take(maximumAlternatives)
                    val primary = candidates.first()
                    continuation.resume(
                        MathRecognitionResult(
                            latex = primary,
                            normalizedExpression = primary,
                            plainText = primary,
                            confidence = recognized.confidence.toFloat(),
                            alternatives = candidates.drop(1).mapIndexed { index, value ->
                                MathRecognitionAlternative(value, (recognized.confidence - .06 * (index + 1)).toFloat().coerceIn(0f, 1f))
                            },
                            detectedType = MathRecognitionClassifier.detect(primary),
                            warnings = listOf(recognized.message),
                        ),
                    )
                },
                onFailure = { message ->
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message.take(240)))
                },
            )
        }
}

class MultimodalMathRecognitionEngine(
    private val digitalInk: MathHandwritingRecognitionProvider,
    private val image: MathImageRecognitionProvider,
) {
    private val structureEnhancer = StructureAwareRecognitionEnhancer()
    @Volatile private var digitalRetryAfterMillis = 0L

    suspend fun recognize(
        input: MathRecognitionInput,
        options: MathRecognitionOptions = MathRecognitionOptions(),
        previousPrimary: String? = null,
    ): StreamingRecognitionSnapshot {
        val lines = splitVisualLines(input.strokes)
        if (lines.size > 1) {
            val started = System.currentTimeMillis()
            val snapshots = lines.mapIndexed { index, strokes ->
                val bounds = SmartBoardBounds.from(
                    strokes.flatMap { stroke -> stroke.points.map { it.position } },
                ).expand(12f)
                recognizeSingle(
                    MathRecognitionInput(
                        strokes = strokes,
                        bounds = bounds,
                        rasterPng = MathRecognitionInputRenderer.render(strokes, bounds),
                        requestFingerprint = "${input.requestFingerprint}-line-$index",
                    ),
                    options,
                    previousPrimary = null,
                )
            }
            return combineVisualLines(
                input.requestFingerprint,
                snapshots,
                System.currentTimeMillis() - started,
            )
        }
        return recognizeSingle(input, options, previousPrimary)
    }

    private suspend fun recognizeSingle(
        input: MathRecognitionInput,
        options: MathRecognitionOptions,
        previousPrimary: String?,
    ): StreamingRecognitionSnapshot = supervisorScope {
        val started = System.currentTimeMillis()
        val digital = async {
            providerResult(
                retryAfter = digitalRetryAfterMillis,
                timeoutMillis = DIGITAL_PROVIDER_TIMEOUT_MILLIS,
                onUnavailable = { digitalRetryAfterMillis = it },
            ) { digitalInk.recognize(input, options) }
        }
        val raster = async {
            if (input.rasterPng.isEmpty()) null else providerResult(
                retryAfter = 0L,
                timeoutMillis = RASTER_PROVIDER_TIMEOUT_MILLIS,
                onUnavailable = {},
            ) { image.recognize(input.rasterPng, options.maximumAlternatives) }
        }
        val digitalResult = digital.await()
        val rasterResult = raster.await()
        val fused = if (digitalResult == null && rasterResult == null) {
            unavailableSnapshot(input.requestFingerprint, System.currentTimeMillis() - started)
        } else {
            fuse(input.requestFingerprint, digitalResult, rasterResult, previousPrimary, System.currentTimeMillis() - started)
        }
        structureEnhancer.enhance(
            fused,
            input.strokes,
        ).snapshot
    }

    private fun combineVisualLines(
        fingerprint: String,
        lines: List<StreamingRecognitionSnapshot>,
        latencyMillis: Long,
    ): StreamingRecognitionSnapshot {
        val latex = lines.joinToString(";") { it.result.latex }
        val normalized = lines.joinToString(";") {
            it.result.normalizedExpression ?: it.result.latex
        }
        val confidence = lines.mapNotNull { it.result.confidence }.minOrNull() ?: 0f
        val sources = lines.flatMap { it.candidates.firstOrNull()?.sources.orEmpty() }.toSet()
        val candidate = RecognitionLatticeCandidate(
            text = latex,
            normalizedExpression = normalized,
            confidence = confidence,
            sources = sources,
            parserVerified = lines.all { it.candidates.firstOrNull()?.parserVerified == true },
            detectedType = MathExpressionType.SYSTEM,
        )
        val result = MathRecognitionResult(
            latex = latex,
            normalizedExpression = normalized,
            plainText = latex,
            confidence = confidence,
            alternatives = emptyList(),
            detectedType = MathExpressionType.SYSTEM,
            warnings = lines.flatMap { it.result.warnings }.distinct() +
                "Recognized as ${lines.size} spatially separate equation lines.",
        )
        return StreamingRecognitionSnapshot(
            requestFingerprint = fingerprint,
            candidates = listOf(candidate),
            stablePrimary = null,
            stability = lines.minOfOrNull(StreamingRecognitionSnapshot::stability) ?: 0f,
            latencyMillis = latencyMillis,
            result = result,
        )
    }

    private fun splitVisualLines(strokes: List<StrokeElement>): List<List<StrokeElement>> {
        if (strokes.size < 4) return listOf(strokes)
        val heights = strokes.map { it.bounds.height.coerceAtLeast(1f) }.sorted()
        val referenceHeight = heights[((heights.lastIndex) * .72f).toInt()].coerceAtLeast(12f)
        val anchors = strokes.filter { stroke ->
            stroke.bounds.height >= referenceHeight * .72f &&
                stroke.bounds.width < stroke.bounds.height * 3.2f
        }
        if (anchors.size < 2) return listOf(strokes)
        val threshold = (referenceHeight * 1.18f).coerceIn(44f, 68f)
        val centers = mutableListOf<Float>()
        anchors.map { it.bounds.center.y }.sorted().forEach { center ->
            val nearest = centers.indices.minByOrNull { index -> abs(centers[index] - center) }
            if (nearest == null || abs(centers[nearest] - center) > threshold) {
                centers += center
            } else {
                centers[nearest] = (centers[nearest] + center) / 2f
            }
        }
        if (centers.size !in 2..6) return listOf(strokes)
        return strokes.groupBy { stroke ->
            centers.indices.minBy { index -> abs(centers[index] - stroke.bounds.center.y) }
        }.toSortedMap().values.map { line -> line.sortedBy(StrokeElement::createdAt) }
    }

    suspend fun enhanceWithRaster(
        input: MathRecognitionInput,
        digitalResult: MathRecognitionResult,
        previousPrimary: String? = null,
    ): StreamingRecognitionSnapshot {
        val started = System.currentTimeMillis()
        val raster = if (input.rasterPng.isEmpty()) null else providerResult(
            retryAfter = 0L,
            timeoutMillis = RASTER_PROVIDER_TIMEOUT_MILLIS,
            onUnavailable = {},
        ) { image.recognize(input.rasterPng) }
        return structureEnhancer.enhance(
            fuse(
                input.requestFingerprint,
                digitalResult,
                raster,
                previousPrimary,
                System.currentTimeMillis() - started,
            ),
            input.strokes,
        ).snapshot
    }

    fun fuse(
        fingerprint: String,
        digital: MathRecognitionResult?,
        raster: MathRecognitionResult?,
        previousPrimary: String? = null,
        latencyMillis: Long = 0,
    ): StreamingRecognitionSnapshot {
        require(digital != null || raster != null) { "No recognition provider returned a candidate." }
        data class Accumulator(var score: Float = 0f, val sources: MutableSet<RecognitionCandidateSource> = linkedSetOf(), var display: String = "")
        val candidates = linkedMapOf<String, Accumulator>()
        fun add(result: MathRecognitionResult?, source: RecognitionCandidateSource, weight: Float) {
            if (result == null) return
            val baseConfidence = result.confidence ?: .5f
            (listOf(result.latex to baseConfidence) + result.alternatives.map { it.latex to (it.confidence ?: baseConfidence * .75f) })
                .forEachIndexed { index, (text, confidence) ->
                    val display = normalizeTexTellerLatex(text)
                    val normalized = normalize(display)
                    if (normalized.isBlank()) return@forEachIndexed
                    val accumulator = candidates.getOrPut(normalized) { Accumulator(display = display) }
                    accumulator.score += weight * confidence * (1f - index * .06f).coerceAtLeast(.65f)
                    accumulator.sources += source
                }
        }
        val dedicatedFormulaVision = raster?.warnings.orEmpty().any { "TexTeller" in it }
        // A ready TexTeller pass is trained specifically for two-dimensional mathematical
        // notation. Generic handwriting remains useful as an alternative, but must not outrank a
        // dedicated formula result merely because its confidence is calibrated differently.
        add(digital, RecognitionCandidateSource.DIGITAL_INK, if (dedicatedFormulaVision) .16f else .58f)
        add(raster, RecognitionCandidateSource.RASTER_IMAGE, if (dedicatedFormulaVision) .78f else .30f)
        candidates.forEach { (normalized, candidate) ->
            val analysis = SmartBoardExpressionAnalyzer.analyze(normalized)
            if (analysis.parserVerified) {
                candidate.score += .12f
                candidate.sources += RecognitionCandidateSource.PARSER
            }
            candidate.score += powerStructureScoreAdjustment(normalized, analysis.parserVerified)
            if (previousPrimary != null && normalize(previousPrimary) == normalized) {
                candidate.score += .08f
                candidate.sources += RecognitionCandidateSource.PREVIOUS_STABLE
            }
        }
        val scoredCandidates = candidates.map { (normalized, candidate) ->
            val analysis = SmartBoardExpressionAnalyzer.analyze(normalized)
            RecognitionLatticeCandidate(
                candidate.display,
                normalized,
                candidate.score.coerceIn(0f, 1f),
                candidate.sources,
                analysis.parserVerified,
                analysis.type,
            )
        }
        val lattice = sortRecognitionCandidates(scoredCandidates).take(8)
        require(lattice.isNotEmpty()) { "Recognition returned no usable candidates." }
        val primary = lattice.first()
        val stability = when {
            previousPrimary == null -> .45f
            normalize(previousPrimary) == primary.normalizedExpression -> .92f
            else -> .35f
        }
        val result = MathRecognitionResult(
            latex = primary.text,
            normalizedExpression = primary.normalizedExpression,
            plainText = primary.text,
            confidence = primary.confidence,
            alternatives = lattice.drop(1).map { MathRecognitionAlternative(it.text, it.confidence) },
            detectedType = primary.detectedType,
            warnings = buildList {
                if (raster == null) add("Image pass unavailable; result uses digital ink and parser evidence.")
                if (digital == null) add("Digital-ink pass unavailable; result uses image and parser evidence.")
                if (primary.confidence < .65f) add("Low-confidence interpretation; choose an alternative or edit before insertion.")
            },
        )
        return StreamingRecognitionSnapshot(
            fingerprint,
            lattice,
            primary.text.takeIf { stability >= .8f },
            stability,
            latencyMillis.coerceAtLeast(0),
            result,
        )
    }

    private fun normalize(value: String) = runCatching { SmartBoardLatexAdapter.toEngineExpression(value) }
        .getOrDefault(value).replace(Regex("""\s+"""), "").trim()

    private fun unavailableSnapshot(
        fingerprint: String,
        latencyMillis: Long,
    ): StreamingRecognitionSnapshot {
        val candidate = RecognitionLatticeCandidate(
            text = "?",
            normalizedExpression = "?",
            confidence = 0f,
            sources = emptySet(),
            parserVerified = false,
            detectedType = MathExpressionType.UNKNOWN,
        )
        val result = MathRecognitionResult(
            latex = "?",
            normalizedExpression = "?",
            plainText = "?",
            confidence = 0f,
            alternatives = emptyList(),
            detectedType = MathExpressionType.UNKNOWN,
            warnings = listOf("Recognition providers are temporarily unavailable; the original ink is unchanged."),
        )
        return StreamingRecognitionSnapshot(
            requestFingerprint = fingerprint,
            candidates = listOf(candidate),
            stablePrimary = null,
            stability = 0f,
            latencyMillis = latencyMillis.coerceAtLeast(0),
            result = result,
        )
    }

    private suspend fun <T> providerResult(
        retryAfter: Long,
        timeoutMillis: Long,
        onUnavailable: (Long) -> Unit,
        block: suspend () -> T,
    ): T? {
        val now = System.currentTimeMillis()
        if (now < retryAfter) return null
        val result = withTimeoutOrNull(timeoutMillis) { runCatching { block() }.getOrNull() }
        if (result == null) onUnavailable(now + PROVIDER_RETRY_COOLDOWN_MILLIS)
        return result
    }

    private companion object {
        const val DIGITAL_PROVIDER_TIMEOUT_MILLIS = 60_000L
        const val RASTER_PROVIDER_TIMEOUT_MILLIS = 30_000L
        const val PROVIDER_RETRY_COOLDOWN_MILLIS = 60_000L
    }
}

/**
 * Candidate-level evidence for handwritten powers. A dangling quote, prime, or empty group after
 * a caret is a common OCR artifact and is not a complete exponent. Conversely, a parser-verified
 * explicit exponent receives a small preference. The adjustment only changes ranking among
 * recognizer hypotheses; it never invents the missing exponent.
 */
internal fun powerStructureScoreAdjustment(expression: String, parserVerified: Boolean): Float {
    val compact = expression.replace(Regex("""\s+"""), "")
    if (hasMalformedPowerStructure(compact)) return -.55f
    val explicitPower = hasCompletePowerStructure(compact)
    return if (parserVerified && explicitPower) .035f else 0f
}

internal fun hasMalformedPowerStructure(expression: String): Boolean {
    val compact = expression.replace(Regex("""\s+"""), "")
    return Regex("""\^(?:\{\s*)?(?:["'`]|\\prime)(?:\s*\})?(?=[=+\-*/),;]|$)""")
        .containsMatchIn(compact) ||
        Regex("""\^(?:\{\}|\(\))""").containsMatchIn(compact) ||
        "^^" in compact
}

internal fun hasCompletePowerStructure(expression: String): Boolean =
    Regex("""\^(?:\{|\()?[+\-]?[A-Za-z0-9]""")
        .containsMatchIn(expression.replace(Regex("""\s+"""), ""))

internal fun sortRecognitionCandidates(
    candidates: List<RecognitionLatticeCandidate>,
): List<RecognitionLatticeCandidate> {
    val hasCompletePowerCandidate = candidates.any {
        hasCompletePowerStructure(it.text) && !hasMalformedPowerStructure(it.text)
    }
    return candidates.sortedWith(
        compareByDescending<RecognitionLatticeCandidate> {
            !hasCompletePowerCandidate || !hasMalformedPowerStructure(it.text)
        }.thenByDescending(RecognitionLatticeCandidate::confidence),
    )
}

class StreamingMathRecognitionEngine(
    private val multimodal: MultimodalMathRecognitionEngine,
) {
    suspend fun update(input: MathRecognitionInput, previous: StreamingRecognitionSnapshot?): StreamingRecognitionSnapshot =
        multimodal.recognize(input, previousPrimary = previous?.result?.latex)
}

enum class CorrectionGestureType { SCRIBBLE_ERASE, STRIKETHROUGH_ERASE }

data class CorrectionGestureSuggestion(
    val gestureStrokeId: String,
    val targetStrokeIds: Set<String>,
    val type: CorrectionGestureType,
    val confidence: Float,
)

object SmartBoardCorrectionGestureDetector {
    fun detect(gesture: StrokeElement, existing: List<StrokeElement>): CorrectionGestureSuggestion? {
        val duration = (gesture.points.last().timestampMillis - gesture.points.first().timestampMillis).coerceAtLeast(0)
        if (duration > 1_800 || gesture.points.size < 4) return null
        val targetStrokes = existing.filterNot(StrokeElement::hidden)
            .filter { it.id != gesture.id && it.bounds.intersects(gesture.bounds.expand(8f)) }
        if (targetStrokes.isEmpty()) return null
        val targets = targetStrokes.mapTo(linkedSetOf(), StrokeElement::id)
        val width = gesture.bounds.width.coerceAtLeast(1f)
        val height = gesture.bounds.height.coerceAtLeast(1f)
        val changes = directionChanges(gesture)
        val diagonal = hypot(width, height).coerceAtLeast(1f)
        val travelDensity = gesture.points.zipWithNext().sumOf { (a, b) ->
            hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        }.toFloat() / diagonal
        val targetCrossings = targetStrokes.sumOf { target -> crossingCount(gesture, target) }
        val type = when {
            gesture.points.size >= 12 &&
                changes >= 7 &&
                travelDensity >= 5f &&
                targetCrossings >= 3 &&
                width / height in .45f..4.5f ->
                CorrectionGestureType.SCRIBBLE_ERASE
            width / height >= 4f && changes <= 2 -> CorrectionGestureType.STRIKETHROUGH_ERASE
            else -> return null
        }
        val confidence = if (type == CorrectionGestureType.SCRIBBLE_ERASE) {
            .70f + (travelDensity - 5f).coerceIn(0f, 3f) * .04f + targetCrossings.coerceAtMost(6) * .025f
        } else {
            .82f
        }
        return CorrectionGestureSuggestion(gesture.id, targets, type, confidence.coerceIn(0f, .98f))
    }

    private fun crossingCount(first: StrokeElement, second: StrokeElement): Int =
        first.points.zipWithNext().sumOf { (a, b) ->
            second.points.zipWithNext().count { (c, d) ->
                segmentsCross(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y)
            }
        }

    private fun segmentsCross(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        dx: Float,
        dy: Float,
    ): Boolean {
        fun side(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) =
            (qx - px) * (ry - py) - (qy - py) * (rx - px)
        val firstA = side(ax, ay, bx, by, cx, cy)
        val firstB = side(ax, ay, bx, by, dx, dy)
        val secondA = side(cx, cy, dx, dy, ax, ay)
        val secondB = side(cx, cy, dx, dy, bx, by)
        return firstA * firstB < 0f && secondA * secondB < 0f
    }

    private fun directionChanges(stroke: StrokeElement): Int {
        val directions = stroke.points.zipWithNext().map { (a, b) ->
            Pair((b.x - a.x).sign(), (b.y - a.y).sign())
        }.filter { it.first != 0 || it.second != 0 }
        return directions.zipWithNext().count { (a, b) -> a.first != b.first || a.second != b.second }
    }

    private fun Float.sign() = when {
        this > 1f -> 1
        this < -1f -> -1
        else -> 0
    }
}
