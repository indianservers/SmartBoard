package com.indianservers.smartboard.smartboard.tools

import com.indianservers.smartboard.core.SymbolicCasEngine
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.SemanticExpressionTree
import com.indianservers.smartboard.smartboard.models.SemanticMathNode
import com.indianservers.smartboard.smartboard.models.SemanticMathNodeKind
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSemanticExpressionBuilder
import java.util.UUID

enum class SemanticToolOperation { SIMPLIFY, EXPAND, FACTOR, NEGATE }

enum class SemanticComponentRole {
    EXPRESSION, LEFT_SIDE, RIGHT_SIDE, NUMERATOR, DENOMINATOR, EXPONENT, BASE,
    MATRIX_ROW, MATRIX_CELL, TERM, FACTOR, FUNCTION_ARGUMENT, PIECEWISE_BRANCH, VARIABLE, NUMBER,
}

data class SemanticToolTarget(
    val nodeId: String,
    val expression: String,
    val spokenForm: String,
    val depth: Int,
    val role: SemanticComponentRole = SemanticComponentRole.EXPRESSION,
)

data class SemanticToolResult(
    val operation: SemanticToolOperation,
    val targetBefore: String,
    val targetAfter: String,
    val expressionAfter: String,
    val tree: SemanticExpressionTree,
)

/**
 * Direct subexpression editing. The shared CAS performs algebra; this class only replaces the
 * selected semantic node and rebuilds the persisted tree.
 */
object SmartBoardSemanticToolEngine {
    fun targets(tree: SemanticExpressionTree, maximum: Int = 40): List<SemanticToolTarget> {
        require(maximum in 1..256)
        val result = mutableListOf<SemanticToolTarget>()
        fun visit(
            node: SemanticMathNode,
            depth: Int,
            role: SemanticComponentRole,
        ) {
            if (result.size >= maximum) return
            result += SemanticToolTarget(node.id, render(node), node.spokenForm, depth, role)
            val denominatorProduct = node.kind == SemanticMathNodeKind.PRODUCT &&
                node.children.any(::isReciprocal)
            node.children.forEachIndexed { index, child ->
                val childRole = when {
                    node.kind in setOf(SemanticMathNodeKind.EQUATION, SemanticMathNodeKind.INEQUALITY) && index == 0 ->
                        SemanticComponentRole.LEFT_SIDE
                    node.kind in setOf(SemanticMathNodeKind.EQUATION, SemanticMathNodeKind.INEQUALITY) ->
                        SemanticComponentRole.RIGHT_SIDE
                    node.kind == SemanticMathNodeKind.POWER && index == 0 && isReciprocal(node) ->
                        SemanticComponentRole.DENOMINATOR
                    node.kind == SemanticMathNodeKind.POWER && index == 0 -> SemanticComponentRole.BASE
                    node.kind == SemanticMathNodeKind.POWER && index == 1 -> SemanticComponentRole.EXPONENT
                    node.kind == SemanticMathNodeKind.MATRIX -> SemanticComponentRole.MATRIX_ROW
                    node.kind == SemanticMathNodeKind.MATRIX_ROW -> SemanticComponentRole.MATRIX_CELL
                    node.kind == SemanticMathNodeKind.SUM -> SemanticComponentRole.TERM
                    node.kind == SemanticMathNodeKind.PRODUCT && isReciprocal(child) -> SemanticComponentRole.DENOMINATOR
                    node.kind == SemanticMathNodeKind.PRODUCT && denominatorProduct -> SemanticComponentRole.NUMERATOR
                    node.kind == SemanticMathNodeKind.PRODUCT -> SemanticComponentRole.FACTOR
                    node.kind == SemanticMathNodeKind.FUNCTION -> SemanticComponentRole.FUNCTION_ARGUMENT
                    node.kind == SemanticMathNodeKind.PIECEWISE -> SemanticComponentRole.PIECEWISE_BRANCH
                    child.kind == SemanticMathNodeKind.VARIABLE -> SemanticComponentRole.VARIABLE
                    child.kind == SemanticMathNodeKind.NUMBER -> SemanticComponentRole.NUMBER
                    else -> SemanticComponentRole.EXPRESSION
                }
                visit(child, depth + 1, childRole)
            }
        }
        visit(tree.root, 0, SemanticComponentRole.EXPRESSION)
        return result
    }

    private fun isReciprocal(node: SemanticMathNode): Boolean =
        node.kind == SemanticMathNodeKind.POWER &&
            node.children.getOrNull(1)?.value?.toDoubleOrNull()?.let { it < 0.0 } == true

    fun apply(
        tree: SemanticExpressionTree,
        nodeId: String,
        operation: SemanticToolOperation,
    ): Result<SemanticToolResult> = runCatching {
        val target = requireNotNull(find(tree.root, nodeId)) { "Subexpression is no longer available." }
        val before = render(target)
        val after = when (operation) {
            SemanticToolOperation.SIMPLIFY -> SymbolicCasEngine().simplify(before).exact
            SemanticToolOperation.EXPAND -> SymbolicCasEngine().expand(before).exact
            SemanticToolOperation.FACTOR -> SymbolicCasEngine().factor(before).exact
            SemanticToolOperation.NEGATE -> "-($before)"
        }
        val replacement = SmartBoardSemanticExpressionBuilder.build(after).root.repath(nodeId)
        val replaced = replace(tree.root, nodeId, replacement)
        val expression = render(replaced)
        val rebuilt = SmartBoardSemanticExpressionBuilder.build(
            expression,
            expression,
            tree.root.sourceStrokeIds,
            tree.root.confidence,
        )
        SemanticToolResult(operation, before, after, expression, rebuilt)
    }

    fun replace(
        tree: SemanticExpressionTree,
        nodeId: String,
        replacementExpression: String,
    ): Result<SemanticToolResult> = runCatching {
        require(replacementExpression.isNotBlank() && replacementExpression.length <= 4_000)
        val target = requireNotNull(find(tree.root, nodeId))
        val replacement = SmartBoardSemanticExpressionBuilder.build(replacementExpression).root.repath(nodeId)
        val expression = render(replace(tree.root, nodeId, replacement))
        val rebuilt = SmartBoardSemanticExpressionBuilder.build(
            expression,
            expression,
            tree.root.sourceStrokeIds,
            tree.root.confidence,
        )
        SemanticToolResult(SemanticToolOperation.SIMPLIFY, render(target), replacementExpression, expression, rebuilt)
    }

    fun render(node: SemanticMathNode): String = when (node.kind) {
        SemanticMathNodeKind.NUMBER, SemanticMathNodeKind.VARIABLE, SemanticMathNodeKind.UNKNOWN -> node.value.orEmpty()
        SemanticMathNodeKind.NEGATION -> "-(${node.children.firstOrNull()?.let(::render).orEmpty()})"
        SemanticMathNodeKind.SUM -> node.children.joinToString("+") { "(${render(it)})" }
        SemanticMathNodeKind.PRODUCT -> node.children.joinToString("*") { "(${render(it)})" }
        SemanticMathNodeKind.POWER -> "(${node.children.getOrNull(0)?.let(::render).orEmpty()})^(${node.children.getOrNull(1)?.let(::render).orEmpty()})"
        SemanticMathNodeKind.FUNCTION -> "${node.value.orEmpty()}(${node.children.joinToString(", ", transform = ::render)})"
        SemanticMathNodeKind.EQUATION, SemanticMathNodeKind.INEQUALITY ->
            "${node.children.getOrNull(0)?.let(::render).orEmpty()}${node.value.orEmpty()}${node.children.getOrNull(1)?.let(::render).orEmpty()}"
        SemanticMathNodeKind.MATRIX -> "[${node.children.joinToString(";") { row -> row.children.joinToString(",", transform = ::render) }}]"
        SemanticMathNodeKind.MATRIX_ROW -> node.children.joinToString(",", transform = ::render)
        SemanticMathNodeKind.PIECEWISE -> "piecewise{${node.children.joinToString(";") { it.children.joinToString(",", transform = ::render) }}}"
        SemanticMathNodeKind.PIECEWISE_BRANCH -> node.children.joinToString(",", transform = ::render)
        SemanticMathNodeKind.VECTOR, SemanticMathNodeKind.COORDINATE -> "(${node.children.joinToString(",", transform = ::render)})"
    }

    private fun find(node: SemanticMathNode, id: String): SemanticMathNode? =
        node.takeIf { it.id == id } ?: node.children.firstNotNullOfOrNull { find(it, id) }

    private fun replace(node: SemanticMathNode, id: String, replacement: SemanticMathNode): SemanticMathNode =
        if (node.id == id) replacement else node.copy(children = node.children.map { replace(it, id, replacement) })

    private fun SemanticMathNode.repath(path: String): SemanticMathNode =
        copy(id = path, children = children.mapIndexed { index, child -> child.repath("$path.$index") })
}

enum class SmartBoardReconstructionKind { TABLE, GRAPH_2D, GRAPH_3D, GEOMETRY_2D }

data class SmartBoardReconstructionSuggestion(
    val kind: SmartBoardReconstructionKind,
    val title: String,
    val confidence: Float,
    val explanation: String,
)

object SmartBoardEditableReconstructionEngine {
    fun suggestions(element: MathExpressionElement): List<SmartBoardReconstructionSuggestion> = buildList {
        val root = element.semanticTree?.root
        if (root?.kind == SemanticMathNodeKind.MATRIX) {
            add(SmartBoardReconstructionSuggestion(SmartBoardReconstructionKind.TABLE, "Editable table", .98f, "Matrix rows and cells are structurally parsed."))
        }
        val source = element.normalizedExpression ?: element.displayLatex
        if ('=' in source || Regex("""\b[xy]\b""").containsMatchIn(source)) {
            add(SmartBoardReconstructionSuggestion(SmartBoardReconstructionKind.GRAPH_2D, "Linked 2D graph", .90f, "Expression contains a graphable relation."))
        }
        if (Regex("""\b[xyz]\b""").findAll(source).map { it.value }.toSet().size >= 3) {
            add(SmartBoardReconstructionSuggestion(SmartBoardReconstructionKind.GRAPH_3D, "Linked 3D graph", .84f, "Expression uses x, y and z."))
        }
        if (root?.kind in setOf(SemanticMathNodeKind.COORDINATE, SemanticMathNodeKind.VECTOR)) {
            add(SmartBoardReconstructionSuggestion(SmartBoardReconstructionKind.GEOMETRY_2D, "Editable geometry", .82f, "Coordinate/vector structure can seed a construction."))
        }
    }

    fun tableFrom(element: MathExpressionElement, id: String, now: Long): Result<TableElement> = runCatching {
        val root = requireNotNull(element.semanticTree?.root)
        require(root.kind == SemanticMathNodeKind.MATRIX)
        val rows = root.children.map { row -> row.children.map(SmartBoardSemanticToolEngine::render) }
        val columns = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val padded = rows.map { row -> row + List(columns - row.size) { "" } }
        TableElement(
            id = id,
            columnHeaders = List(columns) { "Column ${it + 1}" },
            rows = padded,
            sourceElementIds = listOf(element.id),
            bounds = SmartBoardBounds(
                element.bounds.left,
                element.bounds.bottom + 20f,
                element.bounds.left + maxOf(280f, columns * 110f),
                element.bounds.bottom + 20f + maxOf(120f, (rows.size + 1) * 34f),
            ),
            createdAt = now,
        )
    }
}

object SmartBoardClassroomToolFactory {
    fun stickyNote(bounds: SmartBoardBounds, now: Long, text: String = "Add note…") =
        TextElement("note-${UUID.randomUUID()}", text, bounds, now)

    fun blankTable(bounds: SmartBoardBounds, now: Long, columns: Int = 3, rows: Int = 4) =
        TableElement(
            "table-${UUID.randomUUID()}",
            List(columns.coerceIn(1, 12)) { "Column ${it + 1}" },
            List(rows.coerceIn(1, 20)) { List(columns.coerceIn(1, 12)) { "" } },
            emptyList(),
            bounds = bounds,
            createdAt = now,
        )

    fun shape(type: SmartBoardShapeType, bounds: SmartBoardBounds, now: Long): ShapeElement {
        val left = bounds.left
        val top = bounds.top
        val right = bounds.right
        val bottom = bounds.bottom
        val center = bounds.center
        val points = when (type) {
            SmartBoardShapeType.CIRCLE, SmartBoardShapeType.ELLIPSE -> (0..32).map { step ->
                val angle = Math.PI * 2 * step / 32
                SmartBoardPoint(
                    center.x + (bounds.width / 2f * kotlin.math.cos(angle)).toFloat(),
                    center.y + (bounds.height / 2f * kotlin.math.sin(angle)).toFloat(),
                )
            }
            SmartBoardShapeType.TRIANGLE, SmartBoardShapeType.RIGHT_TRIANGLE, SmartBoardShapeType.EQUILATERAL_TRIANGLE ->
                listOf(SmartBoardPoint(center.x, top), SmartBoardPoint(right, bottom), SmartBoardPoint(left, bottom), SmartBoardPoint(center.x, top))
            SmartBoardShapeType.COORDINATE_AXES ->
                listOf(SmartBoardPoint(left, center.y), SmartBoardPoint(right, center.y), SmartBoardPoint(center.x, center.y), SmartBoardPoint(center.x, top), SmartBoardPoint(center.x, bottom))
            SmartBoardShapeType.NUMBER_LINE ->
                listOf(SmartBoardPoint(left, center.y), SmartBoardPoint(right, center.y))
            SmartBoardShapeType.LINE, SmartBoardShapeType.LINE_SEGMENT, SmartBoardShapeType.RAY,
            SmartBoardShapeType.ARROW, SmartBoardShapeType.VECTOR_ARROW, SmartBoardShapeType.FORCE_ARROW ->
                listOf(SmartBoardPoint(left, bottom), SmartBoardPoint(right, top))
            SmartBoardShapeType.ANGLE, SmartBoardShapeType.RIGHT_ANGLE_MARKER ->
                listOf(SmartBoardPoint(left, top), SmartBoardPoint(left, bottom), SmartBoardPoint(right, bottom))
            else -> listOf(
                SmartBoardPoint(left, top), SmartBoardPoint(right, top), SmartBoardPoint(right, bottom),
                SmartBoardPoint(left, bottom), SmartBoardPoint(left, top),
            )
        }
        return ShapeElement(
            "shape-${UUID.randomUUID()}",
            type,
            points,
            emptyList(),
            1f,
            2.4f,
            0xff43d9f5,
            bounds = SmartBoardBounds.from(points),
            createdAt = now,
        )
    }
}
