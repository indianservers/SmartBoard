package com.indianservers.smartboard.smartboard.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.SemanticExpressionTree
import com.indianservers.smartboard.smartboard.models.SemanticMathNode
import com.indianservers.smartboard.smartboard.models.SemanticMathNodeKind
import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.BiologyConfirmedLabel
import com.indianservers.smartboard.smartboard.models.BiologyGeneticsResult
import com.indianservers.smartboard.smartboard.models.BiologyProcessStep
import com.indianservers.smartboard.smartboard.models.BiologyResultElement
import com.indianservers.smartboard.smartboard.models.BiologyContentType
import com.indianservers.smartboard.smartboard.models.BiologyLabelCandidate
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionElement
import com.indianservers.smartboard.smartboard.models.ChemistryFormulaComponent
import com.indianservers.smartboard.smartboard.models.ChemistryResultElement
import com.indianservers.smartboard.smartboard.models.ChemistrySolutionStep
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionType
import com.indianservers.smartboard.smartboard.models.EnglishTextElement
import com.indianservers.smartboard.smartboard.models.EnglishCorrectionSuggestion
import com.indianservers.smartboard.smartboard.models.EnglishIssueType
import com.indianservers.smartboard.smartboard.models.EnglishReadabilityResult
import com.indianservers.smartboard.smartboard.models.EnglishResultElement
import com.indianservers.smartboard.smartboard.models.EnglishVocabularyResult
import com.indianservers.smartboard.smartboard.models.PartOfSpeechToken
import com.indianservers.smartboard.smartboard.models.SmartBoardResultStatus
import com.indianservers.smartboard.smartboard.models.SmartBoardTextRange
import com.indianservers.smartboard.smartboard.models.EnglishTextType
import com.indianservers.smartboard.smartboard.models.PhysicsActionType
import com.indianservers.smartboard.smartboard.models.PhysicsContentType
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramElement
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramInference
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramObject
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramRelation
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramType
import com.indianservers.smartboard.smartboard.models.PhysicsEngineMetadata
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultStatus
import com.indianservers.smartboard.smartboard.models.PhysicsSolutionStep
import com.indianservers.smartboard.smartboard.models.PhysicsSubstitution
import com.indianservers.smartboard.smartboard.models.PhysicsTopic
import com.indianservers.smartboard.smartboard.models.ActionResultElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.ImageElement
import com.indianservers.smartboard.smartboard.models.RecognitionRegion
import com.indianservers.smartboard.smartboard.models.RecognitionQualityTier
import com.indianservers.smartboard.smartboard.models.SmartBoardGraphKind
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionMode
import com.indianservers.smartboard.smartboard.models.SmartBoardResultKind
import com.indianservers.smartboard.smartboard.models.SolutionSequenceElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SolutionStep
import com.indianservers.smartboard.smartboard.models.SolutionStepStatus
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBackground
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardInputMode
import com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode
import com.indianservers.smartboard.smartboard.models.SmartBoardPreferences
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SmartBoardConceptCandidate
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectClassification
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectMode
import com.indianservers.smartboard.smartboard.models.SubjectCandidate
import com.indianservers.smartboard.smartboard.models.SubjectClassificationSource
import com.indianservers.smartboard.smartboard.models.SmartBoardViewport
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardSessionMemory
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardSessionMemoryCodec
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorConversation
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorConversationCodec

data class SmartBoardDecodeResult(
    val document: SmartBoardDocument?,
    val sourceSchemaVersion: Int,
    val recovered: Boolean,
    val warnings: List<String>,
)

object SmartBoardDocumentMigration {
    fun migrate(sourceVersion: Int, document: SmartBoardDocument): SmartBoardDocument {
        require(sourceVersion in 0..SmartBoardDocument.CurrentSchemaVersion)
        return when (sourceVersion) {
            0 -> document.copy(
                schemaVersion = SmartBoardDocument.CurrentSchemaVersion,
                background = document.background,
                relationships = document.relationships,
            )
            1 -> document.copy(schemaVersion = SmartBoardDocument.CurrentSchemaVersion, recognitionRegions = emptyList())
            else -> document.copy(
                schemaVersion = SmartBoardDocument.CurrentSchemaVersion,
                subjectMode = if (sourceVersion < 4) {
                    SmartBoardSubjectMode(
                        document.subject.takeUnless { it == SmartBoardSubject.GENERAL } ?: SmartBoardSubject.AUTO,
                        locked = false,
                        userSelected = false,
                        lastChangedAt = document.updatedAt,
                    )
                } else document.subjectMode,
            )
        }
    }
}

object SmartBoardDocumentCodec {
    fun encode(document: SmartBoardDocument): String = buildString {
        appendLine(
            listOf(
                "SB",
                document.schemaVersion,
                pack(document.id),
                pack(document.title),
                document.subject.name,
                document.createdAt,
                document.updatedAt,
                document.background.name,
                document.viewport.panX,
                document.viewport.panY,
                document.viewport.zoom,
                document.subjectMode.selection.name,
                document.subjectMode.locked,
                document.subjectMode.userSelected,
                document.subjectMode.lastChangedAt,
            ).joinToString("|"),
        )
        document.elements.forEach { element ->
            when (element) {
                is StrokeElement -> appendLine(
                    listOf(
                        "S",
                        pack(element.id),
                        element.tool.name,
                        element.width,
                        element.opacity,
                        element.argbColor,
                        bounds(element.bounds),
                        element.createdAt,
                        element.hidden,
                        pack(element.points.joinToString(";") { "${it.x},${it.y},${it.pressure},${it.timestampMillis}" }),
                    ).joinToString("|"),
                )
                is ShapeElement -> appendLine(
                    listOf(
                        "Z", pack(element.id), element.shapeType.name,
                        pack(element.sourceStrokeIds.joinToString(",")), element.recognitionConfidence,
                        element.strokeWidth, element.argbColor, element.opacity,
                        element.fillArgbColor?.toString().orEmpty(), element.rotationDegrees, element.locked,
                        bounds(element.bounds), element.createdAt, element.hidden,
                        pack(element.points.joinToString(";") { "${it.x},${it.y}" }),
                    ).joinToString("|"),
                )
                is MathExpressionElement -> appendLine(
                    listOf(
                        "M",
                        pack(element.id),
                        pack(element.rawLatex),
                        pack(element.correctedLatex.orEmpty()),
                        pack(element.normalizedExpression.orEmpty()),
                        pack(element.sourceStrokeIds.joinToString(",")),
                        element.recognitionConfidence?.toString().orEmpty(),
                        bounds(element.bounds),
                        element.createdAt,
                        element.hidden,
                        pack(element.semanticTree?.let(::encodeSemanticTree).orEmpty()),
                    ).joinToString("|"),
                )
                is TextElement -> appendLine(listOf("T", pack(element.id), pack(element.text), bounds(element.bounds), element.createdAt, element.hidden).joinToString("|"))
                is TableElement -> appendLine(
                    listOf(
                        "U", pack(element.id), pack(element.columnHeaders.joinToString("\u001f")),
                        pack(encodeTableRows(element.rows)), pack(element.sourceElementIds.joinToString(",")),
                        element.firstRowIsHeader, bounds(element.bounds), element.createdAt, element.hidden,
                    ).joinToString("|"),
                )
                is ImageElement -> appendLine(
                    listOf("I", pack(element.id), pack(element.assetId), pack(element.relativePath), element.mimeType, element.pixelWidth, element.pixelHeight,
                        element.rotationDegrees, bounds(element.bounds), element.createdAt, element.hidden).joinToString("|"),
                )
                is ActionResultElement -> appendLine(
                    listOf("A", pack(element.id), element.kind.name, pack(element.title), pack(element.exact.orEmpty()), pack(element.approximate.orEmpty()),
                        pack(element.details.joinToString("\u001f")), pack(element.assumptions.joinToString("\u001f")), pack(element.sourceElementIds.joinToString(",")),
                        element.verified, bounds(element.bounds), element.createdAt, element.hidden).joinToString("|"),
                )
                is GraphConfigurationElement -> appendLine(
                    listOf("G", pack(element.id), element.graphKind.name, pack(element.expressions.joinToString("\u001f")),
                        pack(element.sourceElementIds.joinToString(",")), pack(element.moduleRoute), bounds(element.bounds), element.createdAt, element.hidden).joinToString("|"),
                )
                is SolutionSequenceElement -> appendLine(
                    listOf("Q", pack(element.id), pack(element.problemExpression), pack(encodeSteps(element.steps)),
                        element.firstInvalidStepIndex?.toString().orEmpty(), pack(element.sourceRegionIds.joinToString(",")),
                        bounds(element.bounds), element.createdAt, element.hidden).joinToString("|"),
                )
                is PhysicsExpressionElement -> appendLine(
                    listOf(
                        "P", pack(element.id), pack(element.rawSource), pack(element.correctedSource.orEmpty()),
                        element.contentType.name, element.topic?.name.orEmpty(), pack(element.formulaId.orEmpty()),
                        pack(element.sourceStrokeIds.joinToString(",")), element.recognitionConfidence?.toString().orEmpty(),
                        pack(element.ambiguities.joinToString("\u001f")), pack(element.warnings.joinToString("\u001f")),
                        bounds(element.bounds), element.createdAt, element.hidden,
                    ).joinToString("|"),
                )
                is PhysicsResultElement -> appendLine(
                    listOf(
                        "V", pack(element.id), pack(element.sourceElementIds.joinToString(",")), element.actionType.name,
                        pack(element.title), pack(element.formulaLatex.orEmpty()), pack(element.rearrangedFormulaLatex.orEmpty()),
                        pack(encodePhysicsSubstitutions(element.substitutions)), pack(element.exactResultLatex.orEmpty()),
                        element.numericalResult?.toString().orEmpty(), pack(element.resultUnitSymbol.orEmpty()),
                        element.significantFigures?.toString().orEmpty(), pack(encodePhysicsSteps(element.steps)),
                        pack(element.assumptions.joinToString("\u001f")), pack(element.warnings.joinToString("\u001f")),
                        pack(element.engineMetadata.engines.joinToString("\u001f")), element.engineMetadata.deterministic,
                        element.status.name, bounds(element.bounds), element.createdAt, element.hidden,
                    ).joinToString("|"),
                )
                is PhysicsDiagramElement -> appendLine(
                    listOf(
                        "D", pack(element.id), element.diagramType.name, pack(element.sourceStrokeIds.joinToString(",")),
                        pack(encodeDiagramObjects(element.detectedObjects)), pack(encodeDiagramRelations(element.confirmedRelations)),
                        pack(encodeDiagramInferences(element.inferredRelations)), element.confidence?.toString().orEmpty(),
                        bounds(element.bounds), element.createdAt, element.hidden,
                    ).joinToString("|"),
                )
                is ChemistryExpressionElement -> appendLine(
                    listOf(
                        "C", pack(element.id), pack(element.rawText), pack(element.normalizedChemicalNotation.orEmpty()),
                        element.expressionType.name, pack(element.sourceStrokeIds.joinToString(",")), bounds(element.bounds),
                        element.createdAt, element.hidden, pack(encodeClassification(element.subjectClassification)),
                    ).joinToString("|"),
                )
                is EnglishTextElement -> appendLine(
                    listOf(
                        "E", pack(element.id), pack(element.rawText), pack(element.correctedText.orEmpty()),
                        pack(element.languageCode.orEmpty()), element.textType.name, pack(element.sourceStrokeIds.joinToString(",")),
                        pack(element.lineBreaks.joinToString(",")), bounds(element.bounds), element.createdAt, element.hidden,
                        pack(encodeClassification(element.subjectClassification)),
                    ).joinToString("|"),
                )
                is BiologyContentElement -> appendLine(
                    listOf(
                        "B", pack(element.id), pack(element.recognizedText.orEmpty()), element.contentType.name,
                        pack(element.detectedLabels.joinToString("\u001e") { "${pack(it.text)},${it.confidence?.toString().orEmpty()},${it.confirmed}" }),
                        pack(element.sourceStrokeIds.joinToString(",")), bounds(element.bounds), element.createdAt, element.hidden,
                        pack(encodeClassification(element.subjectClassification)),
                    ).joinToString("|"),
                )
                is ChemistryResultElement -> appendLine(
                    listOf(
                        "J", pack(element.id), pack(element.sourceElementIds.joinToString(",")), pack(element.actionId),
                        element.status.name, pack(element.title), pack(element.normalizedNotation.orEmpty()),
                        pack(element.balancedEquation.orEmpty()), pack(encodeChemistryComponents(element.formulaBreakdown)),
                        element.numericalResult?.toString().orEmpty(), pack(element.resultUnit.orEmpty()),
                        pack(encodeChemistrySteps(element.steps)), pack(element.identifiedConcepts.joinToString("\u001f")),
                        pack(element.visualizationReference.orEmpty()), pack(element.assumptions.joinToString("\u001f")),
                        pack(element.warnings.joinToString("\u001f")), pack(element.engineIds.joinToString("\u001f")),
                        bounds(element.bounds), element.createdAt, element.hidden,
                    ).joinToString("|"),
                )
                is EnglishResultElement -> appendLine(
                    listOf(
                        "K", pack(element.id), pack(element.sourceElementIds.joinToString(",")), pack(element.actionId),
                        element.status.name, pack(element.title), pack(element.originalText), pack(element.suggestedText.orEmpty()),
                        pack(encodeEnglishCorrections(element.corrections)), pack(encodePartOfSpeech(element.partsOfSpeech)),
                        pack(element.explanation.orEmpty()), pack(encodeReadability(element.readability)),
                        pack(encodeVocabulary(element.vocabularyResults)), pack(element.warnings.joinToString("\u001f")),
                        pack(element.engineIds.joinToString("\u001f")), bounds(element.bounds), element.createdAt, element.hidden,
                    ).joinToString("|"),
                )
                is BiologyResultElement -> appendLine(
                    listOf(
                        "L", pack(element.id), pack(element.sourceElementIds.joinToString(",")), pack(element.actionId),
                        element.status.name, pack(element.title), pack(element.conceptId.orEmpty()),
                        pack(element.explanation.orEmpty()), pack(encodeBiologyLabels(element.confirmedLabels)),
                        pack(encodeBiologyProcess(element.processSteps)), pack(encodeGenetics(element.geneticsResult)),
                        pack(element.modelReference.orEmpty()), pack(element.studySummary.joinToString("\u001f")),
                        pack(element.warnings.joinToString("\u001f")), pack(element.engineIds.joinToString("\u001f")),
                        bounds(element.bounds), element.createdAt, element.hidden,
                    ).joinToString("|"),
                )
            }
        }
        document.elementSubjectClassifications.forEach { (elementId, classification) ->
            appendLine("X|${pack(elementId)}|${pack(encodeClassification(classification))}")
        }
        document.elementConcepts.forEach { (elementId, concept) ->
            appendLine(
                listOf(
                    "O", pack(elementId), pack(concept.id), concept.subject.name, pack(concept.conceptId.orEmpty()),
                    pack(concept.displayName), concept.confidence?.toString().orEmpty(), pack(concept.evidence.joinToString("\u001f")),
                    pack(concept.parentConceptId.orEmpty()), pack(concept.engineCapabilityIds.joinToString(",")),
                ).joinToString("|"),
            )
        }
        document.relationships.forEach { relationship ->
            appendLine(
                listOf(
                    "R",
                    pack(relationship.id),
                    relationship.type.name,
                    pack(relationship.elementIds.joinToString(",")),
                    relationship.createdAt,
                ).joinToString("|"),
            )
        }
        document.recognitionRegions.sortedBy(RecognitionRegion::order).forEach { region ->
            appendLine(listOf("N", pack(region.id), bounds(region.bounds), region.order, pack(region.sourceElementIds.joinToString(",")), region.excluded).joinToString("|"))
        }
    }

    fun decode(source: String, recover: Boolean = true): SmartBoardDecodeResult {
        val warnings = mutableListOf<String>()
        val lines = source.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.firstOrNull()?.split('|')
        if (header == null || header.firstOrNull() != "SB" || header.size < 11) {
            return SmartBoardDecodeResult(null, -1, false, listOf("Smart Board header is missing or invalid."))
        }
        val sourceVersion = header[1].toIntOrNull() ?: return SmartBoardDecodeResult(null, -1, false, listOf("Schema version is invalid."))
        if (sourceVersion > SmartBoardDocument.CurrentSchemaVersion) {
            return SmartBoardDecodeResult(null, sourceVersion, false, listOf("This board uses a newer schema version."))
        }
        val elements = mutableListOf<SmartBoardElement>()
        val relationships = mutableListOf<SmartBoardRelationship>()
        val regions = mutableListOf<RecognitionRegion>()
        val classifications = mutableMapOf<String, SmartBoardSubjectClassification>()
        val concepts = mutableMapOf<String, SmartBoardConceptCandidate>()
        lines.drop(1).forEachIndexed { index, line ->
            runCatching {
                val fields = line.split('|')
                when (fields.firstOrNull()) {
                    "S" -> elements += decodeStroke(fields)
                    "Z" -> elements += decodeShape(fields)
                    "M" -> elements += decodeMath(fields)
                    "T" -> elements += decodeText(fields)
                    "U" -> elements += decodeTable(fields)
                    "I" -> elements += decodeImage(fields)
                    "A" -> elements += decodeActionResult(fields)
                    "G" -> elements += decodeGraph(fields)
                    "Q" -> elements += decodeSolutionSequence(fields)
                    "P" -> elements += decodePhysicsExpression(fields)
                    "V" -> elements += decodePhysicsResult(fields)
                    "D" -> elements += decodePhysicsDiagram(fields)
                    "C" -> elements += decodeChemistry(fields)
                    "E" -> elements += decodeEnglish(fields)
                    "B" -> elements += decodeBiology(fields)
                    "J" -> elements += decodeChemistryResult(fields)
                    "K" -> elements += decodeEnglishResult(fields)
                    "L" -> elements += decodeBiologyResult(fields)
                    "X" -> classifications[unpack(fields[1])] = decodeClassification(unpack(fields[2]))
                    "O" -> concepts[unpack(fields[1])] = decodeConcept(fields)
                    "R" -> relationships += decodeRelationship(fields)
                    "N" -> regions += decodeRegion(fields)
                    else -> error("Unknown record")
                }
            }.onFailure {
                if (!recover) throw it
                warnings += "Skipped damaged record ${index + 2}."
            }
        }
        return runCatching {
            val created = header[5].toLong()
            val document = SmartBoardDocument(
                id = unpack(header[2]),
                title = unpack(header[3]).ifBlank { "Recovered Board" },
                subject = enumValueOrDefault(header[4], SmartBoardSubject.MATHEMATICS),
                schemaVersion = sourceVersion.coerceAtLeast(1),
                createdAt = created,
                updatedAt = header[6].toLong().coerceAtLeast(created),
                viewport = SmartBoardViewport(header[8].toFloat(), header[9].toFloat(), header[10].toFloat().coerceIn(.1f, 12f)),
                background = enumValueOrDefault(header[7], SmartBoardBackground.GRID),
                elements = elements.distinctBy(SmartBoardElement::id),
                relationships = relationships.filter { relationship -> relationship.elementIds.any { id -> elements.any { it.id == id } } },
                recognitionRegions = regions.distinctBy(RecognitionRegion::id).sortedBy(RecognitionRegion::order),
                subjectMode = if (header.size >= 15) {
                    SmartBoardSubjectMode(
                        enumValueOrDefault(header[11], SmartBoardSubject.AUTO),
                        header[12].toBooleanStrictOrNull() ?: false,
                        header[13].toBooleanStrictOrNull() ?: false,
                        header[14].toLongOrNull() ?: created,
                    )
                } else SmartBoardSubjectMode(enumValueOrDefault(header[4], SmartBoardSubject.MATHEMATICS), false, false, created),
                elementSubjectClassifications = classifications.filterKeys { id -> elements.any { it.id == id } },
                elementConcepts = concepts.filterKeys { id -> elements.any { it.id == id } },
            )
            SmartBoardDecodeResult(
                SmartBoardDocumentMigration.migrate(sourceVersion, document),
                sourceVersion,
                warnings.isNotEmpty() || sourceVersion != SmartBoardDocument.CurrentSchemaVersion,
                warnings,
            )
        }.getOrElse { SmartBoardDecodeResult(null, sourceVersion, false, warnings + (it.message ?: "Board metadata is invalid.")) }
    }

    private fun decodeStroke(fields: List<String>): StrokeElement {
        require(fields.size >= 10)
        val points = unpack(fields[9]).split(';').filter(String::isNotBlank).map { encoded ->
            val point = encoded.split(',')
            StrokePoint(point[0].toFloat(), point[1].toFloat(), point[2].toFloat(), point[3].toLong())
        }
        return StrokeElement(
            id = unpack(fields[1]),
            points = points,
            tool = enumValueOrDefault(fields[2], StrokeTool.PEN),
            width = fields[3].toFloat(),
            opacity = fields[4].toFloat(),
            argbColor = fields[5].toLong(),
            bounds = parseBounds(fields[6]),
            createdAt = fields[7].toLong(),
            hidden = fields[8].toBooleanStrictOrNull() ?: false,
        )
    }

    private fun decodeShape(fields: List<String>): ShapeElement {
        require(fields.size >= 15)
        val points = unpack(fields[14]).split(';').filter(String::isNotBlank).map { encoded ->
            val point = encoded.split(',')
            SmartBoardPoint(point[0].toFloat(), point[1].toFloat())
        }
        return ShapeElement(
            id = unpack(fields[1]),
            shapeType = enumValueOrDefault(fields[2], SmartBoardShapeType.CURVE),
            points = points,
            sourceStrokeIds = unpack(fields[3]).split(',').filter(String::isNotBlank),
            recognitionConfidence = fields[4].toFloat(),
            strokeWidth = fields[5].toFloat(),
            argbColor = fields[6].toLong(),
            opacity = fields[7].toFloat(),
            fillArgbColor = fields[8].toLongOrNull(),
            rotationDegrees = fields[9].toFloat(),
            locked = fields[10].toBooleanStrictOrNull() ?: false,
            bounds = parseBounds(fields[11]),
            createdAt = fields[12].toLong(),
            hidden = fields[13].toBooleanStrictOrNull() ?: false,
        )
    }

    private fun decodeMath(fields: List<String>) = MathExpressionElement(
        id = unpack(fields[1]),
        rawLatex = unpack(fields[2]).ifBlank { "?" },
        correctedLatex = unpack(fields[3]).takeIf(String::isNotBlank),
        normalizedExpression = unpack(fields[4]).takeIf(String::isNotBlank),
        sourceStrokeIds = unpack(fields[5]).split(',').filter(String::isNotBlank),
        recognitionConfidence = fields[6].toFloatOrNull(),
        bounds = parseBounds(fields[7]),
        createdAt = fields[8].toLong(),
        hidden = fields.getOrNull(9)?.toBooleanStrictOrNull() ?: false,
        semanticTree = fields.getOrNull(10)?.let(::unpack)?.takeIf(String::isNotBlank)?.let(::decodeSemanticTree),
    )

    private fun encodeSemanticTree(tree: SemanticExpressionTree): String {
        val records = mutableListOf(
            listOf(
                pack(tree.authoredLatex),
                pack(tree.engineExpression),
                pack(tree.mathMl),
                pack(tree.spokenForm),
                tree.parserVerified,
                tree.exactStrokeMapping,
            ).joinToString("|"),
        )
        fun appendNode(node: SemanticMathNode) {
            records += listOf(
                pack(node.id),
                node.kind.name,
                pack(node.value.orEmpty()),
                pack(node.sourceStrokeIds.joinToString(",")),
                node.confidence?.toString().orEmpty(),
                pack(node.spokenForm),
                node.children.size,
            ).joinToString("|")
            node.children.forEach(::appendNode)
        }
        appendNode(tree.root)
        return records.joinToString("\u001d")
    }

    private fun decodeSemanticTree(source: String): SemanticExpressionTree {
        val records = source.split('\u001d')
        require(records.size >= 2)
        val header = records.first().split('|')
        require(header.size >= 6)
        var index = 1
        fun readNode(): SemanticMathNode {
            val fields = records.getOrNull(index++)?.split('|') ?: error("Semantic node is missing")
            require(fields.size >= 7)
            val childCount = fields[6].toInt().coerceIn(0, 256)
            return SemanticMathNode(
                id = unpack(fields[0]),
                kind = enumValueOrDefault(fields[1], SemanticMathNodeKind.UNKNOWN),
                value = unpack(fields[2]).takeIf(String::isNotBlank),
                children = List(childCount) { readNode() },
                sourceStrokeIds = unpack(fields[3]).split(',').filter(String::isNotBlank),
                confidence = fields[4].toFloatOrNull(),
                spokenForm = unpack(fields[5]),
            )
        }
        return SemanticExpressionTree(
            root = readNode(),
            authoredLatex = unpack(header[0]),
            engineExpression = unpack(header[1]),
            mathMl = unpack(header[2]),
            spokenForm = unpack(header[3]),
            parserVerified = header[4].toBooleanStrictOrNull() ?: false,
            exactStrokeMapping = header[5].toBooleanStrictOrNull() ?: false,
        )
    }

    private fun decodeRelationship(fields: List<String>) = SmartBoardRelationship(
        id = unpack(fields[1]),
        type = enumValueOrDefault(fields[2], SmartBoardRelationshipType.GROUP),
        elementIds = unpack(fields[3]).split(',').filter(String::isNotBlank),
        createdAt = fields[4].toLong(),
    )

    private fun decodeText(fields: List<String>) = TextElement(unpack(fields[1]), unpack(fields[2]), parseBounds(fields[3]), fields[4].toLong(), fields[5].toBooleanStrictOrNull() ?: false)

    private fun decodeTable(fields: List<String>) = TableElement(
        id = unpack(fields[1]),
        columnHeaders = unpack(fields[2]).split('\u001f').ifEmpty { listOf("Column 1") },
        rows = decodeTableRows(unpack(fields[3])),
        sourceElementIds = unpack(fields[4]).split(',').filter(String::isNotBlank),
        firstRowIsHeader = fields[5].toBooleanStrictOrNull() ?: true,
        bounds = parseBounds(fields[6]),
        createdAt = fields[7].toLong(),
        hidden = fields.getOrNull(8)?.toBooleanStrictOrNull() ?: false,
    )

    private fun decodeImage(fields: List<String>) = ImageElement(
        unpack(fields[1]), unpack(fields[2]), unpack(fields[3]), fields[4], fields[5].toInt(), fields[6].toInt(),
        fields[7].toInt(), parseBounds(fields[8]), fields[9].toLong(), fields[10].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeActionResult(fields: List<String>) = ActionResultElement(
        unpack(fields[1]), enumValueOrDefault(fields[2], SmartBoardResultKind.CAS), unpack(fields[3]), unpack(fields[4]).takeIf(String::isNotBlank),
        unpack(fields[5]).takeIf(String::isNotBlank), unpack(fields[6]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[7]).split('\u001f').filter(String::isNotBlank), unpack(fields[8]).split(',').filter(String::isNotBlank),
        fields[9].toBooleanStrictOrNull() ?: false, parseBounds(fields[10]), fields[11].toLong(), fields[12].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeGraph(fields: List<String>) = GraphConfigurationElement(
        unpack(fields[1]), enumValueOrDefault(fields[2], SmartBoardGraphKind.EXPLICIT_2D),
        unpack(fields[3]).split('\u001f').filter(String::isNotBlank), unpack(fields[4]).split(',').filter(String::isNotBlank),
        unpack(fields[5]), parseBounds(fields[6]), fields[7].toLong(), fields[8].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeSolutionSequence(fields: List<String>) = SolutionSequenceElement(
        unpack(fields[1]), unpack(fields[2]), decodeSteps(unpack(fields[3])), fields[4].toIntOrNull(),
        unpack(fields[5]).split(',').filter(String::isNotBlank), parseBounds(fields[6]), fields[7].toLong(), fields[8].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeRegion(fields: List<String>) = RecognitionRegion(
        unpack(fields[1]), parseBounds(fields[2]), fields[3].toInt(), unpack(fields[4]).split(',').filter(String::isNotBlank),
        fields[5].toBooleanStrictOrNull() ?: false,
    )

    private fun decodePhysicsExpression(fields: List<String>) = PhysicsExpressionElement(
        unpack(fields[1]), unpack(fields[2]).ifBlank { "?" }, unpack(fields[3]).takeIf(String::isNotBlank),
        enumValueOrDefault(fields[4], PhysicsContentType.UNKNOWN),
        fields[5].takeIf(String::isNotBlank)?.let { enumValueOrDefault(it, PhysicsTopic.UNKNOWN) },
        unpack(fields[6]).takeIf(String::isNotBlank), unpack(fields[7]).split(',').filter(String::isNotBlank),
        fields[8].toFloatOrNull(), unpack(fields[9]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[10]).split('\u001f').filter(String::isNotBlank), parseBounds(fields[11]),
        fields[12].toLong(), fields[13].toBooleanStrictOrNull() ?: false,
    )

    private fun decodePhysicsResult(fields: List<String>) = PhysicsResultElement(
        unpack(fields[1]), unpack(fields[2]).split(',').filter(String::isNotBlank),
        enumValueOrDefault(fields[3], PhysicsActionType.EXPLAIN_FORMULA), unpack(fields[4]),
        unpack(fields[5]).takeIf(String::isNotBlank), unpack(fields[6]).takeIf(String::isNotBlank),
        decodePhysicsSubstitutions(unpack(fields[7])), unpack(fields[8]).takeIf(String::isNotBlank),
        fields[9].toDoubleOrNull(), unpack(fields[10]).takeIf(String::isNotBlank), fields[11].toIntOrNull(),
        decodePhysicsSteps(unpack(fields[12])), unpack(fields[13]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[14]).split('\u001f').filter(String::isNotBlank),
        PhysicsEngineMetadata(unpack(fields[15]).split('\u001f').filter(String::isNotBlank), fields[16].toBooleanStrictOrNull() ?: true),
        enumValueOrDefault(fields[17], PhysicsResultStatus.NEEDS_CONFIRMATION), parseBounds(fields[18]),
        fields[19].toLong(), fields[20].toBooleanStrictOrNull() ?: false,
    )

    private fun decodePhysicsDiagram(fields: List<String>) = PhysicsDiagramElement(
        unpack(fields[1]), enumValueOrDefault(fields[2], PhysicsDiagramType.UNKNOWN),
        unpack(fields[3]).split(',').filter(String::isNotBlank), decodeDiagramObjects(unpack(fields[4])),
        decodeDiagramRelations(unpack(fields[5])), decodeDiagramInferences(unpack(fields[6])),
        fields[7].toFloatOrNull(), parseBounds(fields[8]), fields[9].toLong(),
        fields[10].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeChemistry(fields: List<String>) = ChemistryExpressionElement(
        unpack(fields[1]), unpack(fields[2]), unpack(fields[3]).takeIf(String::isNotBlank),
        enumValueOrDefault(fields[4], ChemistryExpressionType.UNKNOWN),
        unpack(fields[5]).split(',').filter(String::isNotBlank), parseBounds(fields[6]), fields[7].toLong(),
        decodeClassification(unpack(fields[9])), fields[8].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeEnglish(fields: List<String>) = EnglishTextElement(
        unpack(fields[1]), unpack(fields[2]), unpack(fields[3]).takeIf(String::isNotBlank),
        unpack(fields[4]).takeIf(String::isNotBlank), enumValueOrDefault(fields[5], EnglishTextType.UNKNOWN),
        unpack(fields[6]).split(',').filter(String::isNotBlank),
        unpack(fields[7]).split(',').mapNotNull(String::toIntOrNull), parseBounds(fields[8]), fields[9].toLong(),
        decodeClassification(unpack(fields[11])), fields[10].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeBiology(fields: List<String>) = BiologyContentElement(
        unpack(fields[1]), unpack(fields[2]).takeIf(String::isNotBlank), enumValueOrDefault(fields[3], BiologyContentType.UNKNOWN),
        unpack(fields[4]).split('\u001e').filter(String::isNotBlank).map { encoded ->
            val value = encoded.split(','); BiologyLabelCandidate(unpack(value[0]), value[1].toFloatOrNull(), value[2].toBooleanStrictOrNull() ?: false)
        },
        unpack(fields[5]).split(',').filter(String::isNotBlank), parseBounds(fields[6]), fields[7].toLong(),
        decodeClassification(unpack(fields[9])), fields[8].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeChemistryResult(fields: List<String>) = ChemistryResultElement(
        unpack(fields[1]), unpack(fields[2]).split(',').filter(String::isNotBlank), unpack(fields[3]),
        enumValueOrDefault(fields[4], SmartBoardResultStatus.NEEDS_CONFIRMATION), unpack(fields[5]),
        unpack(fields[6]).takeIf(String::isNotBlank), unpack(fields[7]).takeIf(String::isNotBlank),
        decodeChemistryComponents(unpack(fields[8])), fields[9].toDoubleOrNull(),
        unpack(fields[10]).takeIf(String::isNotBlank), decodeChemistrySteps(unpack(fields[11])),
        unpack(fields[12]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[13]).takeIf(String::isNotBlank),
        unpack(fields[14]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[15]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[16]).split('\u001f').filter(String::isNotBlank),
        parseBounds(fields[17]), fields[18].toLong(), fields[19].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeEnglishResult(fields: List<String>) = EnglishResultElement(
        unpack(fields[1]), unpack(fields[2]).split(',').filter(String::isNotBlank), unpack(fields[3]),
        enumValueOrDefault(fields[4], SmartBoardResultStatus.NEEDS_CONFIRMATION), unpack(fields[5]),
        unpack(fields[6]), unpack(fields[7]).takeIf(String::isNotBlank),
        decodeEnglishCorrections(unpack(fields[8])), decodePartOfSpeech(unpack(fields[9])),
        unpack(fields[10]).takeIf(String::isNotBlank), decodeReadability(unpack(fields[11])),
        decodeVocabulary(unpack(fields[12])), unpack(fields[13]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[14]).split('\u001f').filter(String::isNotBlank),
        parseBounds(fields[15]), fields[16].toLong(), fields[17].toBooleanStrictOrNull() ?: false,
    )

    private fun decodeBiologyResult(fields: List<String>) = BiologyResultElement(
        unpack(fields[1]), unpack(fields[2]).split(',').filter(String::isNotBlank), unpack(fields[3]),
        enumValueOrDefault(fields[4], SmartBoardResultStatus.NEEDS_CONFIRMATION), unpack(fields[5]),
        unpack(fields[6]).takeIf(String::isNotBlank), unpack(fields[7]).takeIf(String::isNotBlank),
        decodeBiologyLabels(unpack(fields[8])), decodeBiologyProcess(unpack(fields[9])),
        decodeGenetics(unpack(fields[10])), unpack(fields[11]).takeIf(String::isNotBlank),
        unpack(fields[12]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[13]).split('\u001f').filter(String::isNotBlank),
        unpack(fields[14]).split('\u001f').filter(String::isNotBlank),
        parseBounds(fields[15]), fields[16].toLong(), fields[17].toBooleanStrictOrNull() ?: false,
    )

    private fun encodeClassification(value: SmartBoardSubjectClassification) = listOf(
        value.primarySubject?.name.orEmpty(),
        value.confidence?.toString().orEmpty(),
        value.source.name,
        value.userConfirmed,
        value.inheritedFromBoardMode,
        pack(value.warnings.joinToString("\u001f")),
        pack(value.alternateSubjects.joinToString("\u001e") { "${it.subject.name},${it.confidence?.toString().orEmpty()}" }),
    ).joinToString(";")

    private fun decodeClassification(source: String): SmartBoardSubjectClassification {
        val fields = source.split(';')
        return SmartBoardSubjectClassification(
            fields[0].takeIf(String::isNotBlank)?.let { enumValueOrDefault(it, SmartBoardSubject.GENERAL) },
            unpack(fields.getOrElse(6) { "" }).split('\u001e').filter(String::isNotBlank).map { encoded ->
                val candidate = encoded.split(',')
                SubjectCandidate(enumValueOrDefault(candidate[0], SmartBoardSubject.MATHEMATICS), candidate.getOrNull(1)?.toFloatOrNull(), emptyList())
            },
            fields.getOrNull(1)?.toFloatOrNull(),
            fields.getOrNull(2)?.let { enumValueOrDefault(it, SubjectClassificationSource.UNKNOWN) } ?: SubjectClassificationSource.UNKNOWN,
            fields.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
            fields.getOrNull(4)?.toBooleanStrictOrNull() ?: false,
            unpack(fields.getOrElse(5) { "" }).split('\u001f').filter(String::isNotBlank),
        )
    }

    private fun decodeConcept(fields: List<String>) = SmartBoardConceptCandidate(
        unpack(fields[2]), enumValueOrDefault(fields[3], SmartBoardSubject.GENERAL),
        unpack(fields[4]).takeIf(String::isNotBlank), unpack(fields[5]), fields[6].toFloatOrNull(),
        unpack(fields[7]).split('\u001f').filter(String::isNotBlank), unpack(fields[8]).takeIf(String::isNotBlank),
        unpack(fields[9]).split(',').filter(String::isNotBlank),
    )

    private fun encodePhysicsSubstitutions(values: List<PhysicsSubstitution>) = values.joinToString("\u001e") {
        listOf(pack(it.symbol), it.value, pack(it.unitSymbol.orEmpty())).joinToString(",")
    }
    private fun decodePhysicsSubstitutions(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(','); PhysicsSubstitution(unpack(fields[0]), fields[1].toDouble(), unpack(fields[2]).takeIf(String::isNotBlank))
    }
    private fun encodePhysicsSteps(values: List<PhysicsSolutionStep>) = values.joinToString("\u001e") {
        listOf(pack(it.title), pack(it.expression), pack(it.explanation), it.verified).joinToString(",")
    }
    private fun decodePhysicsSteps(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(','); PhysicsSolutionStep(unpack(fields[0]), unpack(fields[1]), unpack(fields[2]), fields[3].toBooleanStrictOrNull() ?: false)
    }
    private fun encodeChemistryComponents(values: List<ChemistryFormulaComponent>) = values.joinToString("\u001e") {
        listOf(pack(it.symbol), it.atomCount, it.atomicMass, it.contribution).joinToString(",")
    }
    private fun decodeChemistryComponents(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(',')
        ChemistryFormulaComponent(unpack(fields[0]), fields[1].toInt(), fields[2].toDouble(), fields[3].toDouble())
    }
    private fun encodeChemistrySteps(values: List<ChemistrySolutionStep>) = values.joinToString("\u001e") {
        listOf(pack(it.expression), pack(it.explanation), it.verified).joinToString(",")
    }
    private fun decodeChemistrySteps(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(',')
        ChemistrySolutionStep(unpack(fields[0]), unpack(fields[1]), fields[2].toBooleanStrictOrNull() ?: false)
    }
    private fun encodeEnglishCorrections(values: List<EnglishCorrectionSuggestion>) = values.joinToString("\u001e") {
        listOf(
            pack(it.id), it.issueType.name, pack(it.originalText), pack(it.suggestedText),
            it.range.start, it.range.endExclusive, pack(it.explanation.orEmpty()),
            it.confidence?.toString().orEmpty(), pack(it.engineSource.orEmpty()), it.accepted,
        ).joinToString(",")
    }
    private fun decodeEnglishCorrections(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(',')
        EnglishCorrectionSuggestion(
            unpack(fields[0]), enumValueOrDefault(fields[1], EnglishIssueType.STYLE),
            unpack(fields[2]), unpack(fields[3]), SmartBoardTextRange(fields[4].toInt(), fields[5].toInt()),
            unpack(fields[6]).takeIf(String::isNotBlank), fields[7].toFloatOrNull(),
            unpack(fields[8]).takeIf(String::isNotBlank), fields[9].toBooleanStrictOrNull() ?: false,
        )
    }
    private fun encodePartOfSpeech(values: List<PartOfSpeechToken>) = values.joinToString("\u001e") {
        listOf(pack(it.token), pack(it.role), it.confidence?.toString().orEmpty()).joinToString(",")
    }
    private fun decodePartOfSpeech(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(','); PartOfSpeechToken(unpack(fields[0]), unpack(fields[1]), fields[2].toFloatOrNull())
    }
    private fun encodeReadability(value: EnglishReadabilityResult?) = value?.let {
        "${it.wordCount},${it.sentenceCount},${it.averageWordsPerSentence}"
    }.orEmpty()
    private fun decodeReadability(value: String): EnglishReadabilityResult? = value.takeIf(String::isNotBlank)?.split(',')?.let {
        EnglishReadabilityResult(it[0].toInt(), it[1].toInt(), it[2].toDouble())
    }
    private fun encodeVocabulary(values: List<EnglishVocabularyResult>) = values.joinToString("\u001e") {
        listOf(pack(it.word), pack(it.definition.orEmpty()), pack(it.synonyms.joinToString("\u001f"))).joinToString(",")
    }
    private fun decodeVocabulary(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(',')
        EnglishVocabularyResult(
            unpack(fields[0]), unpack(fields[1]).takeIf(String::isNotBlank),
            unpack(fields[2]).split('\u001f').filter(String::isNotBlank),
        )
    }
    private fun encodeBiologyLabels(values: List<BiologyConfirmedLabel>) = values.joinToString("\u001e") {
        listOf(pack(it.text), pack(it.structureId.orEmpty()), pack(it.function.orEmpty()), it.confirmedByUser).joinToString(",")
    }
    private fun decodeBiologyLabels(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(',')
        BiologyConfirmedLabel(
            unpack(fields[0]), unpack(fields[1]).takeIf(String::isNotBlank),
            unpack(fields[2]).takeIf(String::isNotBlank), fields[3].toBooleanStrictOrNull() ?: false,
        )
    }
    private fun encodeBiologyProcess(values: List<BiologyProcessStep>) = values.joinToString("\u001e") {
        "${it.order},${pack(it.text)}"
    }
    private fun decodeBiologyProcess(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(','); BiologyProcessStep(fields[0].toInt(), unpack(fields[1]))
    }
    private fun encodeGenetics(value: BiologyGeneticsResult?) = value?.let {
        listOf(pack(it.genotypeRatio.orEmpty()), pack(it.phenotypeRatio.orEmpty()), it.verified).joinToString(",")
    }.orEmpty()
    private fun decodeGenetics(value: String): BiologyGeneticsResult? = value.takeIf(String::isNotBlank)?.split(',')?.let {
        BiologyGeneticsResult(
            unpack(it[0]).takeIf(String::isNotBlank), unpack(it[1]).takeIf(String::isNotBlank),
            it[2].toBooleanStrictOrNull() ?: false,
        )
    }
    private fun encodeDiagramObjects(values: List<PhysicsDiagramObject>) = values.joinToString("\u001e") {
        listOf(pack(it.id), pack(it.kind), pack(it.label.orEmpty()), bounds(it.bounds), it.confidence?.toString().orEmpty()).joinToString(",")
    }
    private fun decodeDiagramObjects(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(','); PhysicsDiagramObject(unpack(fields[0]), unpack(fields[1]), unpack(fields[2]).takeIf(String::isNotBlank),
            parseBounds(fields.subList(3, 7).joinToString(",")), fields.getOrNull(7)?.toFloatOrNull())
    }
    private fun encodeDiagramRelations(values: List<PhysicsDiagramRelation>) = values.joinToString("\u001e") {
        listOf(pack(it.fromId), pack(it.toId), pack(it.relation)).joinToString(",")
    }
    private fun decodeDiagramRelations(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(','); PhysicsDiagramRelation(unpack(fields[0]), unpack(fields[1]), unpack(fields[2]))
    }
    private fun encodeDiagramInferences(values: List<PhysicsDiagramInference>) = values.joinToString("\u001e") {
        listOf(pack(it.description), it.confidence, it.requiresConfirmation).joinToString(",")
    }
    private fun decodeDiagramInferences(value: String) = value.split('\u001e').filter(String::isNotBlank).map {
        val fields = it.split(','); PhysicsDiagramInference(unpack(fields[0]), fields[1].toFloat(), fields[2].toBooleanStrictOrNull() ?: true)
    }

    private fun encodeSteps(steps: List<SolutionStep>) = steps.joinToString("\u001e") { step ->
        listOf(pack(step.id), pack(step.expression), pack(step.sourceStrokeIds.joinToString(",")), step.confidence?.toString().orEmpty(),
            step.status.name, pack(step.feedback.orEmpty())).joinToString(",")
    }

    private fun decodeSteps(value: String): List<SolutionStep> = value.split('\u001e').filter(String::isNotBlank).map { encoded ->
        val fields = encoded.split(',')
        SolutionStep(unpack(fields[0]), unpack(fields[1]), unpack(fields[2]).split(',').filter(String::isNotBlank),
            fields[3].toFloatOrNull(), enumValueOrDefault(fields[4], SolutionStepStatus.UNCHECKED), unpack(fields[5]))
    }

    private fun encodeTableRows(rows: List<List<String>>) = rows.joinToString("\u001e") { row ->
        "${row.size}:${row.joinToString("\u001f") { cell -> pack(cell) }}"
    }

    private fun decodeTableRows(value: String): List<List<String>> {
        if (value.isEmpty()) return emptyList()
        return value.split('\u001e').map { encodedRow ->
            val separator = encodedRow.indexOf(':')
            val declaredSize = encodedRow.take(separator.coerceAtLeast(0)).toIntOrNull()
            if (separator >= 0 && declaredSize != null) {
                val payload = encodedRow.substring(separator + 1)
                if (declaredSize == 0) emptyList()
                else payload.split('\u001f').take(declaredSize).map(::unpack) +
                    List((declaredSize - payload.split('\u001f').size).coerceAtLeast(0)) { "" }
            } else {
                encodedRow.split('\u001f').map(::unpack)
            }
        }
    }

    private fun bounds(value: SmartBoardBounds) = "${value.left},${value.top},${value.right},${value.bottom}"
    private fun parseBounds(value: String): SmartBoardBounds {
        val parts = value.split(',').map(String::toFloat)
        return SmartBoardBounds(parts[0], parts[1], parts[2], parts[3])
    }
    private fun pack(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unpack(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T) =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}

private val Context.smartBoardPreferencesDataStore by preferencesDataStore("smartboard_standalone_preferences")

private class SmartBoardDatabase(context: Context) : SQLiteOpenHelper(context, "smartboard-standalone.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE boards(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,subject TEXT NOT NULL,schema_version INTEGER NOT NULL,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE recovery(slot INTEGER PRIMARY KEY CHECK(slot=1),id TEXT NOT NULL,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE intelligence_sessions(board_id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE tutor_conversations(board_id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS intelligence_sessions(board_id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS tutor_conversations(board_id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
        }
    }
}

class SmartBoardRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val database = SmartBoardDatabase(applicationContext)

    suspend fun save(document: SmartBoardDocument) = withContext(Dispatchers.IO) {
        database.writableDatabase.insertWithOnConflict("boards", null, document.values(), SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun saveRecovery(document: SmartBoardDocument) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("slot", 1)
            put("id", document.id)
            put("payload", SmartBoardDocumentCodec.encode(document))
            put("updated_at", document.updatedAt)
        }
        database.writableDatabase.insertWithOnConflict("recovery", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun load(id: String): SmartBoardDocument? = withContext(Dispatchers.IO) {
        database.readableDatabase.query("boards", arrayOf("payload"), "id=?", arrayOf(id), null, null, null).use { cursor ->
            cursor.takeIf(Cursor::moveToFirst)?.getString(0)?.let { SmartBoardDocumentCodec.decode(it).document }
        }
    }

    suspend fun loadRecovery(): SmartBoardDocument? = withContext(Dispatchers.IO) {
        database.readableDatabase.query("recovery", arrayOf("payload"), "slot=1", null, null, null, null).use { cursor ->
            cursor.takeIf(Cursor::moveToFirst)?.getString(0)?.let { SmartBoardDocumentCodec.decode(it).document }
        }
    }

    suspend fun recent(limit: Int = 20): List<SmartBoardDocument> = withContext(Dispatchers.IO) {
        database.readableDatabase.query("boards", arrayOf("payload"), null, null, null, null, "updated_at DESC", limit.coerceIn(1, 100).toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) SmartBoardDocumentCodec.decode(cursor.getString(0)).document?.let(::add)
            }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("boards", "id=?", arrayOf(id))
        database.writableDatabase.delete("intelligence_sessions", "board_id=?", arrayOf(id))
        database.writableDatabase.delete("tutor_conversations", "board_id=?", arrayOf(id))
    }

    suspend fun saveIntelligenceMemory(memory: SmartBoardSessionMemory) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("board_id", memory.boardId)
            put("payload", SmartBoardSessionMemoryCodec.encode(memory))
            put("updated_at", memory.lastUpdatedAt)
        }
        database.writableDatabase.insertWithOnConflict("intelligence_sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun loadIntelligenceMemory(boardId: String): SmartBoardSessionMemory? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            "intelligence_sessions", arrayOf("payload"), "board_id=?", arrayOf(boardId), null, null, null,
        ).use { cursor -> cursor.takeIf(Cursor::moveToFirst)?.getString(0)?.let(SmartBoardSessionMemoryCodec::decode) }
    }

    suspend fun saveTutorConversation(value: SmartBoardTutorConversation) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("board_id", value.boardId)
            put("payload", SmartBoardTutorConversationCodec.encode(value))
            put("updated_at", value.updatedAt)
        }
        database.writableDatabase.insertWithOnConflict(
            "tutor_conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    suspend fun loadTutorConversation(boardId: String): SmartBoardTutorConversation? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            "tutor_conversations", arrayOf("payload"), "board_id=?", arrayOf(boardId), null, null, null,
        ).use { cursor ->
            cursor.takeIf(Cursor::moveToFirst)?.getString(0)?.let(SmartBoardTutorConversationCodec::decode)
        }
    }

    suspend fun referencedAssetIds(): Set<String> = withContext(Dispatchers.IO) {
        database.readableDatabase.query("boards", arrayOf("payload"), null, null, null, null, null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    SmartBoardDocumentCodec.decode(cursor.getString(0)).document?.elements
                        ?.filterIsInstance<ImageElement>()
                        ?.mapTo(this, ImageElement::assetId)
                }
            }
        }
    }

    suspend fun savePreferences(value: SmartBoardPreferences) {
        applicationContext.smartBoardPreferencesDataStore.edit { preferences ->
            preferences[inputModeKey] = value.inputMode.name
            preferences[pressureKey] = value.pressureSensitivity
            preferences[smoothingKey] = value.smoothingLevel
            preferences[contrastKey] = value.highContrast
            preferences[reducedMotionKey] = value.reducedMotion
            preferences[recognitionModeKey] = value.recognitionMode.name
            preferences[recognitionDefaultsVersionKey] = CurrentRecognitionDefaultsVersion
            preferences[autoShapeEnabledKey] = value.autoShapeEnabled
            preferences[autoShapeDelayKey] = value.autoShapeDelayMillis
            preferences[intelligenceModeKey] = value.intelligenceMode.name
            preferences[intelligenceSuggestionsKey] = value.intelligenceSuggestionsEnabled
            preferences[recognitionPersonalizationKey] = value.recognitionPersonalizationEnabled
            preferences[recognitionDiagnosticsKey] = value.recognitionDiagnosticsEnabled
            preferences[recognitionQualityTierKey] = value.recognitionQualityTier.name
        }
    }

    suspend fun loadPreferences(): SmartBoardPreferences {
        val preferences = applicationContext.smartBoardPreferencesDataStore.data.first()
        return SmartBoardPreferences(
            inputMode = enumValueOrDefault(preferences[inputModeKey].orEmpty(), SmartBoardInputMode.DRAW_WITH_FINGER),
            pressureSensitivity = preferences[pressureKey] ?: true,
            smoothingLevel = (preferences[smoothingKey] ?: 2).coerceIn(0, 4),
            highContrast = preferences[contrastKey] ?: false,
            reducedMotion = preferences[reducedMotionKey] ?: false,
            recognitionMode = when {
                (preferences[recognitionDefaultsVersionKey] ?: 0) < CurrentRecognitionDefaultsVersion &&
                    preferences[recognitionModeKey] == SmartBoardRecognitionMode.MANUAL_ONLY.name ->
                    SmartBoardRecognitionMode.SUGGEST_AFTER_PAUSE
                else -> enumValueOrDefault(
                    preferences[recognitionModeKey].orEmpty(),
                    SmartBoardRecognitionMode.SUGGEST_AFTER_PAUSE,
                )
            },
            autoShapeEnabled = preferences[autoShapeEnabledKey] ?: true,
            autoShapeDelayMillis = (preferences[autoShapeDelayKey] ?: 700).coerceIn(300, 3_000),
            intelligenceMode = enumValueOrDefault(preferences[intelligenceModeKey].orEmpty(), SmartBoardIntelligenceMode.ASSISTIVE),
            intelligenceSuggestionsEnabled = preferences[intelligenceSuggestionsKey] ?: true,
            recognitionPersonalizationEnabled = preferences[recognitionPersonalizationKey] ?: false,
            recognitionDiagnosticsEnabled = preferences[recognitionDiagnosticsKey] ?: false,
            recognitionQualityTier = enumValueOrDefault(preferences[recognitionQualityTierKey].orEmpty(), RecognitionQualityTier.BALANCED),
        )
    }

    suspend fun saveRecognitionPersonalization(encodedProfile: String) {
        require(encodedProfile.length <= 256_000)
        applicationContext.smartBoardPreferencesDataStore.edit { preferences ->
            preferences[recognitionProfileKey] = encodedProfile
        }
    }

    suspend fun loadRecognitionPersonalization(): String =
        applicationContext.smartBoardPreferencesDataStore.data.first()[recognitionProfileKey].orEmpty()

    suspend fun clearRecognitionPersonalization() {
        applicationContext.smartBoardPreferencesDataStore.edit { preferences ->
            preferences.remove(recognitionProfileKey)
        }
    }

    private fun SmartBoardDocument.values() = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("subject", subject.name)
        put("schema_version", schemaVersion)
        put("payload", SmartBoardDocumentCodec.encode(this@values))
        put("updated_at", updatedAt)
    }

    private companion object {
        val inputModeKey = stringPreferencesKey("input_mode")
        val pressureKey = booleanPreferencesKey("pressure")
        val smoothingKey = intPreferencesKey("smoothing")
        val contrastKey = booleanPreferencesKey("high_contrast")
        val reducedMotionKey = booleanPreferencesKey("reduced_motion")
        val recognitionModeKey = stringPreferencesKey("recognition_mode")
        val recognitionDefaultsVersionKey = intPreferencesKey("recognition_defaults_version")
        val autoShapeEnabledKey = booleanPreferencesKey("auto_shape_enabled")
        val autoShapeDelayKey = intPreferencesKey("auto_shape_delay")
        val intelligenceModeKey = stringPreferencesKey("intelligence_mode")
        val intelligenceSuggestionsKey = booleanPreferencesKey("intelligence_suggestions")
        val recognitionPersonalizationKey = booleanPreferencesKey("recognition_personalization")
        val recognitionDiagnosticsKey = booleanPreferencesKey("recognition_diagnostics")
        val recognitionQualityTierKey = stringPreferencesKey("recognition_quality_tier")
        val recognitionProfileKey = stringPreferencesKey("recognition_personalization_profile_v1")
        const val CurrentRecognitionDefaultsVersion = 1
        inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T) =
            runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
    }
}
