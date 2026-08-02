package com.indianservers.smartboard.core

import kotlin.math.abs

data class GraphFolder(val id: String, val title: String, val collapsed: Boolean = false)
data class GraphParameter(val name: String, val value: Double, val minimum: Double = -10.0, val maximum: Double = 10.0, val step: Double = .1, val animated: Boolean = false) {
    init { require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) && minimum < maximum && step > 0) }
}
data class InteractiveGraphRow(
    val id: String,
    val source: String,
    val colorKey: String,
    val visible: Boolean = true,
    val folderId: String? = null,
    val note: String? = null,
    val domain: GraphDomain? = null,
    val error: String? = null,
    val typed: TypedGraphExpression? = null,
)
data class GraphWorkspaceState(
    val rows: List<InteractiveGraphRow> = emptyList(),
    val folders: List<GraphFolder> = emptyList(),
    val parameters: Map<String, GraphParameter> = emptyMap(),
    val linkedVariables: Map<String, String> = emptyMap(),
    val revision: Long = 0,
)

sealed interface GraphWorkspaceAction {
    data class AddExpression(val source: String, val colorKey: String = "cyan", val folderId: String? = null) : GraphWorkspaceAction
    data class EditExpression(val id: String, val source: String) : GraphWorkspaceAction
    data class DeleteExpression(val id: String) : GraphWorkspaceAction
    data class ToggleVisibility(val id: String) : GraphWorkspaceAction
    data class SetDomain(val id: String, val domain: GraphDomain?) : GraphWorkspaceAction
    data class SetParameter(val name: String, val value: Double) : GraphWorkspaceAction
    data class AnimateParameter(val name: String, val enabled: Boolean) : GraphWorkspaceAction
    data class AddFolder(val title: String) : GraphWorkspaceAction
    data class ToggleFolder(val id: String) : GraphWorkspaceAction
    data class MoveToFolder(val rowId: String, val folderId: String?) : GraphWorkspaceAction
    data class SetNote(val rowId: String, val note: String?) : GraphWorkspaceAction
    data class SetLinkedVariable(val name: String, val expression: String) : GraphWorkspaceAction
    data class RemoveLinkedVariable(val name: String) : GraphWorkspaceAction
}

/** Pure, undo-friendly graph action reducer. UI, notebook and classroom playback can replay the same actions. */
class GraphWorkspaceReducer(private val expressions: ExpressionEngine = ExpressionEngine()) {
    fun reduce(state: GraphWorkspaceState, action: GraphWorkspaceAction): GraphWorkspaceState {
        val updated = when (action) {
            is GraphWorkspaceAction.AddExpression -> {
                val id = nextId("g", state.rows.map { it.id })
                state.copy(rows = state.rows + validate(InteractiveGraphRow(id, action.source, action.colorKey, folderId = action.folderId)))
            }
            is GraphWorkspaceAction.EditExpression -> state.copy(rows = state.rows.map { if (it.id == action.id) validate(it.copy(source = action.source)) else it })
            is GraphWorkspaceAction.DeleteExpression -> state.copy(rows = state.rows.filterNot { it.id == action.id })
            is GraphWorkspaceAction.ToggleVisibility -> state.copy(rows = state.rows.map { if (it.id == action.id) it.copy(visible = !it.visible) else it })
            is GraphWorkspaceAction.SetDomain -> state.copy(rows = state.rows.map { if (it.id == action.id) it.copy(domain = action.domain) else it })
            is GraphWorkspaceAction.SetParameter -> {
                val old = state.parameters[action.name] ?: GraphParameter(action.name, action.value)
                resolveLinked(state.copy(parameters = state.parameters + (action.name to old.copy(value = action.value.coerceIn(old.minimum, old.maximum)))))
            }
            is GraphWorkspaceAction.AnimateParameter -> {
                val old = state.parameters[action.name] ?: GraphParameter(action.name, 0.0)
                state.copy(parameters = state.parameters + (action.name to old.copy(animated = action.enabled)))
            }
            is GraphWorkspaceAction.AddFolder -> state.copy(folders = state.folders + GraphFolder(nextId("folder", state.folders.map { it.id }), action.title.ifBlank { "Folder" }))
            is GraphWorkspaceAction.ToggleFolder -> state.copy(folders = state.folders.map { if (it.id == action.id) it.copy(collapsed = !it.collapsed) else it })
            is GraphWorkspaceAction.MoveToFolder -> {
                require(action.folderId == null || state.folders.any { it.id == action.folderId }) { "Unknown graph folder." }
                state.copy(rows = state.rows.map { if (it.id == action.rowId) it.copy(folderId = action.folderId) else it })
            }
            is GraphWorkspaceAction.SetNote -> state.copy(rows = state.rows.map { if (it.id == action.rowId) it.copy(note = action.note?.take(4_000)) else it })
            is GraphWorkspaceAction.SetLinkedVariable -> {
                require(action.name.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))
                expressions.compile(action.expression)
                resolveLinked(state.copy(linkedVariables = state.linkedVariables + (action.name to action.expression)))
            }
            is GraphWorkspaceAction.RemoveLinkedVariable -> state.copy(linkedVariables = state.linkedVariables - action.name)
        }
        return discoverParameters(updated).copy(revision = state.revision + 1)
    }

    fun animationFrame(state: GraphWorkspaceState, deltaSeconds: Double): GraphWorkspaceState {
        require(deltaSeconds >= 0)
        val values = state.parameters.mapValues { (_, parameter) ->
            if (!parameter.animated) parameter else {
                val width = parameter.maximum - parameter.minimum
                val raw = parameter.value + parameter.step * deltaSeconds * 20
                parameter.copy(value = parameter.minimum + ((raw - parameter.minimum) % width + width) % width)
            }
        }
        return state.copy(parameters = values, revision = state.revision + 1)
    }

    private fun validate(row: InteractiveGraphRow): InteractiveGraphRow {
        var typed: TypedGraphExpression? = null
        val error = runCatching {
            typed = TypedGraphExpressionParser.parse(row.source)
            val kind = AdvancedGraphEngine(expressions).classify(row.source)
            when (kind) {
                AdvancedGraphKind.Parametric -> require(row.source.contains(';')) { "Use x(t)=…; y(t)=…" }
                AdvancedGraphKind.VectorField -> require(row.source.contains(';')) { "Use two vector components separated by ';'." }
                AdvancedGraphKind.Implicit, AdvancedGraphKind.Inequality -> require(row.source.contains('=' ) || listOf("<", ">").any(row.source::contains))
                else -> expressions.compile(stripEquation(row.source))
            }
        }.exceptionOrNull()?.message
        return row.copy(error = error, typed = if (error == null) typed else null)
    }

    private fun discoverParameters(state: GraphWorkspaceState): GraphWorkspaceState {
        val reserved = setOf("x", "y", "t", "theta", "pi", "e", "sin", "cos", "tan", "sqrt", "abs", "ln", "log", "exp", "min", "max")
        val found = state.rows.flatMap { it.typed?.parameters.orEmpty() }.filterNot { it in reserved || it.length > 10 }.toSet() + state.linkedVariables.keys
        return resolveLinked(state.copy(parameters = state.parameters.filterKeys { it in found } + found.associateWith { state.parameters[it] ?: GraphParameter(it, 1.0) }))
    }

    private fun resolveLinked(state: GraphWorkspaceState): GraphWorkspaceState {
        var values = state.parameters
        repeat(state.linkedVariables.size.coerceAtLeast(1)) {
            state.linkedVariables.forEach { (name, source) ->
                val value = runCatching { expressions.compile(source).eval(values.mapValues { it.value.value }) }.getOrNull()
                if (value?.isFinite() == true) {
                    val old = values[name] ?: GraphParameter(name, value)
                    values = values + (name to old.copy(value = value.coerceIn(old.minimum, old.maximum)))
                }
            }
        }
        return state.copy(parameters = values)
    }

    private fun nextId(prefix: String, existing: List<String>): String = generateSequence(1) { it + 1 }.map { "$prefix-$it" }.first { it !in existing }
}

data class GraphTransition(val from: GraphWorkspaceState, val action: GraphWorkspaceAction, val to: GraphWorkspaceState)

class GraphActionHistory(private val reducer: GraphWorkspaceReducer = GraphWorkspaceReducer(), initial: GraphWorkspaceState = GraphWorkspaceState()) {
    private val undo = mutableListOf<GraphTransition>()
    private val redo = mutableListOf<GraphTransition>()
    var state: GraphWorkspaceState = initial; private set
    fun dispatch(action: GraphWorkspaceAction): GraphWorkspaceState {
        val next = reducer.reduce(state, action); undo += GraphTransition(state, action, next); redo.clear(); state = next; return state
    }
    fun undo(): GraphWorkspaceState { if (undo.isNotEmpty()) { val item = undo.removeAt(undo.lastIndex); redo += item; state = item.from }; return state }
    fun redo(): GraphWorkspaceState { if (redo.isNotEmpty()) { val item = redo.removeAt(redo.lastIndex); undo += item; state = item.to }; return state }
}
