package com.indianservers.smartboard.smartboard.domain

import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.BiologyResultElement
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionElement
import com.indianservers.smartboard.smartboard.models.ChemistryResultElement
import com.indianservers.smartboard.smartboard.models.EnglishTextElement
import com.indianservers.smartboard.smartboard.models.EnglishResultElement
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramElement
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultElement
import com.indianservers.smartboard.smartboard.models.ActionResultElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.ImageElement
import com.indianservers.smartboard.smartboard.models.SolutionSequenceElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBackground
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectClassification
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectMode
import com.indianservers.smartboard.smartboard.models.SmartBoardConceptCandidate

sealed interface SmartBoardCommand {
    val label: String
    fun apply(document: SmartBoardDocument, now: Long): SmartBoardDocument
    fun revert(document: SmartBoardDocument, now: Long): SmartBoardDocument
}

data class AddElementCommand(val element: SmartBoardElement) : SmartBoardCommand {
    override val label = "Add element"
    override fun apply(document: SmartBoardDocument, now: Long) =
        document.copy(elements = document.elements.filterNot { it.id == element.id } + element, updatedAt = now)
    override fun revert(document: SmartBoardDocument, now: Long) =
        document.copy(elements = document.elements.filterNot { it.id == element.id }, updatedAt = now)
}

data class AddElementsCommand(val elements: List<SmartBoardElement>) : SmartBoardCommand {
    override val label = "Duplicate selection"
    override fun apply(document: SmartBoardDocument, now: Long) =
        document.copy(elements = document.elements.filterNot { candidate -> elements.any { it.id == candidate.id } } + elements, updatedAt = now)
    override fun revert(document: SmartBoardDocument, now: Long) =
        document.copy(elements = document.elements.filterNot { candidate -> elements.any { it.id == candidate.id } }, updatedAt = now)
}

data class InsertTutorOutputCommand(
    val element: SmartBoardElement,
    val relationship: SmartBoardRelationship,
) : SmartBoardCommand {
    override val label = "Insert tutor output"
    override fun apply(document: SmartBoardDocument, now: Long) = document.copy(
        elements = document.elements.filterNot { it.id == element.id } + element,
        relationships = document.relationships.filterNot { it.id == relationship.id } + relationship,
        updatedAt = now,
    )
    override fun revert(document: SmartBoardDocument, now: Long) = document.copy(
        elements = document.elements.filterNot { it.id == element.id },
        relationships = document.relationships.filterNot { it.id == relationship.id },
        updatedAt = now,
    )
}

data class DeleteElementsCommand(
    val removed: List<SmartBoardElement>,
    val originalIndices: List<Int>,
    val affectedRelationships: List<SmartBoardRelationship> = emptyList(),
    val removedClassifications: Map<String, SmartBoardSubjectClassification> = emptyMap(),
    val removedConcepts: Map<String, SmartBoardConceptCandidate> = emptyMap(),
) : SmartBoardCommand {
    init { require(removed.size == originalIndices.size) }
    override val label = "Delete selection"
    override fun apply(document: SmartBoardDocument, now: Long) =
        document.copy(
            elements = document.elements.filterNot { candidate -> removed.any { it.id == candidate.id } },
            relationships = document.relationships.mapNotNull { relationship ->
                relationship.copy(elementIds = relationship.elementIds.filterNot { id -> removed.any { it.id == id } })
                    .takeIf { it.elementIds.size >= 2 }
            },
            elementSubjectClassifications = document.elementSubjectClassifications - removed.map(SmartBoardElement::id).toSet(),
            elementConcepts = document.elementConcepts - removed.map(SmartBoardElement::id).toSet(),
            updatedAt = now,
        )
    override fun revert(document: SmartBoardDocument, now: Long): SmartBoardDocument {
        val restored = document.elements.toMutableList()
        removed.zip(originalIndices).sortedBy { it.second }.forEach { (element, index) -> restored.add(index.coerceIn(0, restored.size), element) }
        val affectedIds = affectedRelationships.mapTo(hashSetOf(), SmartBoardRelationship::id)
        return document.copy(
            elements = restored,
            relationships = document.relationships.filterNot { it.id in affectedIds } + affectedRelationships,
            elementSubjectClassifications = document.elementSubjectClassifications + removedClassifications,
            elementConcepts = document.elementConcepts + removedConcepts,
            updatedAt = now,
        )
    }
}

data class MoveElementsCommand(val elementIds: Set<String>, val delta: SmartBoardPoint) : SmartBoardCommand {
    override val label = "Move selection"
    override fun apply(document: SmartBoardDocument, now: Long) = document.translated(elementIds, delta, now)
    override fun revert(document: SmartBoardDocument, now: Long) = document.translated(elementIds, SmartBoardPoint(-delta.x, -delta.y), now)
}

data class ClearBoardCommand(
    val removed: List<SmartBoardElement>,
    val relationships: List<SmartBoardRelationship>,
    val classifications: Map<String, SmartBoardSubjectClassification> = emptyMap(),
    val concepts: Map<String, SmartBoardConceptCandidate> = emptyMap(),
) : SmartBoardCommand {
    override val label = "Clear board"
    override fun apply(document: SmartBoardDocument, now: Long) = document.copy(
        elements = emptyList(),
        relationships = emptyList(),
        elementSubjectClassifications = emptyMap(),
        elementConcepts = emptyMap(),
        updatedAt = now,
    )
    override fun revert(document: SmartBoardDocument, now: Long) = document.copy(
        elements = removed,
        relationships = relationships,
        elementSubjectClassifications = classifications,
        elementConcepts = concepts,
        updatedAt = now,
    )
}

data class BackgroundCommand(val before: SmartBoardBackground, val after: SmartBoardBackground) : SmartBoardCommand {
    override val label = "Change background"
    override fun apply(document: SmartBoardDocument, now: Long) = document.copy(background = after, updatedAt = now)
    override fun revert(document: SmartBoardDocument, now: Long) = document.copy(background = before, updatedAt = now)
}

data class ChangeBoardSubjectModeCommand(
    val before: SmartBoardSubjectMode,
    val after: SmartBoardSubjectMode,
) : SmartBoardCommand {
    override val label = if (before.locked != after.locked) "Change subject lock" else "Change Board subject"
    override fun apply(document: SmartBoardDocument, now: Long) =
        document.copy(subject = after.selection, subjectMode = after, updatedAt = now)
    override fun revert(document: SmartBoardDocument, now: Long) =
        document.copy(subject = before.selection, subjectMode = before, updatedAt = now)
}

data class AssignSubjectClassificationCommand(
    val elementIds: Set<String>,
    val before: Map<String, SmartBoardSubjectClassification>,
    val after: SmartBoardSubjectClassification,
) : SmartBoardCommand {
    override val label = "Classify selected content"
    override fun apply(document: SmartBoardDocument, now: Long) = document.withClassifications(
        document.elementSubjectClassifications + elementIds.associateWith { after },
        elementIds,
        now,
    )
    override fun revert(document: SmartBoardDocument, now: Long): SmartBoardDocument {
        val restored = document.elementSubjectClassifications.toMutableMap().apply {
            elementIds.forEach { id -> before[id]?.let { put(id, it) } ?: remove(id) }
        }
        return document.withClassifications(restored, elementIds, now)
    }
}

data class EditMathExpressionCommand(val before: MathExpressionElement, val after: MathExpressionElement) : SmartBoardCommand {
    override val label = "Edit recognized expression"
    override fun apply(document: SmartBoardDocument, now: Long) = document.replaced(after, now)
    override fun revert(document: SmartBoardDocument, now: Long) = document.replaced(before, now)
}

data class ReplaceElementCommand(
    val before: SmartBoardElement,
    val after: SmartBoardElement,
    override val label: String = "Edit element",
) : SmartBoardCommand {
    init { require(before.id == after.id) }
    override fun apply(document: SmartBoardDocument, now: Long) = document.replaced(after, now)
    override fun revert(document: SmartBoardDocument, now: Long) = document.replaced(before, now)
}

data class SetStrokeVisibilityCommand(val before: Map<String, Boolean>, val hidden: Boolean) : SmartBoardCommand {
    override val label = if (hidden) "Hide source handwriting" else "Show source handwriting"
    override fun apply(document: SmartBoardDocument, now: Long) = document.withStrokeVisibility(before.keys, hidden, now)
    override fun revert(document: SmartBoardDocument, now: Long) = document.copy(
        elements = document.elements.map { element ->
            val old = before[element.id] ?: return@map element
            if (element is StrokeElement) element.copy(hidden = old) else element
        },
        updatedAt = now,
    )
}

data class InsertRecognizedExpressionCommand(
    val expression: SmartBoardElement,
    val relationship: SmartBoardRelationship,
    val sourceHiddenBefore: Map<String, Boolean>,
    val hideSources: Boolean,
    val relatedElements: List<SmartBoardElement> = emptyList(),
    val classifications: Map<String, SmartBoardSubjectClassification> = emptyMap(),
    val concepts: Map<String, SmartBoardConceptCandidate> = emptyMap(),
) : SmartBoardCommand {
    override val label = "Insert recognized expression"
    override fun apply(document: SmartBoardDocument, now: Long) = document.copy(
        elements = document.elements.map { element ->
            if (!hideSources || element.id !in sourceHiddenBefore) element
            else when (element) {
                is StrokeElement -> element.copy(hidden = true)
                else -> element
            }
        }.filterNot { existing -> existing.id == expression.id || relatedElements.any { it.id == existing.id } } + expression + relatedElements,
        relationships = document.relationships.filterNot { it.id == relationship.id } + relationship,
        elementSubjectClassifications = document.elementSubjectClassifications + classifications,
        elementConcepts = document.elementConcepts + concepts,
        updatedAt = now,
    )
    override fun revert(document: SmartBoardDocument, now: Long) = document.copy(
        elements = document.elements.filterNot { existing -> existing.id == expression.id || relatedElements.any { it.id == existing.id } }.map { element ->
            val hidden = sourceHiddenBefore[element.id] ?: return@map element
            when (element) {
                is StrokeElement -> element.copy(hidden = hidden)
                else -> element
            }
        },
        relationships = document.relationships.filterNot { it.id == relationship.id },
        elementSubjectClassifications = document.elementSubjectClassifications - (setOf(expression.id) + relatedElements.map(SmartBoardElement::id)),
        elementConcepts = document.elementConcepts - (setOf(expression.id) + relatedElements.map(SmartBoardElement::id)),
        updatedAt = now,
    )
}

data class GroupCommand(val relationship: SmartBoardRelationship) : SmartBoardCommand {
    override val label = "Group strokes"
    override fun apply(document: SmartBoardDocument, now: Long) =
        document.copy(relationships = document.relationships.filterNot { it.id == relationship.id } + relationship, updatedAt = now)
    override fun revert(document: SmartBoardDocument, now: Long) =
        document.copy(relationships = document.relationships.filterNot { it.id == relationship.id }, updatedAt = now)
}

data class UngroupCommand(val relationships: List<SmartBoardRelationship>) : SmartBoardCommand {
    override val label = "Ungroup strokes"
    override fun apply(document: SmartBoardDocument, now: Long) =
        document.copy(relationships = document.relationships.filterNot { existing -> relationships.any { it.id == existing.id } }, updatedAt = now)
    override fun revert(document: SmartBoardDocument, now: Long) =
        document.copy(relationships = document.relationships + relationships.filterNot { relationship -> document.relationships.any { it.id == relationship.id } }, updatedAt = now)
}

data class AddRelationshipsCommand(val relationships: List<SmartBoardRelationship>) : SmartBoardCommand {
    override val label = "Connect understood board regions"
    override fun apply(document: SmartBoardDocument, now: Long) = document.copy(
        relationships = document.relationships.filterNot { existing -> relationships.any { it.id == existing.id } } + relationships,
        updatedAt = now,
    )
    override fun revert(document: SmartBoardDocument, now: Long) = document.copy(
        relationships = document.relationships.filterNot { existing -> relationships.any { it.id == existing.id } },
        updatedAt = now,
    )
}

data class ReorderElementsCommand(val before: List<String>, val after: List<String>) : SmartBoardCommand {
    override val label = "Reorder selection"
    override fun apply(document: SmartBoardDocument, now: Long) = document.reordered(after, now)
    override fun revert(document: SmartBoardDocument, now: Long) = document.reordered(before, now)
}

class SmartBoardCommandHistory(private val limit: Int = 120) {
    init {
        require(limit in 10..1_000)
    }
    private val undo = ArrayDeque<SmartBoardCommand>()
    private val redo = ArrayDeque<SmartBoardCommand>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val undoLabel get() = undo.lastOrNull()?.label
    val redoLabel get() = redo.lastOrNull()?.label

    fun execute(document: SmartBoardDocument, command: SmartBoardCommand, now: Long): SmartBoardDocument {
        undo += command
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
        return command.apply(document, now)
    }

    fun undo(document: SmartBoardDocument, now: Long): SmartBoardDocument {
        val command = undo.removeLastOrNull() ?: return document
        redo += command
        return command.revert(document, now)
    }

    fun redo(document: SmartBoardDocument, now: Long): SmartBoardDocument {
        val command = redo.removeLastOrNull() ?: return document
        undo += command
        return command.apply(document, now)
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}

fun duplicateElements(document: SmartBoardDocument, ids: Set<String>, idFactory: () -> String, now: Long, delta: SmartBoardPoint = SmartBoardPoint(24f, 24f)): List<SmartBoardElement> =
    document.elements.filter { it.id in ids }.map { element ->
        when (element) {
            is StrokeElement -> element.copy(
                id = idFactory(),
                points = element.points.map { it.copy(x = it.x + delta.x, y = it.y + delta.y) },
                bounds = element.bounds.translate(delta),
                createdAt = now,
            )
            is ShapeElement -> element.copy(
                id = idFactory(),
                points = element.points.map { it + delta },
                bounds = element.bounds.translate(delta),
                createdAt = now,
            )
            is MathExpressionElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is TextElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is TableElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is ImageElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is ActionResultElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is GraphConfigurationElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is SolutionSequenceElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is PhysicsExpressionElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is PhysicsResultElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is PhysicsDiagramElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is ChemistryExpressionElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is EnglishTextElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is BiologyContentElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is ChemistryResultElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is EnglishResultElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
            is BiologyResultElement -> element.copy(id = idFactory(), bounds = element.bounds.translate(delta), createdAt = now)
        }
    }

fun groupRelationship(ids: Set<String>, id: String, now: Long) =
    SmartBoardRelationship(id, SmartBoardRelationshipType.GROUP, ids.toList(), now)

private fun SmartBoardDocument.translated(ids: Set<String>, delta: SmartBoardPoint, now: Long) = copy(
    elements = elements.map { element ->
        if (element.id !in ids) element else when (element) {
            is StrokeElement -> element.copy(
                points = element.points.map { it.copy(x = it.x + delta.x, y = it.y + delta.y) },
                bounds = element.bounds.translate(delta),
            )
            is ShapeElement -> element.copy(
                points = element.points.map { it + delta },
                bounds = element.bounds.translate(delta),
            )
            is MathExpressionElement -> element.copy(bounds = element.bounds.translate(delta))
            is TextElement -> element.copy(bounds = element.bounds.translate(delta))
            is TableElement -> element.copy(bounds = element.bounds.translate(delta))
            is ImageElement -> element.copy(bounds = element.bounds.translate(delta))
            is ActionResultElement -> element.copy(bounds = element.bounds.translate(delta))
            is GraphConfigurationElement -> element.copy(bounds = element.bounds.translate(delta))
            is SolutionSequenceElement -> element.copy(bounds = element.bounds.translate(delta))
            is PhysicsExpressionElement -> element.copy(bounds = element.bounds.translate(delta))
            is PhysicsResultElement -> element.copy(bounds = element.bounds.translate(delta))
            is PhysicsDiagramElement -> element.copy(bounds = element.bounds.translate(delta))
            is ChemistryExpressionElement -> element.copy(bounds = element.bounds.translate(delta))
            is EnglishTextElement -> element.copy(bounds = element.bounds.translate(delta))
            is BiologyContentElement -> element.copy(bounds = element.bounds.translate(delta))
            is ChemistryResultElement -> element.copy(bounds = element.bounds.translate(delta))
            is EnglishResultElement -> element.copy(bounds = element.bounds.translate(delta))
            is BiologyResultElement -> element.copy(bounds = element.bounds.translate(delta))
        }
    },
    updatedAt = now,
)

private fun SmartBoardDocument.replaced(value: SmartBoardElement, now: Long) =
    copy(elements = elements.map { if (it.id == value.id) value else it }, updatedAt = now)

private fun SmartBoardDocument.withStrokeVisibility(ids: Set<String>, hidden: Boolean, now: Long) = copy(
    elements = elements.map { element -> if (element is StrokeElement && element.id in ids) element.copy(hidden = hidden) else element },
    updatedAt = now,
)

private fun SmartBoardDocument.reordered(order: List<String>, now: Long): SmartBoardDocument {
    val byId = elements.associateBy(SmartBoardElement::id)
    val ordered = order.mapNotNull(byId::get) + elements.filterNot { it.id in order }
    return copy(elements = ordered, updatedAt = now)
}

private fun SmartBoardDocument.withClassifications(
    values: Map<String, SmartBoardSubjectClassification>,
    ids: Set<String>,
    now: Long,
) = copy(
    elementSubjectClassifications = values,
    elements = elements.map { element ->
        val value = values[element.id]
        if (element.id !in ids || value == null) element else when (element) {
            is ChemistryExpressionElement -> element.copy(subjectClassification = value)
            is EnglishTextElement -> element.copy(subjectClassification = value)
            is BiologyContentElement -> element.copy(subjectClassification = value)
            else -> element
        }
    },
    updatedAt = now,
)
