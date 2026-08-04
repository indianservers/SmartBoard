package com.indianservers.smartboard.smartboard.intelligence

import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionElement
import com.indianservers.smartboard.smartboard.models.EnglishTextElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramElement
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.models.TextElement
import kotlin.math.abs
import kotlin.math.hypot

enum class SemanticCanvasNodeKind {
    FORMULA, DIAGRAM, LABEL, TABLE, GRAPH, EXPLANATION, SHAPE, FORCE, AXIS, CIRCUIT_COMPONENT, BIOLOGY_STRUCTURE, INK,
}

enum class SemanticCanvasRelationKind {
    LABELS, FORCE_ON, CONTROLS_GRAPH, DERIVED_FROM, EXPLAINS, SUPPLIES_DATA, PART_OF, RELATED, CROSS_PAGE,
}

data class SemanticCanvasNode(
    val id: String,
    val boardId: String,
    val elementIds: Set<String>,
    val kind: SemanticCanvasNodeKind,
    val name: String,
    val proposedNames: List<String>,
    val searchableText: String,
    val tags: Set<String>,
    val bounds: SmartBoardBounds,
    val confidence: Float,
)

data class SemanticCanvasEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val kind: SemanticCanvasRelationKind,
    val confidence: Float,
    val explanation: String,
)

data class SemanticCanvasSearchResult(
    val boardId: String,
    val boardTitle: String,
    val nodeId: String,
    val elementIds: Set<String>,
    val title: String,
    val context: String,
    val score: Float,
)

data class SemanticSnapResult(
    val delta: SmartBoardPoint,
    val snapped: Boolean,
    val rationale: String?,
)

data class SemanticCanvasSnapshot(
    val nodes: List<SemanticCanvasNode>,
    val edges: List<SemanticCanvasEdge>,
    val pageCount: Int,
    val createdAt: Long,
) {
    companion object {
        val Empty = SemanticCanvasSnapshot(emptyList(), emptyList(), 0, 0L)
    }
}

class SmartBoardSemanticCanvasEngine {
    fun analyze(active: SmartBoardDocument, pages: List<SmartBoardDocument>, now: Long): SemanticCanvasSnapshot {
        val documents = (listOf(active) + pages).distinctBy(SmartBoardDocument::id).take(100)
        val nodes = documents.flatMap(::nodesFor)
        val byBoard = nodes.groupBy(SemanticCanvasNode::boardId)
        val edges = buildList {
            documents.forEach { board ->
                addAll(inferPageEdges(board, byBoard[board.id].orEmpty()))
            }
            addAll(inferCrossPageEdges(documents, byBoard))
        }.distinctBy { "${it.fromNodeId}|${it.toNodeId}|${it.kind}" }
        return SemanticCanvasSnapshot(nodes, edges, documents.size, now)
    }

    fun search(snapshot: SemanticCanvasSnapshot, pages: List<SmartBoardDocument>, query: String): List<SemanticCanvasSearchResult> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        val terms = normalized.split(' ').filter { it.length > 1 }.toSet()
        val wantsQuadratic = "quadratic" in terms
        val wantsTriangle = terms.any { it.startsWith("triangle") }
        val pageTitles = pages.associate { it.id to it.title }
        return snapshot.nodes.mapNotNull { node ->
            val haystack = normalize("${node.name} ${node.searchableText} ${node.tags.joinToString(" ")}")
            val matches = terms.count { it in haystack }
            val semanticBoost = when {
                wantsQuadratic && ("quadratic" in node.tags || Regex("""x\^?2|x²""").containsMatchIn(haystack)) -> .55f
                wantsTriangle && "triangle" in node.tags -> .55f
                else -> 0f
            }
            val score = (matches.toFloat() / terms.size.coerceAtLeast(1) + semanticBoost).coerceAtMost(1f)
            if (score < .25f) null else SemanticCanvasSearchResult(
                boardId = node.boardId,
                boardTitle = pageTitles[node.boardId] ?: "Board",
                nodeId = node.id,
                elementIds = node.elementIds,
                title = node.name,
                context = node.searchableText.take(120),
                score = score,
            )
        }.sortedByDescending(SemanticCanvasSearchResult::score).take(50)
    }

    fun selectByMeaning(
        snapshot: SemanticCanvasSnapshot,
        activeBoardId: String,
        query: String,
        currentSelection: Set<String>,
    ): Set<String> {
        val normalized = normalize(query)
        val local = snapshot.nodes.filter { it.boardId == activeBoardId }
        val selectedNodes = when {
            "denominator" in normalized -> local.filter { "denominator" in it.tags }
            "force" in normalized -> local.filter { it.kind == SemanticCanvasNodeKind.FORCE || "force" in it.tags }
            "triangle" in normalized -> local.filter { "triangle" in it.tags }
            "axis" in normalized || "axes" in normalized -> local.filter { it.kind == SemanticCanvasNodeKind.AXIS }
            "circuit" in normalized -> local.filter {
                it.kind == SemanticCanvasNodeKind.CIRCUIT_COMPONENT || "circuit" in it.tags
            }
            "equation" in normalized && "graph" in normalized -> {
                val seeds = local.filter { node -> node.elementIds.any(currentSelection::contains) }
                    .ifEmpty { local.filter { it.kind == SemanticCanvasNodeKind.FORMULA }.takeLast(1) }
                val ids = connectedNodeIds(snapshot, seeds.mapTo(linkedSetOf(), SemanticCanvasNode::id)) {
                    it == SemanticCanvasRelationKind.CONTROLS_GRAPH
                }
                local.filter { it.id in ids }
            }
            else -> {
                val terms = normalized.split(' ').filter { it.length > 1 }
                local.filter { node ->
                    val text = normalize("${node.name} ${node.searchableText} ${node.tags.joinToString(" ")}")
                    terms.isNotEmpty() && terms.all(text::contains)
                }
            }
        }
        return selectedNodes.flatMapTo(linkedSetOf()) { it.elementIds }
    }

    fun semanticLasso(
        snapshot: SemanticCanvasSnapshot,
        activeBoardId: String,
        geometricallySelectedIds: Set<String>,
        lassoBounds: SmartBoardBounds,
    ): Set<String> {
        val local = snapshot.nodes.filter { it.boardId == activeBoardId }
        val seeds = local.filter { node ->
            node.elementIds.any(geometricallySelectedIds::contains) ||
                (node.kind != SemanticCanvasNodeKind.INK && node.bounds.center.let(lassoBounds::contains))
        }
        if (seeds.isEmpty()) return geometricallySelectedIds
        val meaningful = seeds.filter { it.kind != SemanticCanvasNodeKind.INK }
        val root = meaningful.ifEmpty { seeds }
        val connected = connectedNodeIds(snapshot, root.mapTo(linkedSetOf(), SemanticCanvasNode::id)) {
            it in setOf(
                SemanticCanvasRelationKind.LABELS,
                SemanticCanvasRelationKind.FORCE_ON,
                SemanticCanvasRelationKind.CONTROLS_GRAPH,
                SemanticCanvasRelationKind.PART_OF,
                SemanticCanvasRelationKind.SUPPLIES_DATA,
            )
        }
        return local.filter { node ->
            node.id in connected &&
                (node.bounds.intersects(lassoBounds.expand(36f)) || node.id in root.map(SemanticCanvasNode::id))
        }.flatMapTo(linkedSetOf()) { it.elementIds }
    }

    fun snap(
        snapshot: SemanticCanvasSnapshot,
        activeBoardId: String,
        selectedIds: Set<String>,
        requested: SmartBoardPoint,
        threshold: Float = 10f,
    ): SemanticSnapResult {
        val local = snapshot.nodes.filter { it.boardId == activeBoardId }
        val moving = local.filter { it.elementIds.any(selectedIds::contains) }
        val stationary = local.filterNot { it.elementIds.any(selectedIds::contains) }
        if (moving.isEmpty() || stationary.isEmpty()) return SemanticSnapResult(requested, false, null)
        val source = unionBounds(moving.map(SemanticCanvasNode::bounds)).translate(requested)
        var xCorrection = 0f
        var yCorrection = 0f
        var xDistance = threshold + 1f
        var yDistance = threshold + 1f
        var reason: String? = null
        stationary.forEach { target ->
            val targetX = when {
                moving.any { it.kind == SemanticCanvasNodeKind.FORMULA } && target.kind == SemanticCanvasNodeKind.FORMULA ->
                    listOf(target.bounds.left)
                moving.any { it.kind == SemanticCanvasNodeKind.CIRCUIT_COMPONENT } ->
                    listOf(target.bounds.left, target.bounds.right, target.bounds.center.x)
                else -> listOf(target.bounds.left, target.bounds.center.x, target.bounds.right)
            }
            val sourceX = if (moving.any { it.kind == SemanticCanvasNodeKind.FORMULA }) {
                listOf(source.left)
            } else listOf(source.left, source.center.x, source.right)
            sourceX.forEach { from -> targetX.forEach { to ->
                val distance = abs(to - from)
                if (distance < xDistance && distance <= threshold) {
                    xDistance = distance
                    xCorrection = to - from
                    reason = contextualSnapName(moving.first(), target)
                }
            } }
            val sourceY = listOf(source.top, source.center.y, source.bottom)
            val targetY = listOf(target.bounds.top, target.bounds.center.y, target.bounds.bottom)
            sourceY.forEach { from -> targetY.forEach { to ->
                val distance = abs(to - from)
                if (distance < yDistance && distance <= threshold) {
                    yDistance = distance
                    yCorrection = to - from
                    reason = reason ?: contextualSnapName(moving.first(), target)
                }
            } }
        }
        val snapped = xDistance <= threshold || yDistance <= threshold
        return SemanticSnapResult(
            SmartBoardPoint(requested.x + xCorrection, requested.y + yCorrection),
            snapped,
            reason,
        )
    }

    private fun nodesFor(board: SmartBoardDocument): List<SemanticCanvasNode> = board.elements
        .filterNot(SmartBoardElement::hidden)
        .map { element ->
            val content = contentOf(element)
            val kind = kindOf(element)
            val tags = tagsOf(element, content)
            SemanticCanvasNode(
                id = "${board.id}:${element.id}",
                boardId = board.id,
                elementIds = sourceIds(element) + element.id,
                kind = kind,
                name = nameOf(element, content),
                proposedNames = proposedNames(element, content),
                searchableText = content,
                tags = tags,
                bounds = element.bounds,
                confidence = confidenceOf(element),
            )
        }

    private fun inferPageEdges(board: SmartBoardDocument, nodes: List<SemanticCanvasNode>): List<SemanticCanvasEdge> {
        val byElement = nodes.flatMap { node -> node.elementIds.map { it to node } }.toMap()
        return buildList {
            board.relationships.forEach { relationship ->
                val related = relationship.elementIds.mapNotNull(byElement::get).distinctBy(SemanticCanvasNode::id)
                related.zipWithNext().forEach { (from, to) ->
                    add(edge(from, to, relationship.type.toSemanticKind(), .98f, "Saved board relationship"))
                }
            }
            val labels = nodes.filter { it.kind == SemanticCanvasNodeKind.LABEL }
            val labelTargets = nodes.filter { it.kind in setOf(
                SemanticCanvasNodeKind.DIAGRAM, SemanticCanvasNodeKind.SHAPE, SemanticCanvasNodeKind.AXIS,
                SemanticCanvasNodeKind.CIRCUIT_COMPONENT, SemanticCanvasNodeKind.BIOLOGY_STRUCTURE,
            ) }
            labels.forEach { label ->
                labelTargets.minByOrNull { distance(label.bounds, it.bounds) }?.let { target ->
                    val distance = distance(label.bounds, target.bounds)
                    if (distance <= 150f) add(edge(label, target, SemanticCanvasRelationKind.LABELS,
                        (1f - distance / 220f).coerceIn(.55f, .94f), "Nearby text labels ${target.name}"))
                }
            }
            nodes.filter { it.kind == SemanticCanvasNodeKind.FORCE }.forEach { force ->
                nodes.filter { it.id != force.id && it.kind in setOf(SemanticCanvasNodeKind.SHAPE, SemanticCanvasNodeKind.DIAGRAM) }
                    .minByOrNull { distance(force.bounds, it.bounds) }?.let { target ->
                        if (distance(force.bounds, target.bounds) <= 180f) {
                            add(edge(force, target, SemanticCanvasRelationKind.FORCE_ON, .82f, "Arrow indicates force on ${target.name}"))
                        }
                    }
            }
            nodes.filter { it.kind == SemanticCanvasNodeKind.GRAPH }.forEach { graph ->
                nodes.filter { it.kind == SemanticCanvasNodeKind.FORMULA }
                    .maxByOrNull { expressionSimilarity(it.searchableText, graph.searchableText) }?.let { formula ->
                        val similarity = expressionSimilarity(formula.searchableText, graph.searchableText)
                        if (similarity >= .35f || formula.elementIds.any(graph.elementIds::contains)) {
                            add(edge(formula, graph, SemanticCanvasRelationKind.CONTROLS_GRAPH,
                                maxOf(.72f, similarity), "Equation controls editable graph"))
                        }
                    }
            }
            nodes.filter { it.kind == SemanticCanvasNodeKind.TABLE }.forEach { table ->
                nodes.filter { it.kind == SemanticCanvasNodeKind.GRAPH && distance(table.bounds, it.bounds) < 220f }
                    .forEach { graph -> add(edge(table, graph, SemanticCanvasRelationKind.SUPPLIES_DATA, .68f, "Table is plausible graph data")) }
            }
        }
    }

    private fun inferCrossPageEdges(
        documents: List<SmartBoardDocument>,
        byBoard: Map<String, List<SemanticCanvasNode>>,
    ): List<SemanticCanvasEdge> = buildList {
        documents.indices.forEach { leftIndex ->
            for (rightIndex in leftIndex + 1 until documents.size) {
                val left = byBoard[documents[leftIndex].id].orEmpty()
                val right = byBoard[documents[rightIndex].id].orEmpty()
                left.filter { it.kind in setOf(SemanticCanvasNodeKind.FORMULA, SemanticCanvasNodeKind.GRAPH, SemanticCanvasNodeKind.DIAGRAM) }
                    .forEach { source ->
                        right.filter { it.kind in setOf(SemanticCanvasNodeKind.FORMULA, SemanticCanvasNodeKind.GRAPH, SemanticCanvasNodeKind.DIAGRAM) }
                            .maxByOrNull { expressionSimilarity(source.searchableText, it.searchableText) }?.let { target ->
                                val score = expressionSimilarity(source.searchableText, target.searchableText)
                                if (score >= .58f) add(edge(source, target, SemanticCanvasRelationKind.CROSS_PAGE, score,
                                    "Related content on ${documents[rightIndex].title}"))
                            }
                    }
            }
        }
    }

    private fun kindOf(element: SmartBoardElement) = when (element) {
        is MathExpressionElement, is PhysicsExpressionElement, is ChemistryExpressionElement -> SemanticCanvasNodeKind.FORMULA
        is GraphConfigurationElement -> SemanticCanvasNodeKind.GRAPH
        is TableElement -> SemanticCanvasNodeKind.TABLE
        is PhysicsDiagramElement -> SemanticCanvasNodeKind.DIAGRAM
        is BiologyContentElement -> if (element.detectedLabels.isNotEmpty()) SemanticCanvasNodeKind.BIOLOGY_STRUCTURE else SemanticCanvasNodeKind.EXPLANATION
        is EnglishTextElement, is TextElement -> if (element.bounds.width < 220f && contentOf(element).length < 48) SemanticCanvasNodeKind.LABEL else SemanticCanvasNodeKind.EXPLANATION
        is ShapeElement -> when (element.shapeType) {
            SmartBoardShapeType.FORCE_ARROW, SmartBoardShapeType.VECTOR_ARROW -> SemanticCanvasNodeKind.FORCE
            SmartBoardShapeType.COORDINATE_AXES, SmartBoardShapeType.NUMBER_LINE, SmartBoardShapeType.GRAPH_GRID -> SemanticCanvasNodeKind.AXIS
            SmartBoardShapeType.RESISTOR, SmartBoardShapeType.CIRCUIT_WIRE, SmartBoardShapeType.NODE -> SemanticCanvasNodeKind.CIRCUIT_COMPONENT
            else -> SemanticCanvasNodeKind.SHAPE
        }
        else -> SemanticCanvasNodeKind.INK
    }

    private fun contentOf(element: SmartBoardElement): String = when (element) {
        is MathExpressionElement -> element.displayLatex
        is PhysicsExpressionElement -> element.displaySource
        is ChemistryExpressionElement -> element.normalizedChemicalNotation ?: element.rawText
        is GraphConfigurationElement -> element.expressions.joinToString(" ")
        is TableElement -> (element.columnHeaders + element.rows.flatten()).joinToString(" ")
        is TextElement -> element.text
        is EnglishTextElement -> element.correctedText ?: element.rawText
        is BiologyContentElement -> listOfNotNull(element.recognizedText).plus(element.detectedLabels.map { it.text }).joinToString(" ")
        is PhysicsDiagramElement -> "${element.diagramType.name} ${element.detectedObjects.joinToString(" ") { "${it.kind} ${it.label.orEmpty()}" }}"
        is ShapeElement -> element.shapeType.name
        else -> ""
    }

    private fun tagsOf(element: SmartBoardElement, content: String): Set<String> = buildSet {
        add(kindOf(element).name.lowercase())
        val normalized = normalize(content)
        addAll(normalized.split(' ').filter { it.length > 1 }.take(64))
        if (element is MathExpressionElement || element is PhysicsExpressionElement) {
            add("equation")
            if ('/' in content || "\\frac" in content) add("denominator")
            if (
                Regex("""x\s*\^?\s*2|x²""", RegexOption.IGNORE_CASE).containsMatchIn(content) ||
                Regex("""b\s*\^?\s*2\s*-\s*4\s*a\s*c""", RegexOption.IGNORE_CASE).containsMatchIn(content)
            ) add("quadratic")
        }
        if (element is ShapeElement) {
            if ("TRIANGLE" in element.shapeType.name) add("triangle")
            if (element.shapeType in setOf(SmartBoardShapeType.FORCE_ARROW, SmartBoardShapeType.VECTOR_ARROW)) add("force")
            if (element.shapeType in setOf(SmartBoardShapeType.RESISTOR, SmartBoardShapeType.CIRCUIT_WIRE, SmartBoardShapeType.NODE)) add("circuit")
        }
    }

    private fun nameOf(element: SmartBoardElement, content: String): String = when (element) {
        is MathExpressionElement -> if ("quadratic" in tagsOf(element, content)) "Quadratic equation" else "Equation ${content.take(34)}"
        is GraphConfigurationElement -> if (element.graphKind.name.endsWith("3D")) "3D graph" else "2D graph"
        is TableElement -> "Table: ${element.columnHeaders.joinToString().take(42)}"
        is ShapeElement -> element.shapeType.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
        is PhysicsDiagramElement -> "${element.diagramType.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)} diagram"
        else -> content.take(48).ifBlank { kindOf(element).name.lowercase().replaceFirstChar(Char::titlecase) }
    }

    private fun proposedNames(element: SmartBoardElement, content: String): List<String> = when (element) {
        is ShapeElement -> when (element.shapeType) {
            SmartBoardShapeType.COORDINATE_AXES -> listOf("x-axis and y-axis", "coordinate plane")
            SmartBoardShapeType.FORCE_ARROW -> listOf("force F", "weight mg", "normal force N")
            SmartBoardShapeType.VECTOR_ARROW -> listOf("vector v", "displacement", "velocity")
            SmartBoardShapeType.RESISTOR -> listOf("resistor R", "load")
            SmartBoardShapeType.NODE -> listOf("junction", "point A")
            SmartBoardShapeType.TRIANGLE, SmartBoardShapeType.RIGHT_TRIANGLE, SmartBoardShapeType.EQUILATERAL_TRIANGLE ->
                listOf("triangle ABC", "geometric triangle")
            else -> listOf(nameOf(element, content))
        }
        is PhysicsDiagramElement -> element.detectedObjects.mapNotNull { it.label ?: it.kind }.distinct().take(5)
        is BiologyContentElement -> element.detectedLabels.map { it.text }.take(5)
        is GraphConfigurationElement -> listOf("x-axis", "y-axis", "graph of ${element.expressions.firstOrNull().orEmpty()}")
        else -> emptyList()
    }

    private fun sourceIds(element: SmartBoardElement): Set<String> = when (element) {
        is MathExpressionElement -> element.sourceStrokeIds.toSet()
        is PhysicsExpressionElement -> element.sourceStrokeIds.toSet()
        is ChemistryExpressionElement -> element.sourceStrokeIds.toSet()
        is EnglishTextElement -> element.sourceStrokeIds.toSet()
        is BiologyContentElement -> element.sourceStrokeIds.toSet()
        is PhysicsDiagramElement -> element.sourceStrokeIds.toSet()
        is ShapeElement -> element.sourceStrokeIds.toSet()
        is GraphConfigurationElement -> element.sourceElementIds.toSet()
        is TableElement -> element.sourceElementIds.toSet()
        else -> emptySet()
    }

    private fun confidenceOf(element: SmartBoardElement): Float = when (element) {
        is MathExpressionElement -> element.recognitionConfidence ?: .72f
        is PhysicsExpressionElement -> element.recognitionConfidence ?: .72f
        is PhysicsDiagramElement -> element.confidence ?: .68f
        is ShapeElement -> element.recognitionConfidence
        else -> .8f
    }.coerceIn(0f, 1f)

    private fun edge(
        from: SemanticCanvasNode,
        to: SemanticCanvasNode,
        kind: SemanticCanvasRelationKind,
        confidence: Float,
        explanation: String,
    ) = SemanticCanvasEdge(
        id = "${kind.name}:${from.id}:${to.id}",
        fromNodeId = from.id,
        toNodeId = to.id,
        kind = kind,
        confidence = confidence.coerceIn(0f, 1f),
        explanation = explanation,
    )

    private fun connectedNodeIds(
        snapshot: SemanticCanvasSnapshot,
        seed: Set<String>,
        relationAllowed: (SemanticCanvasRelationKind) -> Boolean,
    ): Set<String> {
        var result = seed
        var changed: Boolean
        do {
            val next = snapshot.edges.filter { relationAllowed(it.kind) && (it.fromNodeId in result || it.toNodeId in result) }
                .flatMapTo(linkedSetOf()) { listOf(it.fromNodeId, it.toNodeId) } + result
            changed = next.size != result.size
            result = next
        } while (changed)
        return result
    }

    private fun expressionSimilarity(left: String, right: String): Float {
        val a = expressionTokens(left)
        val b = expressionTokens(right)
        if (a.isEmpty() || b.isEmpty()) return 0f
        return (a intersect b).size.toFloat() / (a union b).size
    }

    private fun expressionTokens(value: String): Set<String> =
        normalize(value.replace("\\", " ")).split(' ').filter { it.isNotBlank() }.toSet()

    private fun distance(a: SmartBoardBounds, b: SmartBoardBounds): Float =
        hypot((a.center.x - b.center.x).toDouble(), (a.center.y - b.center.y).toDouble()).toFloat()

    private fun unionBounds(bounds: List<SmartBoardBounds>) = SmartBoardBounds(
        bounds.minOf { it.left }, bounds.minOf { it.top }, bounds.maxOf { it.right }, bounds.maxOf { it.bottom },
    )

    private fun contextualSnapName(source: SemanticCanvasNode, target: SemanticCanvasNode): String = when {
        source.kind == SemanticCanvasNodeKind.FORMULA && target.kind == SemanticCanvasNodeKind.FORMULA -> "Equation columns aligned"
        source.kind == SemanticCanvasNodeKind.LABEL -> "Diagram label snapped to ${target.name}"
        source.kind == SemanticCanvasNodeKind.CIRCUIT_COMPONENT -> "Circuit connection aligned"
        source.kind == SemanticCanvasNodeKind.AXIS -> "Graph axes aligned"
        source.kind == SemanticCanvasNodeKind.TABLE -> "Table columns aligned"
        else -> "Aligned with ${target.name}"
    }

    private fun SmartBoardRelationshipType.toSemanticKind() = when (this) {
        SmartBoardRelationshipType.LABELS -> SemanticCanvasRelationKind.LABELS
        SmartBoardRelationshipType.DERIVED_FROM, SmartBoardRelationshipType.RECOGNIZED_FROM -> SemanticCanvasRelationKind.DERIVED_FROM
        SmartBoardRelationshipType.EXPLAINS, SmartBoardRelationshipType.DESCRIBES -> SemanticCanvasRelationKind.EXPLAINS
        SmartBoardRelationshipType.USES_DATA -> SemanticCanvasRelationKind.SUPPLIES_DATA
        SmartBoardRelationshipType.PART_OF_DIAGRAM, SmartBoardRelationshipType.PART_OF_PROBLEM -> SemanticCanvasRelationKind.PART_OF
        else -> SemanticCanvasRelationKind.RELATED
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("""[^a-z0-9²+\-*/=]+"""), " ")
        .trim()
}
