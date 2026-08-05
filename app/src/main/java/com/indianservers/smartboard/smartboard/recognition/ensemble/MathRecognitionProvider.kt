package com.indianservers.smartboard.smartboard.recognition.ensemble

import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.StrokeElement

/**
 * Model-neutral input. Raster and ink are optional independently so image-only,
 * stroke-only and hybrid providers can share one lifecycle contract.
 */
data class RecognitionInput(
    val requestId: String,
    val requestFingerprint: String,
    val rasterPng: ByteArray = byteArrayOf(),
    val strokes: List<StrokeElement> = emptyList(),
    val strokeBounds: Map<String, SmartBoardBounds> = emptyMap(),
    val canvasWidth: Float,
    val canvasHeight: Float,
    val detectedTextRegions: List<DetectedRecognitionRegion> = emptyList(),
    val baselineEstimates: List<BaselineEstimate> = emptyList(),
    val verticalZones: List<VerticalZoneEstimate> = emptyList(),
    val indicators: RecognitionInputIndicators = RecognitionInputIndicators(),
) {
    init {
        require(requestId.isNotBlank() && requestFingerprint.isNotBlank())
        require(canvasWidth.isFinite() && canvasWidth > 0f)
        require(canvasHeight.isFinite() && canvasHeight > 0f)
        require(strokeBounds.keys.all { id -> strokes.any { it.id == id } })
    }
}

data class DetectedRecognitionRegion(
    val id: String,
    val bounds: SmartBoardBounds,
    val kind: RecognitionRegionKind,
    val confidence: Float?,
) {
    init {
        require(id.isNotBlank())
        require(confidence == null || confidence in 0f..1f)
    }
}

enum class RecognitionRegionKind { TEXT, SYMBOL, EXPRESSION, LABEL, GRAPH, DIAGRAM, UNKNOWN }

data class BaselineEstimate(
    val id: String,
    val y: Float,
    val startX: Float,
    val endX: Float,
    val confidence: Float,
) {
    init {
        require(id.isNotBlank())
        require(listOf(y, startX, endX, confidence).all(Float::isFinite))
        require(startX <= endX && confidence in 0f..1f)
    }
}

enum class VerticalZoneKind { UPPER, BASELINE, LOWER }

data class VerticalZoneEstimate(
    val regionId: String,
    val kind: VerticalZoneKind,
    val bounds: SmartBoardBounds,
    val confidence: Float,
) {
    init {
        require(regionId.isNotBlank())
        require(confidence in 0f..1f)
    }
}

data class RecognitionInputIndicators(
    val likelyGraph: Boolean = false,
    val likelyGeometryDiagram: Boolean = false,
    val likelyMatrix: Boolean = false,
    val likelyMultiline: Boolean = false,
    val likelyOverwriting: Boolean = false,
)

enum class RecognitionDeviceCost { LOW, MEDIUM, HIGH, VERY_HIGH }

data class RecognitionCapabilities(
    val simpleExpressions: Boolean = false,
    val superscripts: Boolean = false,
    val subscripts: Boolean = false,
    val fractions: Boolean = false,
    val radicals: Boolean = false,
    val matrices: Boolean = false,
    val multilineExpressions: Boolean = false,
    val setsAndLogic: Boolean = false,
    val probabilityNotation: Boolean = false,
    val graphs: Boolean = false,
    val geometryDiagrams: Boolean = false,
    val strokeAware: Boolean = false,
    val imageOnly: Boolean = false,
    val expectedDeviceCost: RecognitionDeviceCost,
)

enum class RecognitionDeviceTier { LOW_RESOURCE, BALANCED, HIGH_PERFORMANCE }

data class RecognitionContext(
    val requestId: String,
    val deadlineEpochMillis: Long,
    val maximumAlternatives: Int = 6,
    val deviceTier: RecognitionDeviceTier = RecognitionDeviceTier.BALANCED,
    val previousStableOutput: String? = null,
    val debugDiagnostics: Boolean = false,
) {
    init {
        require(requestId.isNotBlank())
        require(deadlineEpochMillis >= 0L)
        require(maximumAlternatives in 1..32)
    }
}

data class RecognitionTokenCandidate(
    val rawToken: String,
    val normalizedToken: String?,
    val confidence: Float?,
    val alternatives: List<RecognitionTokenAlternative> = emptyList(),
    val sourceBounds: SmartBoardBounds? = null,
) {
    init {
        require(rawToken.isNotEmpty())
        require(confidence == null || confidence in 0f..1f)
    }
}

data class RecognitionTokenAlternative(
    val value: String,
    val confidence: Float?,
    val evidence: List<String> = emptyList(),
) {
    init {
        require(value.isNotEmpty())
        require(confidence == null || confidence in 0f..1f)
    }
}

enum class RecognitionStructureKind {
    NUMBER,
    IDENTIFIER,
    OPERATOR,
    RELATION,
    FRACTION,
    POWER,
    SUBSCRIPT,
    RADICAL,
    FUNCTION,
    GROUP,
    MATRIX,
    ROW,
    MULTILINE,
    SET,
    LOGIC,
    INTEGRAL,
    SUM,
    LIMIT,
    GRAPH,
    GEOMETRY,
    UNKNOWN,
}

data class RecognitionStructureNode(
    val id: String,
    val kind: RecognitionStructureKind,
    val rawText: String?,
    val normalizedText: String?,
    val confidence: Float?,
    val bounds: SmartBoardBounds?,
    val sourceProviderId: String,
    val sourceTokenIndexes: List<Int> = emptyList(),
    val spatialEvidence: List<String> = emptyList(),
    val children: List<RecognitionStructureNode> = emptyList(),
) {
    init {
        require(id.isNotBlank() && sourceProviderId.isNotBlank())
        require(confidence == null || confidence in 0f..1f)
        require(sourceTokenIndexes.all { it >= 0 })
    }
}

data class RecognitionBoundingBoxAssociation(
    val tokenIndex: Int?,
    val strokeIds: Set<String>,
    val bounds: SmartBoardBounds,
    val confidence: Float?,
) {
    init {
        require(tokenIndex == null || tokenIndex >= 0)
        require(confidence == null || confidence in 0f..1f)
    }
}

data class ProviderRecognitionTiming(
    val preprocessingMillis: Long,
    val inferenceMillis: Long,
    val decodingMillis: Long,
) {
    init {
        require(preprocessingMillis >= 0L && inferenceMillis >= 0L && decodingMillis >= 0L)
    }

    val totalMillis: Long
        get() = preprocessingMillis + inferenceMillis + decodingMillis
}

data class ProviderRecognitionResult(
    val providerId: String,
    /** Exact provider output. Never replace this with normalized content. */
    val rawOutput: String?,
    val normalizedOutput: String?,
    val tokenCandidates: List<RecognitionTokenCandidate> = emptyList(),
    val overallConfidence: Float?,
    val structure: RecognitionStructureNode? = null,
    val boundingBoxAssociations: List<RecognitionBoundingBoxAssociation> = emptyList(),
    val timing: ProviderRecognitionTiming,
    val timedOut: Boolean,
    val cancelled: Boolean,
    val warnings: List<String> = emptyList(),
    val modelVersion: String,
    val requestFingerprint: String,
) {
    init {
        require(providerId.isNotBlank() && modelVersion.isNotBlank() && requestFingerprint.isNotBlank())
        require(overallConfidence == null || overallConfidence in 0f..1f)
        require(!(timedOut && cancelled)) { "A result must report one terminal reason." }
        if (!timedOut && !cancelled) require(!rawOutput.isNullOrBlank()) {
            "A completed provider result must preserve its raw output."
        }
    }
}

interface MathRecognitionProvider {
    val providerId: String
    val capabilities: RecognitionCapabilities

    suspend fun recognize(
        input: RecognitionInput,
        context: RecognitionContext,
    ): ProviderRecognitionResult

    suspend fun warmUp()

    fun cancel(requestId: String)

    fun release()
}
