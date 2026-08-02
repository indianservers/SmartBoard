package com.indianservers.smartboard.smartboard.physics

import com.indianservers.smartboard.smartboard.models.DimensionalStatus
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.PhysicsActionType
import com.indianservers.smartboard.smartboard.models.PhysicsBoardAnalysis
import com.indianservers.smartboard.smartboard.models.PhysicsEngineMetadata
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultStatus
import com.indianservers.smartboard.smartboard.models.PhysicsSolutionStep
import com.indianservers.smartboard.smartboard.models.SmartBoardAction
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionInput
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectAnalysis
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectHandler
import com.indianservers.smartboard.smartboard.recognition.MathHandwritingRecognitionProvider
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import com.indianservers.smartboard.smartboard.recognition.MlKitMathRecognitionAdapter
import java.util.UUID
import com.indianservers.smartboard.core.Phase4Statistics
import com.indianservers.smartboard.input.CasPhotoMathRecognizer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class PhysicsSmartBoardSubjectHandler(
    private val provider: MathHandwritingRecognitionProvider = MlKitMathRecognitionAdapter(),
    private val analyzer: PhysicsBoardAnalyzer = PhysicsBoardAnalyzer(),
) : SmartBoardSubjectHandler {
    override val subject = SmartBoardSubject.PHYSICS

    override suspend fun analyze(input: SmartBoardRecognitionInput): SmartBoardSubjectAnalysis {
        require(input.subject == subject)
        val recognition = provider.recognize(
            MathRecognitionInput(input.strokes, input.bounds, input.rasterPng, MathRecognitionRequestBuilder.fingerprint(input)),
            MathRecognitionOptions(),
        )
        val physics = analyzer.analyze(recognition.normalizedExpression ?: recognition.plainText ?: recognition.latex, recognition.confidence)
        return SmartBoardSubjectAnalysis(
            subject = subject,
            summary = "Physics handwriting recognized locally; confirm symbols, units and inferred physical meaning.",
            recognition = recognition,
            attributes = mapOf(
                "contentType" to physics.contentType.name,
                "topic" to (physics.topic?.name ?: ""),
                "formulaId" to (physics.equations.firstOrNull()?.formulaId ?: ""),
                "actions" to physics.suggestedActions.joinToString(",") { it.name },
                "ambiguities" to physics.ambiguities.joinToString("\u001f") { it.message },
                "warnings" to physics.warnings.joinToString("\u001f"),
            ),
        )
    }

    override fun supportedActions(analysis: SmartBoardSubjectAnalysis): List<SmartBoardAction> =
        if (analysis.recognition == null) listOf(SmartBoardAction.RetryRecognition)
        else buildList {
            add(SmartBoardAction.InsertExpression)
            add(SmartBoardAction.EditLatex)
            analysis.attributes["actions"].orEmpty().split(',').filter(String::isNotBlank).forEach { id ->
                add(SmartBoardAction.SubjectAction(id, id.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)))
            }
            add(SmartBoardAction.RetryRecognition)
        }
}

class PhysicsPhotoRecognitionAdapter(
    private val analyzer: PhysicsBoardAnalyzer = PhysicsBoardAnalyzer(),
) {
    suspend fun recognize(bytes: ByteArray): Pair<MathRecognitionResult, PhysicsBoardAnalysis> =
        suspendCancellableCoroutine { continuation ->
            CasPhotoMathRecognizer.recognize(
                bytes,
                onSuccess = { recognized ->
                    if (!continuation.isActive) return@recognize
                    val primary = recognized.candidates.first()
                    val result = MathRecognitionResult(
                        latex = primary,
                        normalizedExpression = primary,
                        plainText = primary,
                        confidence = recognized.confidence.toFloat(),
                        alternatives = recognized.candidates.drop(1).map {
                            com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative(it, null)
                        },
                        detectedType = com.indianservers.smartboard.smartboard.recognition.MathRecognitionClassifier.detect(primary),
                        warnings = listOf(recognized.message, "Photo interpretation is editable and must be confirmed before calculation."),
                    )
                    continuation.resume(result to analyzer.analyze(primary, result.confidence))
                },
                onFailure = { message ->
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                },
            )
        }
}

data class PhysicsActionOutcome(
    val result: PhysicsResultElement?,
    val handoffRoute: String? = null,
    val handoffPayload: String? = null,
    val message: String,
)

/**
 * Subject-local intelligence boundary. Every tool is allowlisted and deterministic; it delegates
 * calculation, units, statistics, vectors and symbolic parsing to existing application engines.
 */
class PhysicsSmartBoardIntelligenceHandler(
    private val analyzer: PhysicsBoardAnalyzer = PhysicsBoardAnalyzer(),
    private val dimensions: PhysicsDimensionalAnalyzer = PhysicsDimensionalAnalyzer(),
    private val numerical: PhysicsNumericalSolver = PhysicsNumericalSolver(),
    private val tutor: PhysicsTutorEngine = PhysicsTutorEngine(),
    private val verifier: PhysicsWorkVerifier = PhysicsWorkVerifier(),
    private val rearranger: PhysicsFormulaRearranger = PhysicsFormulaRearranger(),
    private val unitConverter: PhysicsUnitConverter = PhysicsUnitConverter(),
) {
    fun analyze(element: PhysicsExpressionElement): PhysicsBoardAnalysis =
        analyzer.analyze(element.displaySource, element.recognitionConfidence)

    suspend fun execute(
        element: PhysicsExpressionElement,
        action: PhysicsActionType,
        now: Long,
    ): PhysicsActionOutcome {
        val analysis = analyze(element)
        val result = when (action) {
            PhysicsActionType.CHECK_DIMENSIONS -> {
                val checked = dimensions.check(element.displaySource)
                result(
                    element, action, "Dimensional analysis",
                    checked.termResults.map { PhysicsSolutionStep("Dimension check", it.term, it.explanation, it.compatible != false) },
                    checked.status == DimensionalStatus.CONSISTENT,
                    checked.explanation,
                    checked.warnings,
                    now,
                )
            }
            PhysicsActionType.SOLVE_NUMERICAL, PhysicsActionType.SUBSTITUTE_VALUES -> {
                val solved = numerical.solve(element.displaySource)
                PhysicsResultElement(
                    id = "physics-result-${UUID.randomUUID()}",
                    sourceElementIds = listOf(element.id),
                    actionType = action,
                    title = solved.title,
                    formulaLatex = solved.formula?.equation,
                    rearrangedFormulaLatex = null,
                    substitutions = solved.substitutions,
                    exactResultLatex = null,
                    numericalResult = solved.numericalResult,
                    resultUnitSymbol = solved.resultUnit,
                    significantFigures = null,
                    steps = solved.steps,
                    assumptions = solved.formula?.assumptions.orEmpty(),
                    warnings = solved.warnings,
                    engineMetadata = solved.engineMetadata,
                    status = solved.status,
                    bounds = resultBounds(element.bounds, solved.steps.size),
                    createdAt = now,
                )
            }
            PhysicsActionType.REARRANGE_FORMULA -> {
                val target = analysis.unknownQuantities.singleOrNull()?.symbol
                    ?: Regex("""\bfor\s+([A-Za-z][A-Za-z0-9_]*)""", RegexOption.IGNORE_CASE).find(element.displaySource)?.groupValues?.get(1)
                val rearranged = target?.let { rearranger.rearrange(element.displaySource.substringBefore('\n'), it) }
                result(
                    element, action, "Formula rearrangement",
                    rearranged?.steps.orEmpty(),
                    rearranged?.verified == true,
                    rearranged?.expression ?: "Write the target as an unknown (for example, v = ?) or add 'for v'.",
                    listOfNotNull(rearranged?.warning),
                    now,
                )
            }
            PhysicsActionType.CONVERT_UNITS, PhysicsActionType.CONVERT_TO_SI -> {
                val conversion = unitConverter.convert(element.displaySource)
                result(
                    element, action, "Unit conversion",
                    listOfNotNull(conversion.outputValue?.let {
                        PhysicsSolutionStep(
                            "Convert through SI",
                            "${conversion.inputValue} ${conversion.inputUnit} = $it ${conversion.outputUnit}",
                            conversion.message,
                            conversion.verified,
                        )
                    }),
                    conversion.verified,
                    conversion.outputValue?.let { "$it ${conversion.outputUnit}" } ?: conversion.message,
                    if (conversion.verified) emptyList() else listOf(conversion.message),
                    now,
                )
            }
            PhysicsActionType.ANALYZE_VECTOR -> runCatching {
                val vector = PhysicsVectorAdapter.analyze(element.displaySource)
                result(
                    element, action, "Vector analysis",
                    listOf(
                        PhysicsSolutionStep("Components", vector.components.toString(), "Parsed through the existing vector model.", true),
                        PhysicsSolutionStep("Magnitude", vector.magnitude.toString(), "Euclidean magnitude.", true),
                        PhysicsSolutionStep("Direction", "${vector.directionDegrees} degrees", "Direction measured from the positive x-axis.", true),
                    ),
                    true, "Magnitude ${vector.magnitude}", emptyList(), now,
                )
            }.getOrElse {
                result(element, action, "Vector needs confirmation", emptyList(), false, it.message ?: "Confirm vector components.", emptyList(), now)
            }
            PhysicsActionType.DRAW_GRAPH -> return PhysicsActionOutcome(null, "graph2d", element.displaySource, "Opening the existing graph workspace.")
            PhysicsActionType.OPEN_2D -> return PhysicsActionOutcome(null, "geometry2d", element.displaySource, "Opening the existing 2D workspace.")
            PhysicsActionType.OPEN_3D -> return PhysicsActionOutcome(null, "geometry3d", element.displaySource, "Opening the existing 3D workspace.")
            PhysicsActionType.OPEN_CIRCUIT -> return PhysicsActionOutcome(null, "physics:circuit", element.displaySource, "Opening the existing circuit experience.")
            PhysicsActionType.OPEN_WAVE -> return PhysicsActionOutcome(null, "physics:wave", element.displaySource, "Opening the existing wave experience.")
            PhysicsActionType.OPEN_OPTICS -> return PhysicsActionOutcome(null, "physics:optics", element.displaySource, "Opening the existing optics experience.")
            PhysicsActionType.CHECK_SIGNIFICANT_FIGURES -> {
                val literals = Regex("""[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?""").findAll(element.displaySource).map { it.value }.toList()
                val figures = literals.map(PhysicsSignificantFigures::count)
                result(element, action, "Significant figures", figures.mapIndexed { i, count ->
                    PhysicsSolutionStep("Value ${i + 1}", literals[i], "$count significant figures", true)
                }, true, figures.minOrNull()?.let { "Limiting precision: $it significant figures" }.orEmpty(), emptyList(), now)
            }
            PhysicsActionType.ANALYZE_UNCERTAINTY -> {
                val values = Regex("""[+-]?(?:\d+(?:\.\d*)?|\.\d+)""").findAll(element.displaySource).mapNotNull { it.value.toDoubleOrNull() }.toList()
                if (values.size < 2) result(element, action, "Experimental analysis needs data", emptyList(), false, "Enter at least two measurements.", emptyList(), now)
                else {
                    val stats = PhysicsUncertaintyAdapter.summarize(values)
                    result(element, action, "Measurement analysis", listOf(
                        PhysicsSolutionStep("Mean", stats.mean.toString(), "Arithmetic mean from the existing statistics engine.", true),
                        PhysicsSolutionStep("Sample spread", stats.sampleStandardDeviation.toString(), "Sample standard deviation.", true),
                        PhysicsSolutionStep("Standard error", stats.standardError.toString(), "Uncertainty of the mean.", true),
                    ), true, "Mean ${stats.mean} ± ${stats.absoluteUncertainty}", emptyList(), now)
                }
            }
            PhysicsActionType.ANALYZE_EXPERIMENT -> {
                val pairs = element.displaySource.lines().mapNotNull { line ->
                    val values = Regex("""[+-]?(?:\d+(?:\.\d*)?|\.\d+)""").findAll(line).mapNotNull { it.value.toDoubleOrNull() }.toList()
                    values.takeIf { it.size >= 2 }?.let { it[0] to it[1] }
                }
                if (pairs.size < 3) {
                    result(element, action, "Experimental analysis needs data", emptyList(), false, "Enter at least three x,y measurement pairs.", emptyList(), now)
                } else {
                    val regression = Phase4Statistics.linearRegression(pairs.map { it.first }, pairs.map { it.second })
                    val outlierThreshold = regression.residuals.map { kotlin.math.abs(it) }.average() * 2
                    val anomalous = regression.residuals.indices.filter { kotlin.math.abs(regression.residuals[it]) > outlierThreshold }
                    result(element, action, "Experimental best-fit analysis", listOf(
                        PhysicsSolutionStep("Best-fit line", "y = ${regression.coefficients[1]}x + ${regression.coefficients[0]}", "Existing linear-regression engine.", true),
                        PhysicsSolutionStep("Goodness of fit", "R² = ${regression.rSquared}", "Compare residual structure as well as R².", true),
                        PhysicsSolutionStep("Anomalous points", anomalous.joinToString().ifBlank { "none flagged" }, "Flagged only; points were not removed.", true),
                    ), true, "Gradient ${regression.coefficients[1]}", regression.diagnostics.filterNot { it.passed }.map { it.detail }, now)
                }
            }
            PhysicsActionType.VERIFY_WORK -> {
                val verification = verifier.verify(element.displaySource)
                result(
                    element, action, "Physics work verification",
                    verification.steps.mapIndexed { index, step ->
                        PhysicsSolutionStep(
                            "Line ${index + 1}: ${step.status.name.lowercase()}",
                            step.line,
                            step.feedback,
                            step.status == com.indianservers.smartboard.smartboard.models.PhysicsVerificationStatus.VALID,
                        )
                    },
                    verification.firstInvalidIndex == null,
                    verification.firstInvalidIndex?.let { "First invalid line: ${it + 1}" } ?: "No dimensionally invalid line was found.",
                    emptyList(),
                    now,
                )
            }
            PhysicsActionType.TUTOR_HINT, PhysicsActionType.NEXT_STEP -> {
                val response = tutor.hint(element.displaySource, action == PhysicsActionType.NEXT_STEP)
                result(
                    element, action, response.title,
                    response.guidance.mapIndexed { index, guidance ->
                        PhysicsSolutionStep("Guidance ${index + 1}", "", guidance, response.verified)
                    },
                    response.verified, response.guidance.firstOrNull().orEmpty(), response.warnings, now,
                )
            }
            else -> {
                val quantities = analysis.quantities.joinToString { quantity ->
                    "${quantity.symbol}${quantity.canonicalName?.let { " ($it)" }.orEmpty()}"
                }
                result(
                    element, action, action.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase),
                    analysis.equations.map { PhysicsSolutionStep("Matched formula", it.source, "From the reviewed offline formula registry.", true) },
                    analysis.ambiguities.isEmpty(),
                    quantities.ifBlank { "No physical quantities were confidently identified." },
                    analysis.warnings + analysis.ambiguities.map { it.message } +
                        PhysicsMisconceptionDetector.detect(element.displaySource).map { it.message },
                    now,
                )
            }
        }
        return PhysicsActionOutcome(result, message = result.title)
    }

    private fun result(
        source: PhysicsExpressionElement,
        action: PhysicsActionType,
        title: String,
        steps: List<PhysicsSolutionStep>,
        verified: Boolean,
        summary: String,
        warnings: List<String>,
        now: Long,
    ) = PhysicsResultElement(
        "physics-result-${UUID.randomUUID()}", listOf(source.id), action, title, null, null, emptyList(),
        summary.takeIf(String::isNotBlank), null, null, null, steps, emptyList(), warnings,
        PhysicsEngineMetadata(listOf("PhysicsBoardAnalyzer", "existing application engines"), true),
        if (verified) PhysicsResultStatus.VERIFIED else PhysicsResultStatus.NEEDS_CONFIRMATION,
        resultBounds(source.bounds, steps.size), now,
    )

    private fun resultBounds(source: SmartBoardBounds, steps: Int): SmartBoardBounds {
        val top = source.bottom + 20f
        return SmartBoardBounds(source.left, top, maxOf(source.right, source.left + 340f), top + 96f + steps.coerceAtMost(8) * 22f)
    }
}
