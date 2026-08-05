package com.indianservers.smartboard.smartboard.audit

import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import kotlin.math.max

object SmartBoardAuditScoring {
    data class Comparison(
        val normalizedExpected: String,
        val normalizedDetected: String,
        val exact: Boolean,
        val semantic: Boolean,
        val structure: Boolean,
        val layout: Boolean,
        val symbolScore: Double,
        val structureScore: Double,
        val spatialScore: Double,
        val semanticScore: Double,
        val overallScore: Double,
        val status: AuditStatus,
        val errors: Set<AuditErrorType>,
    )

    fun compare(
        case: SmartBoardAuditCase,
        rawDetected: String?,
        confidence: Float?,
        detectedObjects: List<String> = emptyList(),
    ): Comparison {
        val expected = case.expectedLatex.orEmpty()
        val detected = rawDetected.orEmpty()
        val normalizedExpected = normalize(expected)
        val normalizedDetected = normalize(detected)
        val exact = detected.isNotBlank() && normalizedExpected == normalizedDetected
        val symbol = symbolAccuracy(normalizedExpected, normalizedDetected)
        val structureExpected = structureSignature(case.expectedStructure.orEmpty(), expected)
        val structureDetected = structureSignature("", detected)
        val structureScore = structureSimilarity(structureExpected, structureDetected)
        val structure = structureScore >= .99
        val spatial = spatialScore(case, detected, detectedObjects)
        val layout = spatial >= .99
        val semantic = exact || conservativeSemanticEquivalent(normalizedExpected, normalizedDetected)
        val semanticScore = if (semantic) 1.0 else max(symbol * .85, structureScore * .7)
        val overall = (.30 * symbol + .30 * structureScore + .20 * spatial +
            .15 * semanticScore + .05 * (confidence ?: 0f)).coerceIn(0.0, 1.0)
        val errors = classifyErrors(case, expected, detected, structure, layout, confidence)
        val status = when {
            detected.isBlank() -> AuditStatus.NOT_DETECTED
            exact && structure && layout -> AuditStatus.PASS
            semantic && structure && layout -> AuditStatus.PASS_WITH_NORMALIZATION
            structureScore < .5 -> AuditStatus.WRONG_STRUCTURE
            spatial < .7 -> AuditStatus.WRONG_LAYOUT
            symbol < .75 -> AuditStatus.WRONG_SYMBOL
            else -> AuditStatus.PARTIAL
        }
        return Comparison(
            normalizedExpected, normalizedDetected, exact, semantic, structure, layout,
            symbol, structureScore, spatial, semanticScore, overall, status, errors,
        )
    }

    fun normalize(source: String): String = runCatching {
        SmartBoardLatexAdapter.toEngineExpression(source)
    }.getOrDefault(source)
        .lowercase()
        .replace("\\left", "")
        .replace("\\right", "")
        .replace("\\cdot", "*")
        .replace("\\times", "*")
        .replace("\\pi", "pi")
        .replace('×', '*')
        .replace('·', '*')
        .replace('−', '-')
        .replace('–', '-')
        .replace('÷', '/')
        .replace('≤', '<')
        .replace('≥', '>')
        .replace("²", "^2")
        .replace("³", "^3")
        .replace(Regex("""\\(sin|cos|tan|sec|csc|cot|log|ln|exp|sqrt)"""), "$1")
        .replace(Regex("""\s+"""), "")
        .replace("{", "")
        .replace("}", "")
        .replace(Regex("""\^\(([^()]+)\)"""), "^$1")
        .replace(Regex("""_\(([^()]+)\)"""), "_$1")
        .trim()

    fun symbolAccuracy(expected: String, detected: String): Double {
        if (expected.isEmpty()) return if (detected.isEmpty()) 1.0 else 0.0
        val distance = levenshtein(expected.toList(), detected.toList())
        return (1.0 - distance.toDouble() / max(expected.length, detected.length).coerceAtLeast(1)).coerceIn(0.0, 1.0)
    }

    private fun conservativeSemanticEquivalent(expected: String, detected: String): Boolean {
        if (expected == detected) return true
        val substitutions = listOf(
            "0.5" to "1/2",
            "x*x" to "x^2",
            "sqrtx" to "x^1/2",
            "ln" to "log_e",
        )
        fun reduced(value: String): String {
            var result = value
            substitutions.forEach { (left, right) ->
                result = result.replace(left, right)
            }
            return result
                .replace(Regex("""(?<=[0-9a-z)])\*(?=[a-z(])"""), "")
                .replace(";", "\n")
        }
        return reduced(expected) == reduced(detected)
    }

    private fun structureSignature(explicit: String, value: String): Set<String> = buildSet {
        explicit.split('+')
            .filter(String::isNotBlank)
            .map { it.substringBefore(':') }
            .filterNot { it == "linear-symbol-sequence" }
            .forEach(::add)
        val source = value.lowercase()
        if ('=' in source) add("equation")
        if ('<' in source || '>' in source || '≤' in source || '≥' in source) add("inequality")
        if ('/' in source || "\\frac" in source) add("fraction")
        if ('^' in source || "\\sup" in source) add("superscript")
        if ('_' in source || "\\sub" in source) add("subscript")
        if ("sqrt" in source || "\\sqrt" in source || '√' in source || '∛' in source) add("root")
        if ('\n' in source || ';' in source || "\\\\" in source) add("multiline")
        if ("[[" in source || "\\begin{matrix}" in source || "\\begin{bmatrix}" in source) add("matrix")
        if ("int" in source || "\\int" in source || '∫' in source) add("integral")
        if ("sum" in source || "\\sum" in source || 'Σ' in source || '∑' in source) add("summation")
        if ("graph:" in explicit) add("graph")
        if ("shape:" in explicit) add("shape")
    }

    private fun structureSimilarity(expected: Set<String>, detected: Set<String>): Double {
        if (expected.isEmpty()) return 1.0
        return expected.intersect(detected).size.toDouble() / expected.size
    }

    private fun spatialScore(case: SmartBoardAuditCase, detected: String, objects: List<String>): Double {
        val expected = case.expectedStructure.orEmpty()
        val checks = buildList {
            if ("superscript" in expected) add('^' in detected || "\\sup" in detected)
            if ("subscript" in expected) add('_' in detected || "\\sub" in detected)
            if ("fraction" in expected) add('/' in detected || "\\frac" in detected)
            if ("root" in expected) add("sqrt" in detected || "\\sqrt" in detected || '√' in detected)
            if ("multiline" in expected || "system" in expected) add(';' in detected || '\n' in detected || "\\\\" in detected)
            if ("matrix" in expected) add("[[" in detected || "matrix" in detected || objects.any { "MATRIX" in it })
            if ("graph:" in expected) add(objects.any { it.startsWith("GRAPH:") })
            if ("shape:" in expected) add(objects.any { it.startsWith("SHAPE:") })
        }
        return if (checks.isEmpty()) 1.0 else checks.count { it }.toDouble() / checks.size
    }

    private fun classifyErrors(
        case: SmartBoardAuditCase,
        expected: String,
        detected: String,
        structure: Boolean,
        layout: Boolean,
        confidence: Float?,
    ): Set<AuditErrorType> = buildSet {
        if (expected.any(Char::isDigit) && detected.isNotBlank() && expected.filter(Char::isDigit) != detected.filter(Char::isDigit)) add(AuditErrorType.DIGIT_CONFUSION)
        if (expected.any(Char::isLetter) && detected.isNotBlank() && expected.filter(Char::isLetter).lowercase() != detected.filter(Char::isLetter).lowercase()) add(AuditErrorType.LETTER_CONFUSION)
        if ('^' in expected && '^' !in detected) add(AuditErrorType.SUPERSCRIPT_MISSED)
        if ('_' in expected && '_' !in detected) add(AuditErrorType.SUBSCRIPT_MISSED)
        if (('/' in expected || "\\frac" in expected) && !structure) add(AuditErrorType.FRACTION_MISREAD)
        if (("sqrt" in expected || '√' in expected) && !structure) add(AuditErrorType.ROOT_SCOPE_ERROR)
        if (case.category == AuditCategory.MATRICES_VECTORS && !structure) add(AuditErrorType.MATRIX_ROW_ERROR)
        if (case.category == AuditCategory.GRAPHS && !layout) add(AuditErrorType.GRAPH_NOT_DETECTED)
        if (case.category == AuditCategory.GEOMETRY_DIAGRAMS && !layout) add(AuditErrorType.SHAPE_NOT_DETECTED)
        if (case.canvasRegion in setOf(CanvasRegion.NEAR_BOUNDARY, CanvasRegion.NEAR_TOOLBAR) && !layout) add(AuditErrorType.LOCATION_SENSITIVE)
        if (case.handwritingProfile == HandwritingProfile.CROWDED && !structure) add(AuditErrorType.CROWDING_SENSITIVE)
        if (case.handwritingProfile == HandwritingProfile.OVERWRITTEN_CORRECTION && !structure) add(AuditErrorType.OVERWRITING_ERROR)
        if ("reverse" in case.strokeVariant && !structure) add(AuditErrorType.STROKE_ORDER_SENSITIVE)
        if (confidence != null && confidence < .55f) add(AuditErrorType.LOW_CONFIDENCE)
    }

    private fun <T> levenshtein(left: List<T>, right: List<T>): Int {
        var previous = IntArray(right.size + 1) { it }
        left.forEachIndexed { row, a ->
            val current = IntArray(right.size + 1)
            current[0] = row + 1
            right.forEachIndexed { column, b ->
                current[column + 1] = minOf(
                    current[column] + 1,
                    previous[column + 1] + 1,
                    previous[column] + if (a == b) 0 else 1,
                )
            }
            previous = current
        }
        return previous.last()
    }
}
