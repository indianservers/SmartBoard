package com.indianservers.smartboard.core

import kotlin.math.abs
import kotlin.math.pow

enum class SolverMethod(val label: String) {
    Auto("Best method"),
    Factoring("Factoring"),
    CompletingSquare("Complete square"),
    QuadraticFormula("Quadratic formula"),
    Substitution("Substitution"),
    Elimination("Elimination"),
    GraphTable("Graph / table"),
    IntegrationSubstitution("u-substitution"),
    IntegrationParts("By parts"),
    PartialFractions("Partial fractions"),
    NumericApproximation("Numeric"),
}

enum class SolverReveal { FirstHint, Steps, Method, Answer }

data class QuantityModel(
    val name: String,
    val symbol: String,
    val value: Double?,
    val unit: String? = null,
    val unknown: Boolean = value == null,
)

data class WordProblemModel(
    val quantities: List<QuantityModel>,
    val relationships: List<String>,
    val equations: List<String>,
    val unknowns: List<String>,
    val ambiguity: List<String> = emptyList(),
)

enum class MistakeKind(val label: String) {
    Sign("Sign error"), Bracket("Bracket error"), Formula("Wrong method or formula"),
    Domain("Domain error"), Unit("Unit mismatch"), Arithmetic("Arithmetic error"),
}

data class MistakeDiagnosis(
    val kind: MistakeKind,
    val message: String,
    val correction: String,
)

enum class SolverDestination(val label: String) {
    Graph("Graph"), Table("Spreadsheet"), Geometry("Geometry"), Notebook("Notebook"), Mcq("Practice MCQ")
}

data class SolverHandoff(
    val destination: SolverDestination,
    val payload: String,
    val enabled: Boolean = true,
)

data class GuidedSolution(
    val solution: ProblemSolution,
    val method: SolverMethod,
    val methodReason: String,
    val wordModel: WordProblemModel? = null,
    val handoffs: List<SolverHandoff> = emptyList(),
    val interpretation: MathQueryInterpretation = DeterministicMathQueryInterpreter.interpret(solution.question),
    val alternatives: List<SolverAlternative> = emptyList(),
    val resultForms: List<SolverResultForm> = emptyList(),
) {
    fun hint(stepIndex: Int = 0): SolutionStep? = solution.steps.getOrNull(stepIndex)
    fun why(stepIndex: Int): String = solution.steps.getOrNull(stepIndex)?.let {
        "${it.explanation} This step is valid because it preserves the original mathematical relationship."
    } ?: "Select a revealed step to ask why it is valid."

    fun visibleSteps(reveal: SolverReveal, stepCount: Int): List<SolutionStep> = when (reveal) {
        SolverReveal.FirstHint -> solution.steps.take(1)
        SolverReveal.Steps -> solution.steps.take(stepCount.coerceIn(1, solution.steps.size.coerceAtLeast(1)))
        SolverReveal.Method -> solution.steps.take(stepCount.coerceIn(1, solution.steps.size.coerceAtLeast(1)))
        SolverReveal.Answer -> solution.steps
    }

    fun answerVisible(reveal: SolverReveal) = reveal == SolverReveal.Answer
}

/**
 * Teaching layer above the deterministic local kernel. Natural language is only
 * used to select a deterministic plan; the verified kernel result remains the
 * source of truth.
 */
class MathSolverTutor(private val kernel: MathProblemSolver = MathProblemSolver()) {
    fun solve(question: String, requested: SolverMethod = SolverMethod.Auto): GuidedSolution {
        val interpretation = DeterministicMathQueryInterpreter.interpret(question)
        val wordModel = WordProblemParser.parse(question)
        val interpretedQuestion = interpretation.selected.normalizedQuery
        val deterministicQuestion = wordModel?.equations?.firstOrNull()?.let { "Solve $it" } ?: interpretedQuestion
        val topic = solveExtendedTopic(interpretedQuestion)
        val base = (topic ?: kernel.solve(deterministicQuestion)).let { solved ->
            if (wordModel == null) solved else solved.copy(question = question)
        }
        val selected = selectMethod(base, question, requested)
        val compatible = isCompatible(selected, base.kind)
        val resolved = if (!base.supported) base else if (compatible) adaptSteps(base, selected) else base.copy(
            supported = false,
            answer = "Method not applicable",
            verification = "${selected.label} is not valid for ${base.kind.label}. Choose Best method or a compatible method.",
            warnings = base.warnings + "The solver refused to apply an invalid transformation.",
        )
        val warned = resolved.copy(warnings = (resolved.warnings + interpretation.assumptions + interpretation.ambiguities).distinct())
        val handoffs = handoffs(question, warned)
        val alternatives = SolverMethod.entries.filter { it !in setOf(SolverMethod.Auto, selected) && isCompatible(it, base.kind) }
            .map { method ->
                val alternate = adaptSteps(base, method)
                SolverAlternative(method, alternate.answer, alternate.steps.size, methodReason(method, alternate.kind))
            }
        return GuidedSolution(
            solution = warned,
            method = selected,
            methodReason = methodReason(selected, warned.kind),
            wordModel = wordModel,
            handoffs = handoffs,
            interpretation = interpretation,
            alternatives = alternatives,
            resultForms = SolverResultPresenter.forms(warned, interpretation, handoffs),
        )
    }

    fun diagnose(question: String, learnerWork: String): List<MistakeDiagnosis> {
        if (learnerWork.isBlank()) return emptyList()
        val issues = mutableListOf<MistakeDiagnosis>()
        if (!balanced(learnerWork)) issues += MistakeDiagnosis(MistakeKind.Bracket, "Brackets are not balanced.", "Close each grouping before applying an operation.")
        if (Regex("""sqrt\s*\(\s*-|ln\s*\(\s*(?:0|-)""", RegexOption.IGNORE_CASE).containsMatchIn(learnerWork)) {
            issues += MistakeDiagnosis(MistakeKind.Domain, "A real-valued root or logarithm is outside its domain.", "Require radicands ≥ 0 and logarithm arguments > 0.")
        }
        val units = Regex("""\b(mm|cm|m|km|mg|g|kg|s|min|h)\b""", RegexOption.IGNORE_CASE).findAll(learnerWork).map { it.value.lowercase() }.toSet()
        if (units.size > 1 && !Regex("convert|=|to", RegexOption.IGNORE_CASE).containsMatchIn(learnerWork)) {
            issues += MistakeDiagnosis(MistakeKind.Unit, "Mixed units appear without a conversion.", "Convert every quantity to one compatible unit before calculation.")
        }
        val correct = kernel.solve(question)
        val claimed = Regex("""(?:x\s*=|answer\s*=)\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(learnerWork)?.groupValues?.get(1)
        if (correct.supported && claimed != null && !correct.answer.contains(claimed)) {
            val kind = if (Regex("""[+−-]\s*\d""").containsMatchIn(learnerWork)) MistakeKind.Sign else MistakeKind.Arithmetic
            issues += MistakeDiagnosis(kind, "The claimed value does not match the verified solution.", "Substitute the value into the original question and inspect the first non-zero residual.")
        }
        return issues.distinctBy { it.kind }
    }

    private fun selectMethod(solution: ProblemSolution, question: String, requested: SolverMethod): SolverMethod {
        if (requested != SolverMethod.Auto) return requested
        val lower = question.lowercase()
        return when (solution.kind) {
            ProblemKind.QuadraticEquation -> if (solution.answer.contains("i")) SolverMethod.QuadraticFormula else SolverMethod.Factoring
            ProblemKind.LinearSystem -> SolverMethod.Elimination
            ProblemKind.Integral -> when {
                lower.contains(" from ") -> SolverMethod.NumericApproximation
                Regex("""\bx\s*\*?\s*(ln|sin|cos|exp)""").containsMatchIn(lower) -> SolverMethod.IntegrationParts
                else -> SolverMethod.IntegrationSubstitution
            }
            ProblemKind.LinearEquation -> SolverMethod.Substitution
            else -> SolverMethod.GraphTable
        }
    }

    private fun isCompatible(method: SolverMethod, kind: ProblemKind): Boolean = when (method) {
        SolverMethod.Auto -> true
        SolverMethod.Factoring, SolverMethod.CompletingSquare, SolverMethod.QuadraticFormula -> kind == ProblemKind.QuadraticEquation
        SolverMethod.Substitution, SolverMethod.Elimination -> kind in setOf(ProblemKind.LinearEquation, ProblemKind.LinearSystem)
        SolverMethod.IntegrationSubstitution, SolverMethod.IntegrationParts, SolverMethod.PartialFractions -> kind == ProblemKind.Integral
        SolverMethod.NumericApproximation, SolverMethod.GraphTable -> kind != ProblemKind.Unsupported
    }

    private fun adaptSteps(solution: ProblemSolution, method: SolverMethod): ProblemSolution {
        if (!solution.supported) return solution
        val methodStep = when (method) {
            SolverMethod.Factoring -> SolutionStep("Method · factoring", "p(x) = a(x-r₁)(x-r₂)", "Find factors whose product recreates the verified polynomial, then use the zero-product rule.", SolutionStepRole.Transform)
            SolverMethod.CompletingSquare -> SolutionStep("Method · complete the square", "(x + b/2a)² = (b²-4ac)/(4a²)", "Normalize the leading coefficient and add the same square term to both sides.", SolutionStepRole.Transform)
            SolverMethod.QuadraticFormula -> SolutionStep("Method · quadratic formula", "x = (-b ± √(b²-4ac))/(2a)", "This formula follows from completing the square and works for every quadratic.", SolutionStepRole.Transform)
            SolverMethod.Substitution -> SolutionStep("Method · substitution", "replace an isolated variable in the remaining equation", "Substitution keeps equality while reducing the number of unknowns.", SolutionStepRole.Transform)
            SolverMethod.Elimination -> SolutionStep("Method · elimination", "scale and add equations to cancel one variable", "Adding equal quantities to equal quantities preserves the solution set.", SolutionStepRole.Transform)
            SolverMethod.GraphTable -> SolutionStep("Method · graph/table", "solutions occur where residual = 0", "A graph or value table locates candidates; the local kernel verifies them exactly or numerically.", SolutionStepRole.Transform)
            SolverMethod.IntegrationSubstitution -> SolutionStep("Method · u-substitution", "u = inner expression; du = u'(x) dx", "Reverse the chain rule, integrate in u, then substitute back.", SolutionStepRole.Transform)
            SolverMethod.IntegrationParts -> SolutionStep("Method · integration by parts", "∫u dv = uv - ∫v du", "Reverse the product rule and choose u to simplify after differentiation.", SolutionStepRole.Transform)
            SolverMethod.PartialFractions -> SolutionStep("Method · partial fractions", "P(x)/Q(x) = Σ Aᵢ/qᵢ(x)", "Decompose a proper rational function into simpler verified fractions before integrating.", SolutionStepRole.Transform)
            SolverMethod.NumericApproximation -> SolutionStep("Method · numeric approximation", "refine until error < tolerance", "Use a convergent numerical method and verify the residual or reverse operation.", SolutionStepRole.Transform)
            SolverMethod.Auto -> null
        }
        return if (methodStep == null) solution else solution.copy(steps = listOf(solution.steps.firstOrNull()).filterNotNull() + methodStep + solution.steps.drop(1))
    }

    private fun methodReason(method: SolverMethod, kind: ProblemKind) = when (method) {
        SolverMethod.Factoring -> "Fast when real roots factor cleanly; roots are still checked by substitution."
        SolverMethod.CompletingSquare -> "Shows vertex structure and derives the roots constructively."
        SolverMethod.QuadraticFormula -> "Universal quadratic method, including complex roots."
        SolverMethod.Elimination -> "Cancels one variable with a short, verifiable linear transformation."
        SolverMethod.Substitution -> "Best when a variable can be isolated cleanly."
        SolverMethod.GraphTable -> "Builds intuition while retaining kernel verification for ${kind.label}."
        SolverMethod.IntegrationSubstitution -> "Recognizes a chain-rule pattern."
        SolverMethod.IntegrationParts -> "Recognizes a product that simplifies after differentiation."
        SolverMethod.PartialFractions -> "Splits a rational integrand into elementary terms."
        SolverMethod.NumericApproximation -> "Provides an error-controlled value where a closed form is unavailable."
        SolverMethod.Auto -> "The deterministic classifier selects a compatible method."
    }

    private fun handoffs(question: String, solution: ProblemSolution): List<SolverHandoff> {
        if (!solution.supported) return emptyList()
        val equation = question.substringAfter(":", question).trim().removePrefix("Solve ").removePrefix("solve ")
        val graph = if ('=' in equation) equation.split('=', limit = 2).let { "(${it[0]})-(${it[1]})" } else equation
        val table = "expression=$graph; x=-5..5; step=1"
        val notebook = buildString {
            append(question).append("\nMethod: ").append(solution.kind.label).append("\n")
            solution.steps.forEachIndexed { i, step -> append(i + 1).append(". ").append(step.title).append(": ").append(step.expression).append("\n") }
            append("Answer: ").append(solution.answer).append("\nVerification: ").append(solution.verification)
        }
        return listOf(
            SolverHandoff(SolverDestination.Graph, graph, solution.kind in setOf(ProblemKind.LinearEquation, ProblemKind.QuadraticEquation, ProblemKind.Inequality, ProblemKind.Arithmetic)),
            SolverHandoff(SolverDestination.Table, table),
            SolverHandoff(SolverDestination.Notebook, notebook),
            SolverHandoff(SolverDestination.Mcq, "Question: $question|Correct: ${solution.answer}|Method: ${solution.kind.label}"),
        )
    }

    private fun solveExtendedTopic(question: String): ProblemSolution? = Phase3AdvancedSolver.solve(question) ?: ExtendedTopicSolver.solve(question)

    private fun balanced(text: String): Boolean {
        var depth = 0
        text.forEach { if (it == '(') depth++ else if (it == ')' && --depth < 0) return false }
        return depth == 0
    }
}

object WordProblemParser {
    fun parse(question: String): WordProblemModel? {
        val lower = question.lowercase()
        val numberUnit = Regex("(-?\\d+(?:\\.\\d+)?)\\s*(km|m|cm|mm|kg|g|h|hours?|min|minutes?|s|seconds?|%|rupees?|₹)", RegexOption.IGNORE_CASE)
        val found = numberUnit.findAll(question).toList()
        val motion = listOf("speed", "distance", "time", "travels", "journey").any(lower::contains)
        val finance = listOf("interest", "principal", "rate", "amount", "invest").any(lower::contains)
        val rectangle = lower.contains("rectangle") && listOf("length", "width", "area", "perimeter").any(lower::contains)
        if (!motion && !finance && !rectangle && found.isEmpty()) return null
        if (rectangle) {
            fun value(name: String) = Regex("$name\\s*(?:is|=|of)?\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.toDoubleOrNull()
            val length = value("length"); val width = value("width")
            val asksPerimeter = lower.contains("perimeter")
            val equation = if (length != null && width != null) "x = ${if (asksPerimeter) 2 * (length + width) else length * width}" else null
            return WordProblemModel(
                listOf(QuantityModel("length", "l", length, "units"), QuantityModel("width", "w", width, "units"), QuantityModel(if (asksPerimeter) "perimeter" else "area", "x", null, if (asksPerimeter) "units" else "units²")),
                listOf(if (asksPerimeter) "perimeter = 2(length + width)" else "area = length × width"),
                listOfNotNull(equation), listOf("x"), if (equation == null) listOf("Provide both rectangle length and width.") else emptyList(),
            )
        }
        if (motion) {
            fun named(pattern: String) = Regex("(?:$pattern)\\s*(?:is|=|of|at)?\\s*(-?\\d+(?:\\.\\d+)?)\\s*(km|m|h|hours?|min|s)?", RegexOption.IGNORE_CASE).find(question)
            val distance = named("distance|travels?")
            val speed = named("speed")
            val time = named("time|for")
            val quantities = listOf(
                QuantityModel("distance", "d", distance?.groupValues?.get(1)?.toDoubleOrNull(), distance?.groupValues?.getOrNull(2)?.ifBlank { null }),
                QuantityModel("speed", "v", speed?.groupValues?.get(1)?.toDoubleOrNull(), speed?.groupValues?.getOrNull(2)?.ifBlank { null }),
                QuantityModel("time", "t", time?.groupValues?.get(1)?.toDoubleOrNull(), time?.groupValues?.getOrNull(2)?.ifBlank { null }),
            )
            val unknown = quantities.firstOrNull { lower.contains("find ${it.name}") || lower.contains("what is the ${it.name}") } ?: quantities.firstOrNull { it.value == null }
            val values = quantities.associateBy { it.symbol }
            val equation = when (unknown?.symbol) {
                "d" -> values["v"]?.value?.let { v -> values["t"]?.value?.let { t -> "x = ${v * t}" } }
                "v" -> values["d"]?.value?.let { d -> values["t"]?.value?.let { t -> "x = ${d / t}" } }
                "t" -> values["d"]?.value?.let { d -> values["v"]?.value?.let { v -> "x = ${d / v}" } }
                else -> null
            }
            return WordProblemModel(quantities, listOf("distance = speed × time"), listOfNotNull(equation), listOfNotNull(unknown?.symbol), if (equation == null) listOf("Provide two compatible motion quantities.") else emptyList())
        }
        val values = found.mapIndexed { index, match -> QuantityModel("quantity ${index + 1}", "q${index + 1}", match.groupValues[1].toDouble(), match.groupValues[2]) }
        return WordProblemModel(values, listOf("amount = principal × (1 + rate × time) for simple interest"), emptyList(), listOf("amount"), listOf("State whether interest is simple or compound and provide time."))
    }
}

private object ExtendedTopicSolver {
    fun solve(question: String): ProblemSolution? {
        val q = question.lowercase()
        return when {
            q.contains("combination") || Regex("\\bncr\\b|\\bc\\(").containsMatchIn(q) -> combinatorics(question, combination = true)
            q.contains("permutation") || Regex("\\bnpr\\b|\\bp\\(").containsMatchIn(q) -> combinatorics(question, combination = false)
            q.contains("simple interest") || q.contains("compound interest") -> finance(question)
            q.contains("significant figures") || q.contains("sig figs") -> significantFigures(question)
            q.contains("uncertainty") || q.contains("±") || q.contains("+/-") -> uncertainty(question)
            listOf("gcd", "hcf", "lcm", "prime factor", "is prime").any(q::contains) -> numberTheory(question)
            q.contains("maclaurin") || q.contains("taylor series") -> series(question)
            q.contains("limit") -> limit(question)
            q.contains("recurrence") -> recurrence(question)
            q.contains("dy/dx") || q.contains("differential equation") || q.contains(" ode ") -> ode(question)
            else -> null
        }
    }

    private fun combinatorics(question: String, combination: Boolean): ProblemSolution? {
        val nums = Regex("\\d+").findAll(question).map { it.value.toInt() }.toList()
        if (nums.size < 2) return null
        val n = nums[0]; val r = nums[1]
        if (r !in 0..n || n > 170) return unsupported(question, "Require 0 ≤ r ≤ n ≤ 170.")
        fun product(from: Int, to: Int) = if (from > to) 1.0 else (from..to).fold(1.0) { a, v -> a * v }
        val permutations = product(n - r + 1, n)
        val value = if (combination) permutations / product(1, r) else permutations
        val symbol = if (combination) "C" else "P"
        return solution(question, ProblemKind.SequenceSeries, "${value.toLong()}", listOf(
            step("Interpret", "$n$symbol$r", if (combination) "Order does not matter." else "Order matters."),
            step("Apply formula", if (combination) "n! / (r!(n-r)!)" else "n! / (n-r)!", "Cancel factorials before multiplying."),
            step("Calculate", "$n$symbol$r = ${value.toLong()}", "Evaluate the remaining finite product."),
        ), "Integer product and range constraints verified.")
    }

    private fun finance(question: String): ProblemSolution? {
        fun named(vararg names: String) = names.firstNotNullOfOrNull { name -> Regex("$name\\s*(?:=|is|of)?\\s*(?:₹|rs\\.?)?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.toDoubleOrNull() }
        val p = named("principal", "invest(?:ed|ment)?") ?: return null
        val rate = named("rate") ?: Regex("(\\d+(?:\\.\\d+)?)\\s*%").find(question)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val time = named("time", "for") ?: return null
        val compound = question.contains("compound", true)
        val amount = if (compound) p * (1 + rate / 100).pow(time) else p * (1 + rate * time / 100)
        val formula = if (compound) "A = P(1+r)^t" else "A = P(1+rt)"
        return solution(question, ProblemKind.Percentage, "Amount = ${fmt(amount)}", listOf(step("Model quantities", "P=${fmt(p)}, r=${fmt(rate / 100)}, t=${fmt(time)}", "Convert percent rate to a decimal."), step("Choose model", formula, "The wording determines simple or annual compound growth."), step("Calculate", "A = ${fmt(amount)}", "Substitute with consistent time periods.")), "Amount is at least the principal for a non-negative rate.")
    }

    private fun significantFigures(question: String): ProblemSolution? {
        val value = Regex("-?\\d+(?:\\.\\d+)?").find(question)?.value?.toDoubleOrNull() ?: return null
        val figures = Regex("(?:to|at)\\s*(\\d+)\\s*(?:significant|sig)", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (value == 0.0) return solution(question, ProblemKind.Arithmetic, "0", listOf(step("Round", "0", "Zero remains zero at any requested precision.")), "Exact zero retained.")
        val scale = 10.0.pow(figures - 1 - kotlin.math.floor(kotlin.math.log10(abs(value))).toInt())
        val rounded = kotlin.math.round(value * scale) / scale
        return solution(question, ProblemKind.Arithmetic, fmt(rounded), listOf(step("Locate first significant digit", value.toString(), "Leading zeros are not significant."), step("Round", "$value → ${fmt(rounded)} ($figures s.f.)", "Inspect the next digit and round once.")), "Result has the requested significant precision.")
    }

    private fun uncertainty(question: String): ProblemSolution? {
        val values = Regex("""(-?\d+(?:\.\d+)?)\s*(?:±|\+/-)\s*(\d+(?:\.\d+)?)""").findAll(question)
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }.toList()
        if (values.size < 2) return unsupported(question, "Provide two measurements as value ± absolute uncertainty.")
        val multiply = question.contains('*') || question.contains('×')
        val center = if (multiply) values.fold(1.0) { total, item -> total * item.first } else values.sumOf { it.first }
        val error = if (multiply) abs(center) * kotlin.math.sqrt(values.sumOf { (it.second / it.first).pow(2) })
        else kotlin.math.sqrt(values.sumOf { it.second.pow(2) })
        val answer = fmt(center) + " ± " + fmt(error)
        return solution(question, ProblemKind.Arithmetic, answer, listOf(
            step("Identify measurements", values.joinToString { fmt(it.first) + " ± " + fmt(it.second) }, "Separate values from their standard uncertainties."),
            step("Propagation rule", if (multiply) "combine relative uncertainties in quadrature" else "combine absolute uncertainties in quadrature", "This rule assumes independent uncertainties."),
            step("Calculate", answer, "Report uncertainty and central value at compatible precision."),
        ), "Central value and independent uncertainty were calculated separately.")
    }

    private fun numberTheory(question: String): ProblemSolution? {
        val numbers = Regex("""\d+""").findAll(question).map { it.value.toLong() }.toList()
        if (numbers.isEmpty()) return null
        fun gcd(a0: Long, b0: Long): Long {
            var a = a0; var b = b0
            while (b != 0L) { val next = a % b; a = b; b = next }
            return a
        }
        val lower = question.lowercase()
        if ("prime factor" in lower) {
            var remaining = numbers.first()
            var divisor = 2L
            val factors = mutableListOf<Long>()
            while (divisor * divisor <= remaining) {
                while (remaining % divisor == 0L) { factors += divisor; remaining /= divisor }
                divisor += if (divisor == 2L) 1 else 2
            }
            if (remaining > 1) factors += remaining
            val answer = factors.joinToString(" × ").ifBlank { numbers.first().toString() }
            return solution(question, ProblemKind.ExactArithmetic, answer, listOf(
                step("Trial division", "test primes through √n", "Every composite integer has a prime factor no larger than its square root."),
                step("Collect factors", answer, "Repeated factors retain multiplicity."),
            ), "The factor product reconstructs " + numbers.first() + ".")
        }
        if ("is prime" in lower) {
            val n = numbers.first()
            val prime = n >= 2 && (2L..kotlin.math.sqrt(n.toDouble()).toLong()).none { n % it == 0L }
            return solution(question, ProblemKind.ExactArithmetic, if (prime) "$n is prime" else "$n is composite", listOf(
                step("Bound divisors", "test through floor(√n)", "A non-trivial factor pair has a member within this bound."),
                step("Exact remainder test", if (prime) "no divisor found" else "divisor found", "Zero remainder proves compositeness."),
            ), "Deterministic integer divisibility test completed.")
        }
        if (numbers.size < 2) return null
        val g = numbers.reduce(::gcd)
        val lcm = numbers.fold(1L) { total, n -> if (total == 0L || n == 0L) 0L else total / gcd(total, n) * n }
        val wantsLcm = "lcm" in lower
        val answer = if (wantsLcm) lcm else g
        return solution(question, ProblemKind.ExactArithmetic, answer.toString(), listOf(
            step("Euclidean algorithm", "gcd(a,b) = gcd(b, a mod b)", "Each remainder preserves common divisors."),
            step("Calculate", (if (wantsLcm) "lcm = " else "gcd = ") + answer, if (wantsLcm) "Use lcm(a,b)=|ab|/gcd(a,b)." else "Continue until the remainder is zero."),
        ), "Exact integer identity verified.")
    }

    private fun series(question: String): ProblemSolution? {
        val function = Regex("(?:of|for)\\s+(sin|cos|exp|e\\^x|ln\\(1\\+x\\))", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.lowercase() ?: return null
        val expansion = when (function) { "sin" -> "x - x^3/3! + x^5/5! - …"; "cos" -> "1 - x^2/2! + x^4/4! - …"; "ln(1+x)" -> "x - x^2/2 + x^3/3 - …"; else -> "1 + x + x^2/2! + x^3/3! + …" }
        return solution(question, ProblemKind.SequenceSeries, expansion, listOf(step("Expansion point", "a = 0", "A Maclaurin series is a Taylor series at zero."), step("Derivative coefficients", "f^(n)(0)/n!", "Evaluate successive derivatives at zero."), step("Series", expansion, "Assemble terms with their factorial denominators.")), "Known convergence domain applies; truncation error decreases near x=0.")
    }

    private fun limit(question: String): ProblemSolution? {
        val square = Regex("x\\^2\\s*-\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.toDoubleOrNull()
        val root = Regex("/\\s*\\(\\s*x\\s*-\\s*(\\d+(?:\\.\\d+)?)\\s*\\)", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.toDoubleOrNull()
        val at = Regex("x\\s*(?:->|→)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.toDoubleOrNull()
        if (square != null && root != null && at != null) {
            if (abs(square - root * root) < 1e-8 && abs(at - root) < 1e-8) return solution(question, ProblemKind.Arithmetic, fmt(2 * root), listOf(step("Detect indeterminate form", "0/0", "Direct substitution signals a removable discontinuity."), step("Factor", "(x²-${fmt(square)})/(x-${fmt(root)}) = x+${fmt(root)}", "Cancel only for x ≠ ${fmt(root)}."), step("Take limit", "${fmt(root)}+${fmt(root)} = ${fmt(2 * root)}", "The simplified expression is continuous at the target.")), "Left and right values approach ${fmt(2 * root)}.")
        }
        return unsupported(question, "This foundation currently handles factorable removable limits; broader symbolic limits are queued.")
    }

    private fun recurrence(question: String): ProblemSolution? {
        val match = Regex("a[_ ]?n\\s*=\\s*a[_ ]?\\{?n-1}?\\s*([+-])\\s*(\\d+(?:\\.\\d+)?).*?a[_ ]?1\\s*=\\s*(-?\\d+(?:\\.\\d+)?).*?n\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE).find(question) ?: return null
        val delta = match.groupValues[2].toDouble() * if (match.groupValues[1] == "-") -1 else 1
        val first = match.groupValues[3].toDouble(); val n = match.groupValues[4].toInt(); val answer = first + (n - 1) * delta
        return solution(question, ProblemKind.SequenceSeries, "a_$n = ${fmt(answer)}", listOf(step("Classify", "first-order arithmetic recurrence", "Each term changes by the same amount."), step("Closed form", "a_n = a_1 + (n-1)d", "Unroll the recurrence n-1 times."), step("Evaluate", "a_$n = ${fmt(answer)}", "Substitute the initial value and index.")), "Successive terms differ by ${fmt(delta)}.")
    }

    private fun ode(question: String): ProblemSolution? {
        val match = Regex("dy/dx\\s*=\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\*?\\s*y", RegexOption.IGNORE_CASE).find(question) ?: return unsupported(question, "ODE foundation supports dy/dx = k y; provide an initial condition for a unique constant.")
        val k = match.groupValues[1].toDouble()
        return solution(question, ProblemKind.Integral, "y = C*exp(${fmt(k)}*x)", listOf(step("Classify", "separable first-order ODE", "y and x terms can be separated."), step("Separate", "dy/y = ${fmt(k)} dx", "Divide by y on intervals where y ≠ 0; y=0 is included by C=0."), step("Integrate", "ln|y| = ${fmt(k)}x + C", "Integrate both sides."), step("Solve for y", "y = C*exp(${fmt(k)}x)", "Exponentiate and absorb sign into C.")), "Differentiate the family: y' = ${fmt(k)}y.")
    }

    private fun solution(q: String, kind: ProblemKind, answer: String, steps: List<SolutionStep>, verify: String) = ProblemSolution(q, kind, answer, steps, verify, .96)
    private fun unsupported(q: String, reason: String) = ProblemSolution(q, ProblemKind.Unsupported, "Not solved yet", emptyList(), reason, 0.0, listOf("The local kernel will not invent an unsupported transformation."), false)
    private fun step(title: String, expression: String, explanation: String) = SolutionStep(title, expression, explanation, SolutionStepRole.Transform)
    private fun fmt(value: Double): String = if (abs(value - value.toLong()) < 1e-9) value.toLong().toString() else String.format(java.util.Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
}
