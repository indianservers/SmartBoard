package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.SemanticMathNodeKind
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.recognition.SmartBoardRegionRole
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSemanticExpressionBuilder
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSpecialistKind
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSpecialistRecognitionRegistry
import com.indianservers.smartboard.smartboard.recognition.SmartBoardWholeBoardUnderstandingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardSemanticRecognitionTest {
    @Test
    fun equationBuildsTypedTreeMathMlSpokenFormAndApproximateStrokeLinks() {
        val tree = SmartBoardSemanticExpressionBuilder.build(
            latex = "x^2+2*x=7",
            sourceStrokeIds = listOf("s1", "s2", "s3"),
            confidence = .91f,
        )
        assertEquals(SemanticMathNodeKind.EQUATION, tree.root.kind)
        assertEquals("=", tree.root.value)
        assertEquals(2, tree.root.children.size)
        assertTrue(tree.mathMl.startsWith("<math>"))
        assertTrue(tree.spokenForm.contains("equals"))
        assertTrue(tree.root.sourceStrokeIds.containsAll(listOf("s1", "s2", "s3")))
        assertFalse(tree.exactStrokeMapping)
    }

    @Test
    fun matrixUsesSpecialistStructureAndExistingEngineRouting() {
        val tree = SmartBoardSemanticExpressionBuilder.build(
            latex = "\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}",
            normalizedExpression = "[[1,2];[3,4]]",
        )
        assertEquals(SemanticMathNodeKind.MATRIX, tree.root.kind)
        assertEquals(2, tree.root.children.size)
        assertEquals(2, tree.root.children.first().children.size)
        val interpretations = SmartBoardSpecialistRecognitionRegistry.recognize(
            tree.authoredLatex,
            SmartBoardSubject.MATHEMATICS,
            tree,
        )
        assertEquals(SmartBoardSpecialistKind.MATRIX, interpretations.first().specialist)
        assertTrue(interpretations.first().supportedActions.contains("Row reduce"))
    }

    @Test
    fun semanticTreeSurvivesSaveOpenRoundTrip() {
        val tree = SmartBoardSemanticExpressionBuilder.build("x+1=3", sourceStrokeIds = listOf("ink"))
        val expression = MathExpressionElement(
            "m", "x+1=3", null, "x+1=3", listOf("ink"), .9f,
            SmartBoardBounds(10f, 10f, 180f, 60f), 2L, semanticTree = tree,
        )
        val document = SmartBoardDocument.new("semantic", 1L).copy(elements = listOf(expression))
        val reopened = requireNotNull(SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(document), recover = false).document)
        val reopenedExpression = reopened.elements.single() as MathExpressionElement
        assertEquals(tree, reopenedExpression.semanticTree)
        assertEquals(SmartBoardDocument.CurrentSchemaVersion, reopened.schemaVersion)
    }

    @Test
    fun wholeBoardUnderstandingSuggestsReviewableLabelAndRepresentationLinks() {
        val axes = shape("axes", SmartBoardShapeType.COORDINATE_AXES, SmartBoardBounds(0f, 0f, 200f, 180f))
        val label = TextElement("label", "y = x squared", SmartBoardBounds(210f, 20f, 320f, 55f), 2L)
        val tree = SmartBoardSemanticExpressionBuilder.build("y=x^2")
        val formula = MathExpressionElement(
            "formula", "y=x^2", null, "y=x^2", emptyList(), 1f,
            SmartBoardBounds(205f, 75f, 325f, 120f), 3L, semanticTree = tree,
        )
        val document = SmartBoardDocument.new("board", 1L).copy(elements = listOf(axes, label, formula))
        val understanding = SmartBoardWholeBoardUnderstandingEngine.analyze(document, 4L)
        assertTrue(understanding.regions.any { it.role == SmartBoardRegionRole.GRAPH })
        assertTrue(understanding.relationshipSuggestions.any { it.relationship.type == SmartBoardRelationshipType.LABELS })
        assertTrue(understanding.relationshipSuggestions.any { it.relationship.type == SmartBoardRelationshipType.REPRESENTS })
        assertTrue(document.relationships.isEmpty())
    }

    private fun shape(id: String, type: SmartBoardShapeType, bounds: SmartBoardBounds) = ShapeElement(
        id = id,
        shapeType = type,
        points = listOf(SmartBoardPoint(bounds.left, bounds.top), SmartBoardPoint(bounds.right, bounds.bottom)),
        sourceStrokeIds = listOf("source-$id"),
        recognitionConfidence = .9f,
        strokeWidth = 2f,
        argbColor = 0xff000000,
        bounds = bounds,
        createdAt = 1L,
    )
}
