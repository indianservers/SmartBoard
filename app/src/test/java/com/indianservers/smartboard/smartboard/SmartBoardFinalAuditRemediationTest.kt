package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.biology.data.BundledBiologyCatalogue
import com.indianservers.smartboard.smartboard.domain.ClearBoardCommand
import com.indianservers.smartboard.smartboard.domain.DeleteElementsCommand
import com.indianservers.smartboard.smartboard.domain.duplicateElements
import com.indianservers.smartboard.smartboard.models.BiologyConfirmedLabel
import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.BiologyContentType
import com.indianservers.smartboard.smartboard.models.BiologyGeneticsResult
import com.indianservers.smartboard.smartboard.models.BiologyProcessStep
import com.indianservers.smartboard.smartboard.models.BiologyResultElement
import com.indianservers.smartboard.smartboard.models.ChemistryFormulaComponent
import com.indianservers.smartboard.smartboard.models.ChemistryResultElement
import com.indianservers.smartboard.smartboard.models.ChemistrySolutionStep
import com.indianservers.smartboard.smartboard.models.EnglishCorrectionSuggestion
import com.indianservers.smartboard.smartboard.models.EnglishIssueType
import com.indianservers.smartboard.smartboard.models.EnglishReadabilityResult
import com.indianservers.smartboard.smartboard.models.EnglishResultElement
import com.indianservers.smartboard.smartboard.models.EnglishVocabularyResult
import com.indianservers.smartboard.smartboard.models.ImageElement
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.PartOfSpeechToken
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardConceptCandidate
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.SmartBoardResultStatus
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectClassification
import com.indianservers.smartboard.smartboard.models.SmartBoardTextRange
import com.indianservers.smartboard.smartboard.models.SubjectClassificationSource
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.tutor.BiologySmartBoardTutorHandler
import com.indianservers.smartboard.smartboard.tutor.DefaultUnifiedSmartBoardTutor
import com.indianservers.smartboard.smartboard.tutor.MathematicsSmartBoardTutorHandler
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorContextBuilder
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorToolCall
import com.indianservers.smartboard.smartboard.tutor.SmartBoardVerifiedStepStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardFinalAuditRemediationTest {
    private val bounds = SmartBoardBounds(0f, 0f, 200f, 80f)

    @Test
    fun `delete undo restores relationship classification and concept`() {
        val source = math("m1", "x=2")
        val result = math("m2", "2")
        val relation = SmartBoardRelationship("rel", SmartBoardRelationshipType.DERIVED_FROM, listOf(source.id, result.id), 1)
        val classification = classification(SmartBoardSubject.MATHEMATICS)
        val concept = SmartBoardConceptCandidate("concept", SmartBoardSubject.MATHEMATICS, "algebra", "Algebra", 1f, listOf("user"), null)
        val document = board(source, result).copy(
            relationships = listOf(relation),
            elementSubjectClassifications = mapOf(result.id to classification),
            elementConcepts = mapOf(result.id to concept),
        )
        val command = DeleteElementsCommand(
            listOf(result), listOf(1), listOf(relation),
            mapOf(result.id to classification), mapOf(result.id to concept),
        )
        val deleted = command.apply(document, 2)
        assertTrue(deleted.relationships.isEmpty())
        assertFalse(deleted.elementSubjectClassifications.containsKey(result.id))
        val restored = command.revert(deleted, 3)
        assertEquals(document.elements, restored.elements)
        assertEquals(listOf(relation), restored.relationships)
        assertEquals(classification, restored.elementSubjectClassifications[result.id])
        assertEquals(concept, restored.elementConcepts[result.id])
    }

    @Test
    fun `clear undo restores all document metadata`() {
        val element = math("m1", "x=2")
        val classification = classification(SmartBoardSubject.MATHEMATICS)
        val concept = SmartBoardConceptCandidate("concept", SmartBoardSubject.MATHEMATICS, "algebra", "Algebra", 1f, listOf("user"), null)
        val document = board(element).copy(
            elementSubjectClassifications = mapOf(element.id to classification),
            elementConcepts = mapOf(element.id to concept),
        )
        val command = ClearBoardCommand(
            document.elements, document.relationships,
            document.elementSubjectClassifications, document.elementConcepts,
        )
        val cleared = command.apply(document, 2)
        assertTrue(cleared.elements.isEmpty())
        assertTrue(cleared.elementSubjectClassifications.isEmpty())
        assertEquals(document, command.revert(cleared, document.updatedAt))
    }

    @Test
    fun `duplicated image retains valid immutable asset reference`() {
        val image = ImageElement("image", "asset-1", "asset-1.png", "image/png", 320, 240, 0, bounds, 1)
        val copy = duplicateElements(board(image), setOf(image.id), { "copy" }, 2).single() as ImageElement
        assertEquals("copy", copy.id)
        assertEquals(image.assetId, copy.assetId)
        assertEquals(image.relativePath, copy.relativePath)
    }

    @Test
    fun `schema five round trips structured subject result types`() {
        val chemistry = ChemistryResultElement(
            "chem", listOf("source"), "molar-mass", SmartBoardResultStatus.VERIFIED, "Water",
            "H2O", null, listOf(ChemistryFormulaComponent("H", 2, 1.008, 2.016)),
            18.015, "g/mol", listOf(ChemistrySolutionStep("2H + O", "Atomic contributions", true)),
            listOf("molar mass"), "periodic-table", emptyList(), emptyList(), listOf("elements-2026"), bounds, 2,
        )
        val correction = EnglishCorrectionSuggestion(
            "correction", EnglishIssueType.SUBJECT_VERB_AGREEMENT, "have", "has",
            SmartBoardTextRange(4, 8), "Singular agreement", .99f, "language-engine", true,
        )
        val english = EnglishResultElement(
            "english", listOf("text"), "grammar", SmartBoardResultStatus.VERIFIED_WITH_CONDITIONS, "Agreement",
            "She have.", "She has.", listOf(correction), listOf(PartOfSpeechToken("She", "pronoun", 1f)),
            "Optional accepted correction", EnglishReadabilityResult(2, 1, 2.0),
            listOf(EnglishVocabularyResult("assignment", "task", listOf("work"))),
            emptyList(), listOf("language-engine"), bounds, 3,
        )
        val biology = BiologyResultElement(
            "biology", listOf("diagram"), "labels", SmartBoardResultStatus.VERIFIED, "Cell labels",
            "cell", "Model-backed labels", listOf(BiologyConfirmedLabel("Nucleus", "nucleus", "Stores DNA", true)),
            listOf(BiologyProcessStep(1, "Interphase")), BiologyGeneticsResult("1:2:1", "3:1", true),
            "animal-cell", listOf("Review organelles"), emptyList(), listOf("biology-catalogue"), bounds, 4,
        )
        val original = board(chemistry, english, biology)
        val decoded = SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(original)).document!!
        assertEquals(SmartBoardDocument.CurrentSchemaVersion, decoded.schemaVersion)
        assertEquals(chemistry, decoded.elements[0])
        assertEquals(english, decoded.elements[1])
        assertEquals(biology, decoded.elements[2])
    }

    @Test
    fun `schema four document migrates to schema five`() {
        val current = SmartBoardDocumentCodec.encode(board(math("m1", "x=2")))
        val legacy = current.replaceFirst("SB|${SmartBoardDocument.CurrentSchemaVersion}|", "SB|4|")
        val decoded = SmartBoardDocumentCodec.decode(legacy)
        assertEquals(4, decoded.sourceSchemaVersion)
        assertEquals(SmartBoardDocument.CurrentSchemaVersion, decoded.document?.schemaVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate tutor handler registration is rejected`() {
        DefaultUnifiedSmartBoardTutor(
            listOf(MathematicsSmartBoardTutorHandler(), MathematicsSmartBoardTutorHandler()),
        )
    }

    @Test
    fun `registered verification and graph tools execute through bounded adapters`() = runBlocking {
        val expression = math("m1", "x + 1 = 2\nx = 1")
        val context = SmartBoardTutorContextBuilder().build(board(expression), setOf(expression.id))
        val tutor = DefaultUnifiedSmartBoardTutor()
        val verification = tutor.toolRegistry.execute(
            SmartBoardTutorToolCall(
                "verify", "math-verify", context.boardId, SmartBoardSubject.MATHEMATICS,
                listOf(expression.id), emptyMap(), false,
            ),
            context,
        )
        assertTrue(verification.success)
        val deniedGraph = tutor.toolRegistry.execute(
            SmartBoardTutorToolCall(
                "graph", "math-graph", context.boardId, SmartBoardSubject.MATHEMATICS,
                listOf(expression.id), emptyMap(), false,
            ),
            context,
        )
        assertFalse(deniedGraph.success)
        val graph = tutor.toolRegistry.execute(
            SmartBoardTutorToolCall(
                "graph-approved", "math-graph", context.boardId, SmartBoardSubject.MATHEMATICS,
                listOf(expression.id), emptyMap(), true,
            ),
            context,
        )
        assertTrue(graph.success)
        assertEquals("graph2d", graph.moduleRoute)
    }

    @Test
    fun `inconclusive Biology label does not block independent known label`() = runBlocking {
        val unknown = biology("b1", "not-a-reviewed-label")
        val knownLabel = BundledBiologyCatalogue.catalogue.diagrams
            .asSequence()
            .flatMap { it.labels.asSequence() }
            .first()
            .text
        val known = biology("b2", knownLabel)
        val context = SmartBoardTutorContextBuilder().build(board(unknown, known), setOf(unknown.id, known.id))
        val handler = BiologySmartBoardTutorHandler()
        val result = handler.verifyWork(handler.buildSubjectContext(context))
        assertEquals(SmartBoardVerifiedStepStatus.UNCERTAIN, result.stepResults[0].status)
        assertEquals(SmartBoardVerifiedStepStatus.VALID, result.stepResults[1].status)
    }

    private fun board(vararg elements: com.indianservers.smartboard.smartboard.models.SmartBoardElement) =
        SmartBoardDocument.new("board", 1).copy(elements = elements.toList())

    private fun math(id: String, value: String) =
        MathExpressionElement(id, value, null, value, emptyList(), 1f, bounds, 1)

    private fun biology(id: String, value: String) = BiologyContentElement(
        id, value, BiologyContentType.LABELLED_DIAGRAM, emptyList(), emptyList(),
        bounds, 1, classification(SmartBoardSubject.BIOLOGY),
    )

    private fun classification(subject: SmartBoardSubject) = SmartBoardSubjectClassification(
        subject, emptyList(), 1f, SubjectClassificationSource.USER_SELECTION, true, false, emptyList(),
    )
}
