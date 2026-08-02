package com.indianservers.smartboard.smartboard.recognition

import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import com.indianservers.smartboard.smartboard.models.RecognitionQualityTier
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.TextElement
import java.util.Base64
import java.util.Locale
import kotlin.math.ln

enum class RecognitionContextEvidenceType {
    PARSER, BOARD_SUBJECT, RECENT_EXPRESSION, SHARED_VARIABLE, NEARBY_LABEL,
    SPECIALIST, PERSONALIZATION, QUALITY_TIER,
}

data class RecognitionRerankEvidence(
    val type: RecognitionContextEvidenceType,
    val explanation: String,
    val scoreDelta: Float,
) {
    init {
        require(explanation.isNotBlank())
        require(scoreDelta in -.5f..0.5f)
    }
}

data class RecognitionContext(
    val boardSubject: SmartBoardSubject,
    val recentExpressions: List<String>,
    val nearbyLabels: List<String>,
    val activeVariables: Set<String>,
    val conceptNames: Set<String>,
    val qualityTier: RecognitionQualityTier,
) {
    init {
        require(recentExpressions.size <= 32)
        require(nearbyLabels.size <= 24)
        require(activeVariables.size <= 64)
        require(conceptNames.size <= 64)
    }

    companion object {
        fun from(
            document: SmartBoardDocument,
            region: SmartBoardBounds,
            qualityTier: RecognitionQualityTier,
        ): RecognitionContext {
            val recent = document.elements.filterIsInstance<MathExpressionElement>()
                .filterNot(MathExpressionElement::hidden)
                .sortedByDescending(MathExpressionElement::createdAt)
                .take(32)
                .map { it.normalizedExpression ?: SmartBoardLatexAdapter.toEngineExpression(it.displayLatex) }
            val labels = document.elements.filterIsInstance<TextElement>()
                .filterNot(TextElement::hidden)
                .filter { it.bounds.intersects(region.expand(maxOf(120f, region.width * 1.5f))) }
                .take(24)
                .map(TextElement::text)
            val variables = recent.flatMap(::variables).toSet().take(64).toSet()
            val concepts = document.elementConcepts.values
                .sortedByDescending { it.confidence ?: 0f }
                .map { it.displayName.lowercase(Locale.ROOT) }
                .distinct()
                .take(64)
                .toSet()
            return RecognitionContext(document.subjectMode.selection, recent, labels, variables, concepts, qualityTier)
        }
    }
}

data class ContextualRecognitionCandidate(
    val candidate: RecognitionLatticeCandidate,
    val baseConfidence: Float,
    val finalConfidence: Float,
    val evidence: List<RecognitionRerankEvidence>,
)

data class ContextualRerankOutcome(
    val snapshot: StreamingRecognitionSnapshot,
    val rankedCandidates: List<ContextualRecognitionCandidate>,
) {
    val primaryEvidence: List<RecognitionRerankEvidence> get() = rankedCandidates.firstOrNull()?.evidence.orEmpty()
}

/**
 * Phase 7 reranker. It can only reorder provider candidates; candidate text is immutable.
 */
object SmartBoardContextualRecognitionReranker {
    fun rerank(
        snapshot: StreamingRecognitionSnapshot,
        context: RecognitionContext,
        personalization: RecognitionPersonalizationProfile = RecognitionPersonalizationProfile.Empty,
    ): ContextualRerankOutcome {
        val previousTypes = context.recentExpressions.take(8).map {
            runCatching { com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer.analyze(it).type }.getOrNull()
        }.toSet()
        val ranked = snapshot.candidates.map { candidate ->
            val normalizedVariables = variables(candidate.normalizedExpression)
            val evidence = buildList {
                if (candidate.parserVerified) add(evidence(RecognitionContextEvidenceType.PARSER, "Shared parser verified this structure.", .025f))
                if (candidate.detectedType in previousTypes) add(evidence(RecognitionContextEvidenceType.RECENT_EXPRESSION, "Structure matches recent Board work.", .035f))
                val shared = normalizedVariables.intersect(context.activeVariables)
                if (shared.isNotEmpty()) add(evidence(RecognitionContextEvidenceType.SHARED_VARIABLE, "Uses active variable${if (shared.size == 1) "" else "s"} ${shared.take(4).joinToString()}.", (.018f * shared.size).coerceAtMost(.07f)))
                val nearbyTokens = context.nearbyLabels.flatMap(::words).toSet()
                val candidateTokens = words(candidate.normalizedExpression).toSet()
                val nearbyOverlap = nearbyTokens.intersect(candidateTokens)
                if (nearbyOverlap.isNotEmpty()) add(evidence(RecognitionContextEvidenceType.NEARBY_LABEL, "Matches nearby label context.", (.012f * nearbyOverlap.size).coerceAtMost(.05f)))
                val subjectDelta = subjectCompatibility(context.boardSubject, candidate)
                if (subjectDelta != 0f) add(evidence(RecognitionContextEvidenceType.BOARD_SUBJECT, "Compatible with the active ${context.boardSubject.name.lowercase().replace('_', ' ')} Board.", subjectDelta))
                val personal = personalization.biasFor(candidate.normalizedExpression)
                if (personal > 0f) add(evidence(RecognitionContextEvidenceType.PERSONALIZATION, "Matches corrections you chose previously on this device.", personal))
                qualityEvidence(context.qualityTier, candidate)?.let(::add)
            }
            val final = (candidate.confidence + evidence.sumOf { it.scoreDelta.toDouble() }.toFloat()).coerceIn(0f, 1f)
            ContextualRecognitionCandidate(candidate, candidate.confidence, final, evidence)
        }.sortedWith(compareByDescending<ContextualRecognitionCandidate> { it.finalConfidence }.thenByDescending { it.baseConfidence })
        val lattice = ranked.map { it.candidate.copy(confidence = it.finalConfidence) }
        val primary = lattice.first()
        val rerankedResult = snapshot.result.copy(
            latex = primary.text,
            normalizedExpression = primary.normalizedExpression,
            plainText = primary.text,
            confidence = primary.confidence,
            alternatives = lattice.drop(1).map { MathRecognitionAlternative(it.text, it.confidence) },
            detectedType = primary.detectedType,
            warnings = snapshot.result.warnings + if (primary.text != snapshot.result.latex) {
                listOf("Context reordered existing candidates; review the visible evidence before insertion.")
            } else emptyList(),
        )
        return ContextualRerankOutcome(
            snapshot.copy(
                candidates = lattice,
                stablePrimary = primary.text.takeIf { snapshot.stability >= .8f },
                result = rerankedResult,
            ),
            ranked,
        )
    }

    private fun subjectCompatibility(subject: SmartBoardSubject, candidate: RecognitionLatticeCandidate): Float = when (subject) {
        SmartBoardSubject.MATHEMATICS, SmartBoardSubject.AUTO -> .015f
        SmartBoardSubject.PHYSICS -> if (Regex("""\b(?:v|u|a|t|f|m|g|lambda|omega)\b""", RegexOption.IGNORE_CASE).containsMatchIn(candidate.normalizedExpression)) .025f else 0f
        SmartBoardSubject.CHEMISTRY -> if ("=" in candidate.normalizedExpression || "->" in candidate.normalizedExpression) .015f else 0f
        else -> 0f
    }

    private fun qualityEvidence(
        tier: RecognitionQualityTier,
        candidate: RecognitionLatticeCandidate,
    ): RecognitionRerankEvidence? = when (tier) {
        RecognitionQualityTier.FAST -> null
        RecognitionQualityTier.BALANCED -> if (candidate.parserVerified) evidence(RecognitionContextEvidenceType.QUALITY_TIER, "Balanced mode gives a small verified-structure preference.", .01f) else null
        RecognitionQualityTier.ACCURATE -> if (candidate.parserVerified) evidence(RecognitionContextEvidenceType.QUALITY_TIER, "Accurate mode prefers verified structures.", .025f) else evidence(RecognitionContextEvidenceType.QUALITY_TIER, "Accurate mode lowers unverified structures.", -.02f)
    }

    private fun evidence(type: RecognitionContextEvidenceType, explanation: String, delta: Float) =
        RecognitionRerankEvidence(type, explanation, delta)
}

data class RecognitionCorrectionMemory(
    val recognized: String,
    val confirmed: String,
    val count: Int,
    val lastUsedAt: Long,
) {
    init {
        require(recognized.isNotBlank() && confirmed.isNotBlank())
        require(count in 1..10_000)
        require(lastUsedAt >= 0)
    }
}

data class RecognitionPersonalizationProfile(
    val schemaVersion: Int = 1,
    val corrections: List<RecognitionCorrectionMemory> = emptyList(),
    val totalConfirmedCorrections: Int = 0,
    val updatedAt: Long = 0,
) {
    init {
        require(schemaVersion == 1)
        require(corrections.size <= 256)
        require(totalConfirmedCorrections >= 0)
    }

    fun biasFor(candidate: String): Float {
        val canonical = canonical(candidate)
        val count = corrections.filter { canonical(it.confirmed) == canonical }.sumOf(RecognitionCorrectionMemory::count)
        return if (count == 0) 0f else (.025f * ln(1.0 + count)).toFloat().coerceAtMost(.12f)
    }

    companion object { val Empty = RecognitionPersonalizationProfile() }
}

/**
 * Phase 8 learns only confirmed text-to-text corrections. Stroke geometry, images, Board text,
 * document ids, and user identifiers are never stored in this profile.
 */
object SmartBoardRecognitionPersonalizer {
    fun recordCorrection(
        profile: RecognitionPersonalizationProfile,
        recognized: String,
        confirmed: String,
        now: Long,
    ): RecognitionPersonalizationProfile {
        val from = canonical(recognized)
        val to = canonical(confirmed)
        if (from.isBlank() || to.isBlank() || from == to) return profile
        val existing = profile.corrections.firstOrNull { canonical(it.recognized) == from && canonical(it.confirmed) == to }
        val replacement = RecognitionCorrectionMemory(
            recognized = recognized.take(512),
            confirmed = confirmed.take(512),
            count = ((existing?.count ?: 0) + 1).coerceAtMost(10_000),
            lastUsedAt = now,
        )
        val updated = (profile.corrections.filterNot { it === existing } + replacement)
            .sortedWith(compareByDescending<RecognitionCorrectionMemory> { it.lastUsedAt }.thenByDescending { it.count })
            .take(256)
        return profile.copy(
            corrections = updated,
            totalConfirmedCorrections = if (profile.totalConfirmedCorrections == Int.MAX_VALUE) Int.MAX_VALUE else profile.totalConfirmedCorrections + 1,
            updatedAt = now,
        )
    }
}

object RecognitionPersonalizationProfileCodec {
    fun encode(profile: RecognitionPersonalizationProfile): String = buildString {
        appendLine("RPP|${profile.schemaVersion}|${profile.totalConfirmedCorrections}|${profile.updatedAt}")
        profile.corrections.forEach {
            appendLine(listOf(pack(it.recognized), pack(it.confirmed), it.count, it.lastUsedAt).joinToString("|"))
        }
    }

    fun decode(source: String): RecognitionPersonalizationProfile {
        if (source.isBlank()) return RecognitionPersonalizationProfile.Empty
        val lines = source.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.first().split('|')
        require(header.size >= 4 && header[0] == "RPP" && header[1] == "1")
        val corrections = lines.drop(1).take(256).mapNotNull { line ->
            runCatching {
                val fields = line.split('|')
                RecognitionCorrectionMemory(unpack(fields[0]), unpack(fields[1]), fields[2].toInt(), fields[3].toLong())
            }.getOrNull()
        }
        return RecognitionPersonalizationProfile(
            corrections = corrections,
            totalConfirmedCorrections = header[2].toIntOrNull()?.coerceAtLeast(0) ?: corrections.sumOf(RecognitionCorrectionMemory::count),
            updatedAt = header[3].toLongOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }

    private fun pack(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
    private fun unpack(value: String) = String(Base64.getUrlDecoder().decode(value))
}

enum class RecognitionDiagnosticInput { DIGITAL_INK, IMAGE, FUSED }
enum class RecognitionLatencyBucket { UNKNOWN, UNDER_150_MS, FROM_150_TO_500_MS, OVER_500_MS }
enum class RecognitionConfidenceBucket { LOW, MEDIUM, HIGH }

data class RecognitionDiagnosticEvent(
    val input: RecognitionDiagnosticInput,
    val latency: RecognitionLatencyBucket,
    val confidence: RecognitionConfidenceBucket,
    val candidateCount: Int,
    val selectedRank: Int?,
    val corrected: Boolean?,
    val occurredAt: Long,
) {
    init {
        require(candidateCount in 0..16)
        require(selectedRank == null || selectedRank in 1..16)
        require(occurredAt >= 0)
    }
}

class BoundedRecognitionDiagnostics(private val maximumEvents: Int = 500) {
    private val events = ArrayDeque<RecognitionDiagnosticEvent>()
    init { require(maximumEvents in 10..10_000) }

    @Synchronized
    fun record(event: RecognitionDiagnosticEvent) {
        events += event
        while (events.size > maximumEvents) events.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<RecognitionDiagnosticEvent> = events.toList()

    @Synchronized
    fun clear() = events.clear()

    fun health(): RecognitionRuntimeHealth {
        val sample = snapshot().takeLast(100)
        if (sample.isEmpty()) return RecognitionRuntimeHealth(0, 0.0, 0.0, false)
        val slowRate = sample.count { it.latency == RecognitionLatencyBucket.OVER_500_MS }.toDouble() / sample.size
        val correctionRate = sample.count { it.corrected == true }.toDouble() / sample.count { it.corrected != null }.coerceAtLeast(1)
        return RecognitionRuntimeHealth(sample.size, slowRate, correctionRate, slowRate > .20 || correctionRate > .35)
    }
}

data class RecognitionRuntimeHealth(
    val sampleCount: Int,
    val slowRate: Double,
    val correctionRate: Double,
    val rollbackRecommended: Boolean,
)

data class RecognitionModelManifest(
    val modelId: String,
    val version: String,
    val sha256: String,
    val minimumSchemaVersion: Int,
    val supportedInputs: Set<RecognitionDiagnosticInput>,
    val qualityTier: RecognitionQualityTier,
    val verified: Boolean,
) {
    init {
        require(modelId.isNotBlank() && version.isNotBlank())
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(minimumSchemaVersion > 0)
        require(supportedInputs.isNotEmpty())
    }
}

data class RecognitionQualityGateReport(
    val passed: Boolean,
    val blockers: List<String>,
    val warnings: List<String>,
)

object SmartBoardRecognitionQualityGate {
    fun evaluate(
        metrics: RecognitionBenchmarkMetrics,
        targets: RecognitionBenchmarkTargets,
        baseline: RecognitionBenchmarkMetrics? = null,
    ): RecognitionQualityGateReport {
        val blockers = buildList {
            if (metrics.topThreeRecall < targets.topThreeRecall) add("Top-three recall ${percent(metrics.topThreeRecall)} is below ${percent(targets.topThreeRecall)}.")
            if (metrics.confidenceBrierScore > targets.maximumBrierScore) add("Confidence calibration exceeds the Brier-score limit.")
            if (metrics.medianLatencyMillis > targets.maximumMedianLatencyMillis) add("Median latency ${metrics.medianLatencyMillis} ms exceeds ${targets.maximumMedianLatencyMillis} ms.")
            if (metrics.p95LatencyMillis > targets.maximumP95LatencyMillis) add("P95 latency ${metrics.p95LatencyMillis} ms exceeds ${targets.maximumP95LatencyMillis} ms.")
            if (metrics.medianCorrectionActions > targets.maximumMedianCorrectionActions) add("Median correction actions exceed the release target.")
            if (baseline != null && metrics.semanticAccuracy + .002 < baseline.semanticAccuracy) add("Semantic accuracy regressed against the active baseline.")
        }
        val warnings = buildList {
            if (metrics.caseCount < 1_000) add("Corpus has fewer than 1,000 cases; production activation requires broader representation.")
            if (baseline != null && metrics.p95LatencyMillis > baseline.p95LatencyMillis) add("Tail latency increased relative to baseline.")
        }
        return RecognitionQualityGateReport(blockers.isEmpty(), blockers, warnings)
    }

    private fun percent(value: Double) = "${(value * 1000).toInt() / 10.0}%"
}

/**
 * Phase 9 activation boundary. Unverified or benchmark-regressing manifests never become active.
 */
class SmartBoardRecognitionReleaseController(initial: RecognitionModelManifest) {
    var activeManifest: RecognitionModelManifest = initial
        private set
    var previousManifest: RecognitionModelManifest? = null
        private set

    fun activate(
        candidate: RecognitionModelManifest,
        gate: RecognitionQualityGateReport,
        supportedSchemaVersion: Int,
    ): Result<RecognitionModelManifest> = runCatching {
        require(candidate.verified) { "Model package is not verified." }
        require(candidate.minimumSchemaVersion <= supportedSchemaVersion) { "Model requires a newer recognition schema." }
        require(gate.passed) { gate.blockers.joinToString(" ") }
        previousManifest = activeManifest
        activeManifest = candidate
        candidate
    }

    fun rollback(reason: String): Result<RecognitionModelManifest> = runCatching {
        require(reason.isNotBlank())
        val previous = requireNotNull(previousManifest) { "No previous verified model is available." }
        val replaced = activeManifest
        activeManifest = previous
        previousManifest = replaced
        activeManifest
    }
}

private fun canonical(value: String) = runCatching { SmartBoardLatexAdapter.toEngineExpression(value) }
    .getOrDefault(value).replace(Regex("""\s+"""), "").lowercase(Locale.ROOT)

private fun variables(value: String): List<String> = Regex("""\b[a-zA-Z][a-zA-Z0-9_]*\b""")
    .findAll(value)
    .map { it.value.lowercase(Locale.ROOT) }
    .filterNot { it in knownFunctions }
    .take(64)
    .toList()

private fun words(value: String): List<String> = Regex("""[a-zA-Z][a-zA-Z0-9_]*|\d+(?:\.\d+)?""")
    .findAll(value.lowercase(Locale.ROOT))
    .map { it.value }
    .take(64)
    .toList()

private val knownFunctions = setOf("sin", "cos", "tan", "log", "ln", "exp", "sqrt", "pi", "infinity")
