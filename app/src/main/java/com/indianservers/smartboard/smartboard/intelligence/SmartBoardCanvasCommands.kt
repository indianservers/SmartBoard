package com.indianservers.smartboard.smartboard.intelligence

enum class CanvasCommandKind {
    SELECT_BY_MEANING,
    SEARCH_CANVAS,
    GRAPH_FROM_INK,
    GRAPH_SELECTED_EQUATION,
    SET_GRAPH_PARAMETER,
    SHOW_HINT,
    LOCALIZE_MISTAKE,
    RECOGNIZE,
    TEACH_EXAMPLE,
    ENABLE_TEACH_MODE,
    DISABLE_TEACH_MODE,
    CLEAR_BOARD,
    DELETE_SELECTION,
    UNDO,
    REDO,
    UNKNOWN,
}

data class ParsedCanvasCommand(
    val original: String,
    val kind: CanvasCommandKind,
    val argument: String? = null,
    val numericValue: Double? = null,
    val requiresConfirmation: Boolean = false,
    val summary: String,
)

object SmartBoardCanvasCommandEngine {
    fun parse(source: String): ParsedCanvasCommand {
        val command = source.trim().take(500)
        val normalized = command.lowercase().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return unknown(command)

        val parameter = Regex(
            """(?:set|change|make)\s+(?:parameter\s+)?([a-z][a-z0-9_]*)\s*(?:=|to)\s*(-?\d+(?:\.\d+)?)""",
        ).find(normalized)
        if (parameter != null) {
            return ParsedCanvasCommand(
                command,
                CanvasCommandKind.SET_GRAPH_PARAMETER,
                parameter.groupValues[1],
                parameter.groupValues[2].toDoubleOrNull(),
                summary = "Adjust graph parameter ${parameter.groupValues[1]}",
            )
        }

        val teachLabel = Regex("""teach\s+(?:this|selection|smart board)\s+(?:as|to mean)\s+(.+)""").find(normalized)
        if (teachLabel != null) {
            val label = command.takeLast(teachLabel.groupValues[1].length).trim()
            return ParsedCanvasCommand(
                command,
                CanvasCommandKind.TEACH_EXAMPLE,
                label,
                summary = "Learn the selected handwriting as “$label”",
            )
        }

        return when {
            normalized in setOf("clear board", "clear the board", "erase everything", "delete everything") ->
                ParsedCanvasCommand(command, CanvasCommandKind.CLEAR_BOARD, requiresConfirmation = true,
                    summary = "Clear every object from this board")
            normalized in setOf("delete selection", "delete selected", "remove selection", "remove selected objects") ->
                ParsedCanvasCommand(command, CanvasCommandKind.DELETE_SELECTION, requiresConfirmation = true,
                    summary = "Delete the selected objects")
            normalized == "undo" || normalized.startsWith("undo ") ->
                ParsedCanvasCommand(command, CanvasCommandKind.UNDO, summary = "Undo the last board change")
            normalized == "redo" || normalized.startsWith("redo ") ->
                ParsedCanvasCommand(command, CanvasCommandKind.REDO, summary = "Redo the last undone change")
            ("enable" in normalized || "turn on" in normalized || "start" in normalized) &&
                ("teach mode" in normalized || "handwriting adaptation" in normalized) ->
                ParsedCanvasCommand(command, CanvasCommandKind.ENABLE_TEACH_MODE, summary = "Enable personal handwriting adaptation")
            ("disable" in normalized || "turn off" in normalized || "stop" in normalized) &&
                ("teach mode" in normalized || "handwriting adaptation" in normalized) ->
                ParsedCanvasCommand(command, CanvasCommandKind.DISABLE_TEACH_MODE, summary = "Disable personal handwriting adaptation")
            normalized.contains("where did") || normalized.startsWith("find ") ||
                normalized.startsWith("search ") || normalized.startsWith("show me where") -> {
                val query = normalized
                    .removePrefix("find ").removePrefix("search ").removePrefix("show me where ")
                    .trim()
                ParsedCanvasCommand(command, CanvasCommandKind.SEARCH_CANVAS, query, summary = "Search all board pages for “$query”")
            }
            normalized.startsWith("select ") || normalized.startsWith("choose ") ||
                normalized.startsWith("show all ") -> {
                val query = normalized.removePrefix("select ").removePrefix("choose ").removePrefix("show all ").trim()
                ParsedCanvasCommand(command, CanvasCommandKind.SELECT_BY_MEANING, query, summary = "Select canvas objects matching “$query”")
            }
            ("graph" in normalized && ("ink" in normalized || "curve" in normalized || "drawing" in normalized)) ->
                ParsedCanvasCommand(command, CanvasCommandKind.GRAPH_FROM_INK, summary = "Estimate editable graphs from the drawn ink")
            normalized in setOf("graph this", "graph selected equation", "plot this", "plot selected equation") ->
                ParsedCanvasCommand(command, CanvasCommandKind.GRAPH_SELECTED_EQUATION, summary = "Create an editable graph from the selected equation")
            normalized.contains("mistake") || normalized.contains("wrong step") ||
                normalized.contains("check my work") || normalized.contains("check steps") ->
                ParsedCanvasCommand(command, CanvasCommandKind.LOCALIZE_MISTAKE, summary = "Locate the first invalid mathematical transformation")
            normalized.contains("hint") || normalized.contains("next step") ->
                ParsedCanvasCommand(command, CanvasCommandKind.SHOW_HINT, summary = "Place a small hint beside the relevant line")
            normalized.startsWith("recognize") || normalized.startsWith("read handwriting") ->
                ParsedCanvasCommand(command, CanvasCommandKind.RECOGNIZE, summary = "Recognize the selected or recent handwriting")
            else -> unknown(command)
        }
    }

    private fun unknown(command: String) = ParsedCanvasCommand(
        command,
        CanvasCommandKind.UNKNOWN,
        summary = "Command not recognized",
    )
}
