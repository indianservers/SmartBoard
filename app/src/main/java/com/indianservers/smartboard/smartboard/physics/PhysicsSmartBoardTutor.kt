package com.indianservers.smartboard.smartboard.physics

import com.indianservers.smartboard.smartboard.models.DimensionalStatus
import com.indianservers.smartboard.smartboard.models.PhysicsBoardAnalysis
import com.indianservers.smartboard.smartboard.models.PhysicsVerificationStatus

sealed interface PhysicsTutorTool {
    data class Analyze(val source: String) : PhysicsTutorTool
    data class CheckDimensions(val equation: String) : PhysicsTutorTool
    data class SolveNumerical(val source: String) : PhysicsTutorTool
    data class AnalyzeVector(val source: String) : PhysicsTutorTool
    data class SummarizeMeasurements(val values: List<Double>) : PhysicsTutorTool
}

data class PhysicsTutorResponse(
    val title: String,
    val guidance: List<String>,
    val warnings: List<String>,
    val verified: Boolean,
)

class PhysicsTutorEngine(
    private val analyzer: PhysicsBoardAnalyzer = PhysicsBoardAnalyzer(),
    private val dimensions: PhysicsDimensionalAnalyzer = PhysicsDimensionalAnalyzer(),
) {
    fun hint(source: String, nextStepOnly: Boolean): PhysicsTutorResponse {
        val analysis = analyzer.analyze(source)
        val first = when {
            analysis.ambiguities.isNotEmpty() -> "Confirm ${analysis.ambiguities.first().token}: ${analysis.ambiguities.first().interpretations.joinToString()}."
            analysis.unknownQuantities.isNotEmpty() -> "Write the symbol to find: ${analysis.unknownQuantities.joinToString { it.symbol }}."
            analysis.equations.isNotEmpty() -> "Check that every known value uses units compatible with ${analysis.equations.first().source}."
            else -> "Label the known quantities, their units, and the quantity you need to find."
        }
        val guidance = if (nextStepOnly) listOf(first) else buildList {
            add(first)
            add("Sketch the physical situation and choose a positive direction or sign convention.")
            analysis.equations.firstOrNull()?.let { add("Rearrange ${it.source} symbolically before substituting values.") }
            add("Keep full precision during calculation, then report units and significant figures.")
        }
        return PhysicsTutorResponse(
            if (nextStepOnly) "Next Physics step" else "Physics hint",
            guidance,
            analysis.warnings,
            analysis.ambiguities.isEmpty(),
        )
    }

    fun explain(analysis: PhysicsBoardAnalysis): PhysicsTutorResponse = PhysicsTutorResponse(
        "Physics interpretation",
        buildList {
            add("Detected ${analysis.contentType.name.lowercase().replace('_', ' ')}.")
            analysis.topic?.let { add("Likely topic: ${it.name.lowercase().replace('_', ' ')}.") }
            analysis.quantities.forEach { add("${it.symbol}: ${it.canonicalName ?: "meaning needs confirmation"}${it.unitSymbol?.let { unit -> " [$unit]" }.orEmpty()}") }
        },
        analysis.warnings + analysis.ambiguities.map { it.message },
        analysis.ambiguities.isEmpty(),
    )
}

data class PhysicsWorkStepVerification(
    val line: String,
    val status: PhysicsVerificationStatus,
    val feedback: String,
)

data class PhysicsWorkVerification(
    val steps: List<PhysicsWorkStepVerification>,
    val firstInvalidIndex: Int?,
)

class PhysicsWorkVerifier(
    private val dimensions: PhysicsDimensionalAnalyzer = PhysicsDimensionalAnalyzer(),
    private val matcher: PhysicsFormulaMatcher = PhysicsFormulaMatcher(),
) {
    fun verify(source: String): PhysicsWorkVerification {
        val lines = source.lines().map(String::trim).filter(String::isNotBlank)
        val steps = lines.map { line ->
            if ('=' !in line) {
                PhysicsWorkStepVerification(line, PhysicsVerificationStatus.UNCERTAIN, "This line is a value or statement; confirm its meaning and unit.")
            } else {
                val dimensional = dimensions.check(line)
                when (dimensional.status) {
                    DimensionalStatus.INCONSISTENT -> PhysicsWorkStepVerification(line, PhysicsVerificationStatus.INVALID, dimensional.explanation)
                    DimensionalStatus.CONSISTENT -> PhysicsWorkStepVerification(
                        line,
                        PhysicsVerificationStatus.VALID,
                        if (matcher.match(line) != null) "Formula and dimensions match the reviewed registry."
                        else "Dimensions are consistent; physical validity still depends on the stated assumptions.",
                    )
                    DimensionalStatus.AMBIGUOUS, DimensionalStatus.UNSUPPORTED ->
                        PhysicsWorkStepVerification(line, PhysicsVerificationStatus.UNCERTAIN, dimensional.explanation)
                }
            }
        }
        return PhysicsWorkVerification(steps, steps.indexOfFirst { it.status == PhysicsVerificationStatus.INVALID }.takeIf { it >= 0 })
    }
}
