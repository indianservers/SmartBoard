package com.indianservers.smartboard.smartboard.tutor

import com.indianservers.smartboard.core.MathSolverTutor
import com.indianservers.smartboard.core.SolverMethod
import com.indianservers.smartboard.smartboard.integration.SmartBoardWorkVerificationAdapter
import com.indianservers.smartboard.smartboard.models.SolutionStepStatus

enum class SmartBoardTutorMode {
    HINT, NEXT_STEP, FULL_SOLUTION, CONCEPT, CHECK_WORK, FIND_MISTAKE, ALTERNATIVE_METHOD, VISUAL_EXPLANATION, SIMILAR_QUESTION,
}

data class SmartBoardTutorRequest(
    val problem: String,
    val learnerSteps: List<Pair<String, Float?>> = emptyList(),
    val mode: SmartBoardTutorMode,
    val hintLevel: Int = 1,
    val requestedMethod: SolverMethod = SolverMethod.Auto,
)

data class SmartBoardTutorResponse(
    val title: String,
    val content: List<String>,
    val verified: Boolean,
    val degraded: Boolean = false,
    val warnings: List<String> = emptyList(),
)

/**
 * Offline tutor orchestration. It consumes the existing deterministic solver and trusted verifier;
 * no generated text is represented as mathematically verified unless one of those engines supports it.
 */
class SmartBoardTutorEngine(
    private val tutor: MathSolverTutor = MathSolverTutor(),
    private val verifier: SmartBoardWorkVerificationAdapter = SmartBoardWorkVerificationAdapter(),
) {
    fun respond(request: SmartBoardTutorRequest): SmartBoardTutorResponse {
        require(request.problem.isNotBlank() && request.problem.length <= 4_000)
        require(request.hintLevel in 1..7)
        val solution = tutor.solve(request.problem, request.requestedMethod)
        if (!solution.solution.supported) {
            return SmartBoardTutorResponse(
                "Tutor unavailable for this problem",
                listOf(solution.solution.answer),
                verified = false,
                degraded = true,
                warnings = solution.solution.warnings + "Drawing, editing, saving and other local tools remain available.",
            )
        }
        return when (request.mode) {
            SmartBoardTutorMode.HINT -> hint(solution.solution.steps.map { it.explanation }, request.hintLevel)
            SmartBoardTutorMode.NEXT_STEP -> {
                val index = request.learnerSteps.size.coerceAtMost(solution.solution.steps.lastIndex)
                val step = solution.solution.steps[index]
                SmartBoardTutorResponse("One next step", listOf(step.expression), verified = true)
            }
            SmartBoardTutorMode.FULL_SOLUTION -> SmartBoardTutorResponse(
                "Verified solution", solution.solution.steps.map { "${it.title}: ${it.expression} — ${it.explanation}" } + solution.solution.answer, true,
            )
            SmartBoardTutorMode.CHECK_WORK, SmartBoardTutorMode.FIND_MISTAKE -> {
                if (request.learnerSteps.isEmpty()) return SmartBoardTutorResponse("Write at least one line", emptyList(), false)
                val result = verifier.verify(request.learnerSteps)
                val first = result.firstInvalidStepIndex
                SmartBoardTutorResponse(
                    if (first == null) "Work checked" else "First incorrect step: ${first + 1}",
                    result.steps.mapIndexed { index, step -> "Line ${index + 1}: ${step.status.name.lowercase()} — ${step.feedback}" },
                    verified = result.conclusive,
                )
            }
            SmartBoardTutorMode.ALTERNATIVE_METHOD -> {
                val alternate = solution.alternatives.firstOrNull()
                SmartBoardTutorResponse(
                    "Alternative method",
                    listOfNotNull(alternate?.let { "${it.method.label}: ${it.answer}" }, solution.methodReason),
                    alternate != null,
                    degraded = alternate == null,
                )
            }
            SmartBoardTutorMode.CONCEPT -> SmartBoardTutorResponse("Concept explanation", listOf(solution.methodReason), true)
            SmartBoardTutorMode.VISUAL_EXPLANATION -> SmartBoardTutorResponse(
                "Visual handoff", listOf("Open the linked graph or geometry view; the source expression remains attached to this Board."), false, true,
            )
            SmartBoardTutorMode.SIMILAR_QUESTION -> SmartBoardTutorResponse(
                "Similar-question generator unavailable offline", listOf("The deterministic engine will not invent an unverified exercise."), false, true,
            )
        }
    }

    private fun hint(explanations: List<String>, level: Int): SmartBoardTutorResponse {
        val step = explanations.getOrElse((level - 1).coerceAtMost(explanations.lastIndex)) { "Identify the operation that preserves equivalence." }
        val content = when (level) {
            1 -> "Recall the core concept; identify what the unknown represents."
            2 -> "Work toward isolating the unknown while preserving equality."
            3 -> step.substringBefore('.').ifBlank { step }
            4 -> "Set up the next valid transformation; do not calculate the final answer yet."
            5 -> step
            6 -> explanations.take(2).joinToString(" Then ")
            else -> explanations.joinToString("\n")
        }
        return SmartBoardTutorResponse("Hint $level of 7", listOf(content), verified = level >= 3)
    }
}

enum class SmartBoardMisconceptionKind {
    SIGN_DISTRIBUTION, FRACTION_ADDITION, INVALID_CANCELLATION, EXPONENT_RULE, INEQUALITY_REVERSAL,
    MISSING_PRODUCT_RULE, MISSING_CHAIN_RULE, MISSING_INTEGRATION_CONSTANT, SAMPLE_DENOMINATOR,
}

data class SmartBoardMisconceptionEvidence(
    val kind: SmartBoardMisconceptionKind,
    val explanation: String,
    val provisional: Boolean = true,
)

object SmartBoardMisconceptionAnalyzer {
    /** Rules are considered only after the trusted verifier has established an invalid transition. */
    fun assess(before: String, after: String, status: SolutionStepStatus, confidence: Float?): List<SmartBoardMisconceptionEvidence> {
        if (status != SolutionStepStatus.INVALID || (confidence ?: 1f) < .7f) return emptyList()
        val compactBefore = before.replace(" ", "")
        val compactAfter = after.replace(" ", "")
        return buildList {
            if (Regex("-\\([^)]*\\)").containsMatchIn(compactBefore) && '-' !in compactAfter) add(evidence(SmartBoardMisconceptionKind.SIGN_DISTRIBUTION, "A negative sign may not have been distributed to every term."))
            if ('/' in compactBefore && Regex("\\d+/\\d+\\+\\d+/\\d+").containsMatchIn(compactBefore)) add(evidence(SmartBoardMisconceptionKind.FRACTION_ADDITION, "Fraction denominators may have been added directly."))
            if (Regex("\\^\\d+\\*.*\\^\\d+").containsMatchIn(compactBefore) && '^' !in compactAfter) add(evidence(SmartBoardMisconceptionKind.EXPONENT_RULE, "Recheck the exponent rule for products."))
            if ('<' in compactBefore || '>' in compactBefore) add(evidence(SmartBoardMisconceptionKind.INEQUALITY_REVERSAL, "Check whether multiplying or dividing by a negative required reversing the inequality."))
            if (compactBefore.contains("int", true) && !compactAfter.contains("+C", true)) add(evidence(SmartBoardMisconceptionKind.MISSING_INTEGRATION_CONSTANT, "An indefinite integral may require a constant of integration."))
        }
    }

    private fun evidence(kind: SmartBoardMisconceptionKind, message: String) = SmartBoardMisconceptionEvidence(kind, message)
}
