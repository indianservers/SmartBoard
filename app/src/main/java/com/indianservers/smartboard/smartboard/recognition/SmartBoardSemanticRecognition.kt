package com.indianservers.smartboard.smartboard.recognition

import com.indianservers.smartboard.core.SymbolicCasEngine
import com.indianservers.smartboard.core.SymbolicExpression
import com.indianservers.smartboard.core.TypedGraphExpressionParser
import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer
import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.SemanticExpressionTree
import com.indianservers.smartboard.smartboard.models.SemanticMathNode
import com.indianservers.smartboard.smartboard.models.SemanticMathNodeKind
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.TableElement
import java.util.Locale
import kotlin.math.hypot

object SmartBoardSemanticExpressionBuilder {
    private val relation = Regex("""(<=|>=|!=|=|<|>)""")

    fun build(
        latex: String,
        normalizedExpression: String? = null,
        sourceStrokeIds: List<String> = emptyList(),
        confidence: Float? = null,
    ): SemanticExpressionTree {
        val prepared = SmartBoardLatexAdapter.prepare(latex).getOrNull()
        val engine = normalizedExpression?.takeIf(String::isNotBlank)
            ?: prepared?.engineExpression
            ?: SmartBoardLatexAdapter.toEngineExpression(latex)
        val parsed = parseStructural(engine, sourceStrokeIds, confidence)
        val root = parsed ?: unknownNode(engine, sourceStrokeIds, confidence)
        return SemanticExpressionTree(
            root = root,
            authoredLatex = latex,
            engineExpression = engine,
            mathMl = mathMl(root),
            spokenForm = speak(root),
            parserVerified = parsed != null && (prepared?.analysis?.parserVerified != false),
            exactStrokeMapping = sourceStrokeIds.isEmpty(),
        )
    }

    private fun parseStructural(source: String, strokes: List<String>, confidence: Float?): SemanticMathNode? {
        val clean = source.trim()
        if (clean.isBlank()) return null
        parseMatrix(clean, strokes, confidence)?.let { return it }
        parsePiecewise(clean, strokes, confidence)?.let { return it }
        val relationMatch = topLevelRelation(clean)
        if (relationMatch != null) {
            val left = clean.substring(0, relationMatch.first).trim()
            val operator = relationMatch.second
            val right = clean.substring(relationMatch.first + operator.length).trim()
            if (left.isNotBlank() && right.isNotBlank()) {
                val kind = if (operator == "=") SemanticMathNodeKind.EQUATION else SemanticMathNodeKind.INEQUALITY
                val children = listOfNotNull(parseCas(left, "root.0", strokes, confidence), parseCas(right, "root.1", strokes, confidence))
                if (children.size == 2) return SemanticMathNode("root", kind, operator, children, strokes, confidence)
            }
        }
        return parseCas(clean, "root", strokes, confidence)
    }

    private fun parseCas(
        source: String,
        path: String,
        strokes: List<String>,
        confidence: Float?,
    ): SemanticMathNode? = runCatching { SymbolicCasEngine().parse(source) }.getOrNull()?.let {
        mapCas(it, path, strokes, confidence)
    }

    private fun mapCas(
        expression: SymbolicExpression,
        path: String,
        strokes: List<String>,
        confidence: Float?,
    ): SemanticMathNode {
        val childExpressions: List<SymbolicExpression>
        val kind: SemanticMathNodeKind
        val value: String?
        when (expression) {
            is SymbolicExpression.Number -> {
                kind = SemanticMathNodeKind.NUMBER; value = expression.value.toString(); childExpressions = emptyList()
            }
            is SymbolicExpression.Variable -> {
                kind = SemanticMathNodeKind.VARIABLE; value = expression.name; childExpressions = emptyList()
            }
            is SymbolicExpression.UnaryMinus -> {
                kind = SemanticMathNodeKind.NEGATION; value = null; childExpressions = listOf(expression.value)
            }
            is SymbolicExpression.Sum -> {
                kind = SemanticMathNodeKind.SUM; value = null; childExpressions = expression.terms
            }
            is SymbolicExpression.Product -> {
                kind = SemanticMathNodeKind.PRODUCT; value = null; childExpressions = expression.factors
            }
            is SymbolicExpression.Power -> {
                kind = SemanticMathNodeKind.POWER; value = null; childExpressions = listOf(expression.base, expression.exponent)
            }
            is SymbolicExpression.Function -> {
                kind = SemanticMathNodeKind.FUNCTION; value = expression.name; childExpressions = expression.arguments
            }
        }
        val children = childExpressions.mapIndexed { index, child ->
            mapCas(child, "$path.$index", approximateStrokes(strokes, index, childExpressions.size), confidence)
        }
        return SemanticMathNode(path, kind, value, children, strokes, confidence).withSpeech()
    }

    private fun parseMatrix(source: String, strokes: List<String>, confidence: Float?): SemanticMathNode? {
        if (!source.startsWith("[") || !source.endsWith("]")) return null
        val body = source.removeSurrounding("[", "]")
        val rows = splitTopLevel(body, ';').ifEmpty { return null }
        if (rows.size == 1 && ',' !in body) return null
        val rowNodes = rows.mapIndexed { rowIndex, row ->
            val cells = splitTopLevel(row.removeSurrounding("[", "]"), ',')
            SemanticMathNode(
                id = "root.$rowIndex",
                kind = SemanticMathNodeKind.MATRIX_ROW,
                children = cells.mapIndexed { columnIndex, cell ->
                    parseCas(cell, "root.$rowIndex.$columnIndex", approximateStrokes(strokes, columnIndex, cells.size), confidence)
                        ?: unknownNode(cell, emptyList(), confidence, "root.$rowIndex.$columnIndex")
                },
                sourceStrokeIds = approximateStrokes(strokes, rowIndex, rows.size),
                confidence = confidence,
            ).withSpeech()
        }
        if (rowNodes.isEmpty()) return null
        return SemanticMathNode("root", SemanticMathNodeKind.MATRIX, children = rowNodes, sourceStrokeIds = strokes, confidence = confidence).withSpeech()
    }

    private fun parsePiecewise(source: String, strokes: List<String>, confidence: Float?): SemanticMathNode? {
        val lower = source.lowercase(Locale.ROOT)
        if (!lower.startsWith("piecewise") && "\\begin{cases}" !in lower) return null
        val body = source.substringAfter('{', "").substringBeforeLast('}', "")
            .ifBlank { source.substringAfter("piecewise", "").removeSurrounding("(", ")") }
        val branches = splitTopLevel(body, ';')
        if (branches.isEmpty()) return null
        return SemanticMathNode(
            "root",
            SemanticMathNodeKind.PIECEWISE,
            children = branches.mapIndexed { index, branch ->
                val parts = splitTopLevel(branch, ',')
                SemanticMathNode(
                    "root.$index",
                    SemanticMathNodeKind.PIECEWISE_BRANCH,
                    children = parts.mapIndexed { partIndex, part ->
                        parseStructural(part, approximateStrokes(strokes, index, branches.size), confidence)
                            ?.repath("root.$index.$partIndex")
                            ?: unknownNode(part, emptyList(), confidence, "root.$index.$partIndex")
                    },
                    sourceStrokeIds = approximateStrokes(strokes, index, branches.size),
                    confidence = confidence,
                ).withSpeech()
            },
            sourceStrokeIds = strokes,
            confidence = confidence,
        ).withSpeech()
    }

    private fun topLevelRelation(source: String): Pair<Int, String>? {
        var depth = 0
        var index = 0
        while (index < source.length) {
            when (source[index]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth = (depth - 1).coerceAtLeast(0)
            }
            if (depth == 0) relation.find(source, index)?.takeIf { it.range.first == index }?.let {
                return index to it.value
            }
            index++
        }
        return null
    }

    private fun splitTopLevel(source: String, delimiter: Char): List<String> {
        var depth = 0
        var start = 0
        val result = mutableListOf<String>()
        source.forEachIndexed { index, char ->
            when (char) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth = (depth - 1).coerceAtLeast(0)
                delimiter -> if (depth == 0) {
                    source.substring(start, index).trim().takeIf(String::isNotBlank)?.let(result::add)
                    start = index + 1
                }
            }
        }
        source.substring(start).trim().takeIf(String::isNotBlank)?.let(result::add)
        return result
    }

    private fun approximateStrokes(strokes: List<String>, index: Int, count: Int): List<String> {
        if (strokes.isEmpty() || count <= 0) return emptyList()
        val start = index * strokes.size / count
        val end = ((index + 1) * strokes.size / count).coerceAtLeast(start + 1).coerceAtMost(strokes.size)
        return strokes.subList(start.coerceAtMost(strokes.lastIndex), end)
    }

    private fun unknownNode(
        source: String,
        strokes: List<String>,
        confidence: Float?,
        id: String = "root",
    ) = SemanticMathNode(id, SemanticMathNodeKind.UNKNOWN, source, sourceStrokeIds = strokes, confidence = confidence).withSpeech()

    private fun SemanticMathNode.repath(path: String): SemanticMathNode = copy(
        id = path,
        children = children.mapIndexed { index, child -> child.repath("$path.$index") },
    )

    private fun SemanticMathNode.withSpeech() = copy(spokenForm = speak(this))

    private fun speak(node: SemanticMathNode): String = when (node.kind) {
        SemanticMathNodeKind.NUMBER, SemanticMathNodeKind.VARIABLE, SemanticMathNodeKind.UNKNOWN -> node.value.orEmpty()
        SemanticMathNodeKind.NEGATION -> "negative ${node.children.firstOrNull()?.let(::speak).orEmpty()}"
        SemanticMathNodeKind.SUM -> node.children.joinToString(" plus ", transform = ::speak)
        SemanticMathNodeKind.PRODUCT -> node.children.joinToString(" times ", transform = ::speak)
        SemanticMathNodeKind.POWER -> "${node.children.getOrNull(0)?.let(::speak).orEmpty()} to the power of ${node.children.getOrNull(1)?.let(::speak).orEmpty()}"
        SemanticMathNodeKind.FUNCTION -> "${node.value.orEmpty()} of ${node.children.joinToString(", ", transform = ::speak)}"
        SemanticMathNodeKind.EQUATION -> "${node.children.getOrNull(0)?.let(::speak).orEmpty()} equals ${node.children.getOrNull(1)?.let(::speak).orEmpty()}"
        SemanticMathNodeKind.INEQUALITY -> "${node.children.getOrNull(0)?.let(::speak).orEmpty()} ${relationSpeech(node.value)} ${node.children.getOrNull(1)?.let(::speak).orEmpty()}"
        SemanticMathNodeKind.MATRIX -> "matrix with ${node.children.size} rows, ${node.children.joinToString("; ", transform = ::speak)}"
        SemanticMathNodeKind.MATRIX_ROW -> node.children.joinToString(", ", transform = ::speak)
        SemanticMathNodeKind.PIECEWISE -> "piecewise expression, ${node.children.joinToString("; ", transform = ::speak)}"
        SemanticMathNodeKind.PIECEWISE_BRANCH -> node.children.joinToString(" when ", transform = ::speak)
        SemanticMathNodeKind.VECTOR -> "vector ${node.children.joinToString(", ", transform = ::speak)}"
        SemanticMathNodeKind.COORDINATE -> "coordinate ${node.children.joinToString(", ", transform = ::speak)}"
    }

    private fun relationSpeech(value: String?) = when (value) {
        "<" -> "is less than"
        ">" -> "is greater than"
        "<=" -> "is less than or equal to"
        ">=" -> "is greater than or equal to"
        "!=" -> "is not equal to"
        else -> value.orEmpty()
    }

    private fun mathMl(node: SemanticMathNode): String = "<math>${mathMlNode(node)}</math>"

    private fun mathMlNode(node: SemanticMathNode): String = when (node.kind) {
        SemanticMathNodeKind.NUMBER -> "<mn>${xml(node.value)}</mn>"
        SemanticMathNodeKind.VARIABLE -> "<mi>${xml(node.value)}</mi>"
        SemanticMathNodeKind.UNKNOWN -> "<mtext>${xml(node.value)}</mtext>"
        SemanticMathNodeKind.NEGATION -> "<mrow><mo>-</mo>${node.children.firstOrNull()?.let(::mathMlNode).orEmpty()}</mrow>"
        SemanticMathNodeKind.SUM -> rowWithOperator(node.children, "+")
        SemanticMathNodeKind.PRODUCT -> rowWithOperator(node.children, "&#x2062;")
        SemanticMathNodeKind.POWER -> "<msup>${node.children.getOrNull(0)?.let(::mathMlNode).orEmpty()}${node.children.getOrNull(1)?.let(::mathMlNode).orEmpty()}</msup>"
        SemanticMathNodeKind.FUNCTION -> "<mrow><mi>${xml(node.value)}</mi><mo>(</mo>${rowWithOperator(node.children, ",")}<mo>)</mo></mrow>"
        SemanticMathNodeKind.EQUATION, SemanticMathNodeKind.INEQUALITY ->
            "<mrow>${node.children.getOrNull(0)?.let(::mathMlNode).orEmpty()}<mo>${xml(node.value)}</mo>${node.children.getOrNull(1)?.let(::mathMlNode).orEmpty()}</mrow>"
        SemanticMathNodeKind.MATRIX -> "<mtable>${node.children.joinToString("") { "<mtr>${it.children.joinToString("") { cell -> "<mtd>${mathMlNode(cell)}</mtd>" }}</mtr>" }}</mtable>"
        SemanticMathNodeKind.MATRIX_ROW -> rowWithOperator(node.children, ",")
        SemanticMathNodeKind.PIECEWISE -> "<mrow><mo>{</mo><mtable>${node.children.joinToString("") { "<mtr>${it.children.joinToString("") { cell -> "<mtd>${mathMlNode(cell)}</mtd>" }}</mtr>" }}</mtable></mrow>"
        SemanticMathNodeKind.PIECEWISE_BRANCH -> rowWithOperator(node.children, ",")
        SemanticMathNodeKind.VECTOR, SemanticMathNodeKind.COORDINATE -> "<mfenced>${rowWithOperator(node.children, ",")}</mfenced>"
    }

    private fun rowWithOperator(children: List<SemanticMathNode>, operator: String) =
        "<mrow>${children.joinToString("<mo>$operator</mo>", transform = ::mathMlNode)}</mrow>"

    private fun xml(value: String?) = value.orEmpty().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

enum class SmartBoardSpecialistKind {
    ALGEBRA_CALCULUS, MATRIX, GRAPH, GEOMETRY, TABLE_DATA, PHYSICS, CHEMISTRY, BIOLOGY, MIXED_TEXT,
}

data class SmartBoardSpecialistInterpretation(
    val specialist: SmartBoardSpecialistKind,
    val confidence: Float,
    val objectIntent: String,
    val supportedActions: List<String>,
    val evidence: List<String>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(confidence in 0f..1f)
        require(supportedActions.size <= 24 && evidence.size <= 24 && warnings.size <= 24)
    }
}

/**
 * Routes one recognition result to existing domain engines. It deliberately does not duplicate
 * CAS, graph, physics, chemistry, or biology evaluation.
 */
object SmartBoardSpecialistRecognitionRegistry {
    fun recognize(
        source: String,
        subject: SmartBoardSubject?,
        semanticTree: SemanticExpressionTree? = null,
        nearbyShapes: List<ShapeElement> = emptyList(),
    ): List<SmartBoardSpecialistInterpretation> = buildList {
        val analysis = runCatching { SmartBoardExpressionAnalyzer.analyze(semanticTree?.engineExpression ?: source) }.getOrNull()
        val lower = source.lowercase(Locale.ROOT)
        val graph = runCatching { TypedGraphExpressionParser.parse(semanticTree?.engineExpression ?: source) }.isSuccess
        if (semanticTree?.root?.kind == SemanticMathNodeKind.MATRIX || analysis?.type?.name == "MATRIX") add(
            interpretation(SmartBoardSpecialistKind.MATRIX, .96f, "Symbolic matrix", listOf("Row reduce", "Determinant", "Inverse", "Eigen analysis"), "Matrix structure parsed"),
        )
        if (analysis != null && analysis.parserVerified) add(
            interpretation(SmartBoardSpecialistKind.ALGEBRA_CALCULUS, .90f, analysis.type.name.lowercase().replace('_', ' '), analysis.actions.map { it.name.lowercase().replace('_', ' ') }, "Shared CAS parser verified"),
        )
        if (graph || nearbyShapes.any { it.shapeType in graphShapes }) add(
            interpretation(SmartBoardSpecialistKind.GRAPH, if (graph) .91f else .72f, "Graphable relation", listOf("Open graph editor", "Plot", "Inspect points"), if (graph) "Typed graph parser accepted expression" else "Nearby axes or grid"),
        )
        if (nearbyShapes.any { it.shapeType in geometryShapes }) add(
            interpretation(SmartBoardSpecialistKind.GEOMETRY, .84f, "Geometric construction", listOf("Open geometry", "Measure", "Add constraints"), "Recognized geometric primitives"),
        )
        if (nearbyShapes.any { it.shapeType in tableShapes } || source.count { it == ',' || it == '\t' } >= 2) add(
            interpretation(SmartBoardSpecialistKind.TABLE_DATA, .82f, "Structured table or dataset", listOf("Create table", "Calculate column", "Regression"), "Repeated cells or delimiters"),
        )
        if (subject == SmartBoardSubject.PHYSICS || unitPattern.containsMatchIn(source)) add(
            interpretation(SmartBoardSpecialistKind.PHYSICS, if (subject == SmartBoardSubject.PHYSICS) .96f else .76f, "Physics formula or diagram", listOf("Check units", "Solve", "Simulate"), "Physics subject or SI-unit evidence"),
        )
        if (subject == SmartBoardSubject.CHEMISTRY || chemistryPattern.containsMatchIn(source)) add(
            interpretation(SmartBoardSpecialistKind.CHEMISTRY, if (subject == SmartBoardSubject.CHEMISTRY) .96f else .74f, "Chemical notation", listOf("Normalize formula", "Balance", "Molar mass"), "Element/reaction notation"),
        )
        if (subject == SmartBoardSubject.BIOLOGY || biologyWords.any(lower::contains)) add(
            interpretation(SmartBoardSpecialistKind.BIOLOGY, if (subject == SmartBoardSubject.BIOLOGY) .96f else .70f, "Biology label or process", listOf("Label diagram", "Explain process", "Create study summary"), "Biology terminology"),
        )
        if (none { it.confidence >= .7f }) add(
            interpretation(SmartBoardSpecialistKind.MIXED_TEXT, .65f, "Mixed text and notation", listOf("Keep editable", "Correct text", "Reclassify"), "No single specialist dominates"),
        )
    }.distinctBy { it.specialist }.sortedByDescending { it.confidence }.take(4)

    private fun interpretation(
        kind: SmartBoardSpecialistKind,
        confidence: Float,
        intent: String,
        actions: List<String>,
        evidence: String,
    ) = SmartBoardSpecialistInterpretation(kind, confidence, intent, actions, listOf(evidence))

    private val graphShapes = setOf(SmartBoardShapeType.COORDINATE_AXES, SmartBoardShapeType.GRAPH_GRID, SmartBoardShapeType.NUMBER_LINE)
    private val geometryShapes = setOf(
        SmartBoardShapeType.LINE, SmartBoardShapeType.LINE_SEGMENT, SmartBoardShapeType.RAY,
        SmartBoardShapeType.CIRCLE, SmartBoardShapeType.ELLIPSE, SmartBoardShapeType.TRIANGLE,
        SmartBoardShapeType.RECTANGLE, SmartBoardShapeType.POLYGON, SmartBoardShapeType.ANGLE,
    )
    private val tableShapes = setOf(SmartBoardShapeType.TABLE_BOUNDARY, SmartBoardShapeType.GRAPH_GRID)
    private val unitPattern = Regex("""\b(?:m/s|m/s\^?2|kg|newton|joule|watt|volt|ampere|ohm|hz)\b""", RegexOption.IGNORE_CASE)
    private val chemistryPattern = Regex("""(?:[A-Z][a-z]?\d*){2,}|->|→""")
    private val biologyWords = listOf("cell", "mitosis", "dna", "photosynthesis", "organ", "enzyme", "gene")
}

enum class SmartBoardRegionRole {
    PROBLEM, DERIVATION, FORMULA, GRAPH, GEOMETRY_DIAGRAM, DATA_TABLE, LABEL, ANNOTATION, RESULT,
}

data class SmartBoardUnderstoodRegion(
    val id: String,
    val elementIds: List<String>,
    val role: SmartBoardRegionRole,
    val confidence: Float,
    val evidence: List<String>,
)

data class SmartBoardRelationshipSuggestion(
    val relationship: SmartBoardRelationship,
    val confidence: Float,
    val explanation: String,
)

data class SmartBoardWholeBoardUnderstanding(
    val regions: List<SmartBoardUnderstoodRegion>,
    val relationshipSuggestions: List<SmartBoardRelationshipSuggestion>,
    val summary: String,
    val warnings: List<String> = emptyList(),
)

object SmartBoardWholeBoardUnderstandingEngine {
    fun analyze(document: SmartBoardDocument, now: Long = System.currentTimeMillis()): SmartBoardWholeBoardUnderstanding {
        val visible = document.elements.filterNot(SmartBoardElement::hidden)
        val regions = visible.map { element ->
            SmartBoardUnderstoodRegion(
                id = "region-${element.id}",
                elementIds = listOf(element.id),
                role = role(element),
                confidence = .78f,
                evidence = listOf("Classified from persisted object type"),
            )
        }
        val existingKeys = document.relationships.map { it.type to it.elementIds.toSet() }.toSet()
        val suggestions = mutableListOf<SmartBoardRelationshipSuggestion>()
        visible.forEachIndexed { index, first ->
            visible.drop(index + 1).forEach { second ->
                val distance = hypot(
                    (first.bounds.center.x - second.bounds.center.x).toDouble(),
                    (first.bounds.center.y - second.bounds.center.y).toDouble(),
                ).toFloat()
                val threshold = maxOf(first.bounds.width, second.bounds.width, 80f) * 1.8f
                if (distance > threshold) return@forEach
                inferredRelationship(first, second)?.let { (type, explanation, confidence) ->
                    val ids = listOf(first.id, second.id)
                    if ((type to ids.toSet()) !in existingKeys) suggestions += SmartBoardRelationshipSuggestion(
                        SmartBoardRelationship("understood-${first.id}-${second.id}-${type.name}", type, ids, now),
                        confidence,
                        explanation,
                    )
                }
            }
        }
        val compact = suggestions.distinctBy { it.relationship.type to it.relationship.elementIds.toSet() }.take(64)
        return SmartBoardWholeBoardUnderstanding(
            regions = regions,
            relationshipSuggestions = compact,
            summary = "${regions.size} regions: ${regions.groupingBy { it.role }.eachCount().entries.joinToString { "${it.value} ${it.key.name.lowercase().replace('_', ' ')}" }}",
            warnings = if (compact.isEmpty()) listOf("No new high-confidence spatial relationships found.") else emptyList(),
        )
    }

    private fun role(element: SmartBoardElement): SmartBoardRegionRole = when (element) {
        is MathExpressionElement -> if (element.semanticTree?.root?.kind == SemanticMathNodeKind.EQUATION) SmartBoardRegionRole.FORMULA else SmartBoardRegionRole.DERIVATION
        is ShapeElement -> when (element.shapeType) {
            SmartBoardShapeType.COORDINATE_AXES, SmartBoardShapeType.GRAPH_GRID, SmartBoardShapeType.NUMBER_LINE -> SmartBoardRegionRole.GRAPH
            SmartBoardShapeType.TABLE_BOUNDARY -> SmartBoardRegionRole.DATA_TABLE
            else -> SmartBoardRegionRole.GEOMETRY_DIAGRAM
        }
        is TextElement -> SmartBoardRegionRole.ANNOTATION
        is TableElement -> SmartBoardRegionRole.DATA_TABLE
        else -> if (element.javaClass.simpleName.contains("Result")) SmartBoardRegionRole.RESULT else SmartBoardRegionRole.PROBLEM
    }

    private fun inferredRelationship(
        first: SmartBoardElement,
        second: SmartBoardElement,
    ): Triple<SmartBoardRelationshipType, String, Float>? = when {
        first is TextElement && second is ShapeElement || second is TextElement && first is ShapeElement ->
            Triple(SmartBoardRelationshipType.LABELS, "Nearby text likely labels the diagram object.", .82f)
        first is MathExpressionElement && second is ShapeElement || second is MathExpressionElement && first is ShapeElement ->
            Triple(SmartBoardRelationshipType.REPRESENTS, "Nearby expression likely represents the graph or construction.", .79f)
        first is MathExpressionElement && second is MathExpressionElement ->
            Triple(SmartBoardRelationshipType.DERIVED_FROM, "Vertically adjacent expressions may be consecutive working steps.", .72f)
        role(first) == SmartBoardRegionRole.DATA_TABLE || role(second) == SmartBoardRegionRole.DATA_TABLE ->
            Triple(SmartBoardRelationshipType.USES_DATA, "Nearby content appears to use this table or dataset.", .74f)
        else -> null
    }
}
