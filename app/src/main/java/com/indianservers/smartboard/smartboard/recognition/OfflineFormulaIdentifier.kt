package com.indianservers.smartboard.smartboard.recognition

import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.physics.PhysicsFormulaMatcher
import java.util.Locale

data class OfflineFormulaMatch(
    val id: String,
    val title: String,
    val canonicalForm: String,
    val subject: SmartBoardSubject,
    val confidence: Float,
    val variables: List<String>,
    val explanation: String,
)

/**
 * Deterministic, network-free formula identification for the Smart Board editor.
 * Physics identification delegates to the application's bundled formula catalogue.
 */
object OfflineFormulaIdentifier {
    private val physicsMatcher = PhysicsFormulaMatcher()

    private data class PatternFormula(
        val id: String,
        val title: String,
        val canonical: String,
        val variables: List<String>,
        val matcher: (String) -> Boolean,
    )

    private val mathematics = listOf(
        PatternFormula("math.pythagorean", "Pythagorean theorem", "a² + b² = c²", listOf("a", "b", "c")) {
            Regex("""(?:a\^?2\+b\^?2=c\^?2|c\^?2=a\^?2\+b\^?2)""").matches(it)
        },
        PatternFormula("math.quadratic", "Quadratic formula", "x = (-b ± √(b² - 4ac)) / 2a", listOf("a", "b", "c", "x")) {
            "sqrt(b^2-4ac)" in it && ("-b+" in it || "-bplusminus" in it)
        },
        PatternFormula("math.circle", "Circle in standard form", "(x-h)² + (y-k)² = r²", listOf("h", "k", "r")) {
            Regex("""\(x-[^)]+\)\^?2\+\(y-[^)]+\)\^?2=[^=]+\^?2""").matches(it)
        },
        PatternFormula("math.slope-intercept", "Slope-intercept form", "y = mx + b", listOf("m", "b", "x", "y")) {
            Regex("""y=m\*?x\+b""").matches(it)
        },
        PatternFormula("math.arithmetic-series", "Arithmetic series sum", "Sₙ = n/2(2a + (n-1)d)", listOf("Sₙ", "n", "a", "d")) {
            ("sn=" in it || "s_n=" in it) && "n/2" in it && "(n-1)" in it
        },
    )

    fun identify(source: String): OfflineFormulaMatch? {
        val validated = SafeLatexPreview.validate(source).getOrNull() ?: return null
        val normalized = normalize(validated)
        mathematics.firstOrNull { it.matcher(normalized) }?.let {
            return OfflineFormulaMatch(
                it.id, it.title, it.canonical, SmartBoardSubject.MATHEMATICS, .98f,
                it.variables, "Matched locally from a canonical mathematical structure.",
            )
        }
        return physicsMatcher.match(validated)?.let {
            OfflineFormulaMatch(
                id = it.id,
                title = it.title,
                canonicalForm = it.equation,
                subject = SmartBoardSubject.PHYSICS,
                confidence = .86f,
                variables = it.variables.map { variable -> variable.symbol }.distinct(),
                explanation = "Matched locally against the bundled Physics formula catalogue.",
            )
        }
    }

    private fun normalize(source: String): String = source.lowercase(Locale.ROOT)
        .replace("\\left", "").replace("\\right", "")
        .replace("\\cdot", "*").replace("\\times", "*")
        .replace("\\pm", "plusminus")
        .replace("\\sqrt{", "sqrt(")
        .replace("}", ")")
        .replace("{", "(")
        .replace("²", "^2")
        .replace(Regex("""\s+"""), "")
}
