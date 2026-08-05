package com.indianservers.smartboard.smartboard.intelligence.state

import com.indianservers.smartboard.smartboard.models.SmartBoardBounds

/**
 * Immutable semantic state layered over the existing [SmartBoardDocument].
 *
 * Raw [sourceStrokeIds] remain the source of truth. This state stores links and
 * interpretations only; it never manufactures or replaces handwriting.
 */
data class BoardIntelligenceState(
    val documentId: String,
    val pages: List<BoardPageState>,
    val activePageId: String,
    val activeMode: BoardMode = BoardMode.BOARD,
    val selectedObjectIds: Set<String> = emptySet(),
    val recognizedObjects: List<BoardObject> = emptyList(),
    val semanticRelations: List<BoardRelation> = emptyList(),
    val lessonContext: BoardLessonContext = BoardLessonContext(),
    val unresolvedAmbiguities: List<RecognitionAmbiguity> = emptyList(),
    val detectedErrors: List<BoardError> = emptyList(),
    val aiSuggestions: List<BoardSuggestion> = emptyList(),
    val processingState: BoardProcessingState = BoardProcessingState(),
) {
    init {
        require(documentId.isNotBlank())
        require(pages.isNotEmpty())
        require(pages.map(BoardPageState::id).distinct().size == pages.size)
        require(pages.any { it.id == activePageId })
        require(recognizedObjects.map(BoardObject::id).distinct().size == recognizedObjects.size)
        val objectIds = recognizedObjects.mapTo(hashSetOf(), BoardObject::id)
        require(selectedObjectIds.all(objectIds::contains))
        require(semanticRelations.all { relation ->
            relation.fromObjectId in objectIds && relation.toObjectId in objectIds
        })
    }

    val activePage: BoardPageState
        get() = pages.first { it.id == activePageId }

    fun accepts(result: VersionedBoardResult): Boolean =
        result.documentId == documentId &&
            result.pageId == activePageId &&
            result.requestVersion == processingState.contentVersion &&
            result.regionVersion == processingState.regionVersions[result.regionId]
}

enum class BoardMode {
    BOARD,
    GRAPH,
    GEOMETRY,
    STATISTICS,
    MATRIX,
    AI_LESSON,
    PRESENTATION,
}

data class BoardPageState(
    val id: String,
    val documentPageId: String,
    val objectIds: List<String> = emptyList(),
    val revision: Long = 0L,
) {
    init {
        require(id.isNotBlank() && documentPageId.isNotBlank())
        require(revision >= 0L)
        require(objectIds.distinct().size == objectIds.size)
    }
}

data class BoardObjectMetadata(
    val id: String,
    val pageId: String,
    val bounds: SmartBoardBounds,
    val sourceStrokeIds: List<String>,
    val confidence: Float?,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceRevision: Long,
) {
    init {
        require(id.isNotBlank() && pageId.isNotBlank())
        require(sourceStrokeIds.distinct().size == sourceStrokeIds.size)
        require(confidence == null || confidence in 0f..1f)
        require(createdAt >= 0L && updatedAt >= createdAt && sourceRevision >= 0L)
    }
}

sealed interface BoardObject {
    val metadata: BoardObjectMetadata
    val id: String get() = metadata.id
    val pageId: String get() = metadata.pageId
    val bounds: SmartBoardBounds get() = metadata.bounds
    val sourceStrokeIds: List<String> get() = metadata.sourceStrokeIds
    val confidence: Float? get() = metadata.confidence
    val createdAt: Long get() = metadata.createdAt
    val updatedAt: Long get() = metadata.updatedAt
}

data class HandwritingObject(
    override val metadata: BoardObjectMetadata,
    val rawInkGroupId: String,
) : BoardObject

data class TextObject(
    override val metadata: BoardObjectMetadata,
    val rawText: String,
    val normalizedText: String?,
) : BoardObject

data class MathExpressionObject(
    override val metadata: BoardObjectMetadata,
    val rawRecognition: String,
    val normalizedExpression: String?,
    val latex: String?,
    val parseTreeId: String?,
    val alternatives: List<RecognitionAlternative> = emptyList(),
) : BoardObject

data class EquationObject(
    override val metadata: BoardObjectMetadata,
    val expressionObjectId: String,
    val leftExpression: String,
    val relation: String,
    val rightExpression: String,
) : BoardObject

data class GraphObject(
    override val metadata: BoardObjectMetadata,
    val sourceExpressionObjectIds: List<String>,
    val graphType: String,
    val editableDefinition: String,
    val liveSyncEnabled: Boolean,
) : BoardObject

data class GeometryObject(
    override val metadata: BoardObjectMetadata,
    val geometryType: String,
    val labelObjectIds: List<String> = emptyList(),
    val measurements: Map<String, String> = emptyMap(),
) : BoardObject

data class MatrixObject(
    override val metadata: BoardObjectMetadata,
    val rows: List<List<String>>,
    val expressionObjectId: String?,
) : BoardObject {
    init {
        require(rows.isNotEmpty())
        require(rows.map(List<String>::size).distinct().size == 1)
    }
}

data class TableObject(
    override val metadata: BoardObjectMetadata,
    val cells: List<List<String>>,
    val headerRows: Int = 0,
) : BoardObject

data class DiagramObject(
    override val metadata: BoardObjectMetadata,
    val diagramType: String,
    val componentObjectIds: List<String> = emptyList(),
) : BoardObject

data class ArrowObject(
    override val metadata: BoardObjectMetadata,
    val fromObjectId: String?,
    val toObjectId: String?,
    val meaning: String?,
) : BoardObject

data class AnnotationObject(
    override val metadata: BoardObjectMetadata,
    val text: String,
    val targetObjectIds: List<String> = emptyList(),
) : BoardObject

data class HeadingObject(
    override val metadata: BoardObjectMetadata,
    val text: String,
    val level: Int,
) : BoardObject {
    init {
        require(level in 1..6)
    }
}

data class QuestionObject(
    override val metadata: BoardObjectMetadata,
    val prompt: String,
    val answerObjectIds: List<String> = emptyList(),
) : BoardObject

data class AnswerObject(
    override val metadata: BoardObjectMetadata,
    val content: String,
    val questionObjectId: String?,
    val verificationStatus: String?,
) : BoardObject

data class FormulaObject(
    override val metadata: BoardObjectMetadata,
    val name: String?,
    val expressionObjectId: String,
    val conceptIds: Set<String> = emptySet(),
) : BoardObject

data class RecognitionAlternative(
    val value: String,
    val confidence: Float?,
    val evidence: List<String> = emptyList(),
) {
    init {
        require(value.isNotBlank())
        require(confidence == null || confidence in 0f..1f)
    }
}

enum class BoardRelationKind {
    DERIVED_FROM_STROKES,
    PARSED_AS,
    LABELS,
    CONTROLS_GRAPH,
    GENERATED_FROM,
    EXPLAINS,
    ANSWERS,
    NEXT_STEP,
    PART_OF,
    DEPENDS_ON,
    FORCE_ON,
    SUPPLIES_DATA,
}

data class BoardRelation(
    val id: String,
    val fromObjectId: String,
    val toObjectId: String,
    val kind: BoardRelationKind,
    val confidence: Float?,
    val userConfirmed: Boolean = false,
) {
    init {
        require(id.isNotBlank() && fromObjectId.isNotBlank() && toObjectId.isNotBlank())
        require(fromObjectId != toObjectId)
        require(confidence == null || confidence in 0f..1f)
    }
}

data class BoardLessonContext(
    val title: String? = null,
    val topicIds: Set<String> = emptySet(),
    val learnerLevel: String? = null,
    val importantObjectIds: Set<String> = emptySet(),
    val unresolvedIssueIds: Set<String> = emptySet(),
)

data class RecognitionAmbiguity(
    val id: String,
    val objectId: String?,
    val bounds: SmartBoardBounds,
    val rawValue: String,
    val alternatives: List<RecognitionAlternative>,
    val resolvedValue: String? = null,
) {
    init {
        require(id.isNotBlank() && rawValue.isNotBlank())
        require(alternatives.isNotEmpty())
    }
}

enum class BoardErrorKind {
    RECOGNITION,
    ARITHMETIC,
    ALGEBRAIC,
    SIGN,
    DOMAIN,
    MISSING_SOLUTION,
    GRAPH_MISMATCH,
    RELATIONSHIP,
}

data class BoardError(
    val id: String,
    val kind: BoardErrorKind,
    val objectIds: Set<String>,
    val bounds: SmartBoardBounds,
    val message: String,
    val confidence: Float?,
) {
    init {
        require(id.isNotBlank() && message.isNotBlank())
        require(confidence == null || confidence in 0f..1f)
    }
}

enum class BoardSuggestionKind {
    GRAPH,
    EXPLAIN,
    HINT,
    CHECK_STEP,
    VISUALIZE,
    PARAMETER_SLIDER,
    PRACTICE,
    SAVE_FORMULA,
}

data class BoardSuggestion(
    val id: String,
    val kind: BoardSuggestionKind,
    val label: String,
    val relatedObjectIds: Set<String>,
    val relevance: Float,
    val dismissed: Boolean = false,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank())
        require(relevance in 0f..1f)
    }
}

enum class BoardProcessingPhase { IDLE, DEBOUNCING, GROUPING, RECOGNIZING, PARSING, LINKING, UPDATING_DEPENDENCIES }

data class DirtyBoardRegion(
    val id: String,
    val pageId: String,
    val bounds: SmartBoardBounds,
    val affectedStrokeIds: Set<String>,
    val version: Long,
) {
    init {
        require(id.isNotBlank() && pageId.isNotBlank() && version >= 0L)
    }
}

data class BoardProcessingState(
    val phase: BoardProcessingPhase = BoardProcessingPhase.IDLE,
    val contentVersion: Long = 0L,
    val dirtyRegions: List<DirtyBoardRegion> = emptyList(),
    val regionVersions: Map<String, Long> = emptyMap(),
    val pendingObjectIds: Set<String> = emptySet(),
    val lastCompletedAt: Long? = null,
) {
    init {
        require(contentVersion >= 0L)
        require(regionVersions.values.all { it >= 0L })
        require(lastCompletedAt == null || lastCompletedAt >= 0L)
    }
}

data class VersionedBoardResult(
    val documentId: String,
    val pageId: String,
    val regionId: String,
    val requestVersion: Long,
    val regionVersion: Long,
)
