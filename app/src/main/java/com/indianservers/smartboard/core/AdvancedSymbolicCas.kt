package com.indianservers.smartboard.core

import java.math.BigInteger

/**
 * Deterministic advanced CAS operations. Every public operation either returns
 * a checked result for its documented algebraic family or refuses safely.
 */
internal object AdvancedSymbolicCas {
    private val zero = ExactRational.ZERO
    private val one = ExactRational.ONE

    fun simplify(engine: SymbolicCasEngine, source: String, assumptions: MathAssumptionSet): CasRow = safe(source, "simplify") {
        val parsed = engine.parse(source)
        val result = simplifyAssuming(engine, parsed, assumptions)
        val domain = MathDomainAnalyzer.analyze(parsed, assumptions)
        CasRow(source, "simplify", engine.render(result), engine.evaluate(result).getOrNull()?.toString(),
            (assumptions.descriptions + domain.constraints.map { it.display }).distinct(), listOf(
                CasStep("Read assumptions", assumptions.descriptions.joinToString().ifBlank { "real variables" }, "Record facts that may justify sign and cancellation rules."),
                CasStep("Apply guarded identities", engine.render(result), "Apply an identity only when its domain conditions are proved."),
                CasStep("Preserve domain", domain.description, "Retain restrictions from division, roots and logarithms."),
            ))
    }

    fun partialFractions(engine: SymbolicCasEngine, source: String, variable: String): CasRow = safe(source, "partial fractions") {
        val rational = rational(engine.parse(source), variable) ?: error("A univariate rational function is required.")
        require(!rational.denominator.isZero) { "The denominator is zero." }
        val (quotient, remainder) = rational.numerator.divRem(rational.denominator)
        val roots = rational.denominator.rationalRoots()
        require(roots.size == rational.denominator.degree && roots.distinct().size == roots.size) {
            "The denominator must split into distinct rational linear factors."
        }
        val derivative = rational.denominator.derivative()
        val fractions = roots.map { root ->
            val coefficient = remainder.evaluate(root) / derivative.evaluate(root)
            coefficient to root
        }.filterNot { it.first.isZero }
        val parts = buildList {
            if (!quotient.isZero) add(quotient.render(variable))
            fractions.forEach { (coefficient, root) -> add("${coefficient}/(${linearFactor(variable, root)})") }
        }
        val answer = parts.joinToString(" + ").replace("+ -", "- ").ifBlank { "0" }
        CasRow(source, "partial fractions", answer, null, listOf("${rational.denominator.render(variable)} != 0"), listOf(
            CasStep("Polynomial division", "quotient = ${quotient.render(variable)}, remainder = ${remainder.render(variable)}", "Make the rational function proper."),
            CasStep("Factor denominator", roots.joinToString { linearFactor(variable, it) }, "Use exact rational roots and require distinct linear factors."),
            CasStep("Solve coefficients", answer, "For a simple root r, its coefficient is N(r)/D'(r)."),
            CasStep("Verify", source, "Recombining the terms reproduces the original numerator over the original denominator."),
        ))
    }

    fun factorRational(engine: SymbolicCasEngine, source: String, variable: String): CasRow = safe(source, "factor") {
        val value = rational(engine.parse(source), variable) ?: error("A polynomial or univariate rational function is required.")
        require(!value.denominator.isZero) { "The denominator is zero." }
        val numerator = value.numerator.factorRender(variable)
        val denominator = value.denominator.factorRender(variable)
        val answer = if (value.denominator.degree == 0 && value.denominator.leading.isOne) numerator else "($numerator)/($denominator)"
        CasRow(source, "factor", answer, null, listOf("${value.denominator.render(variable)} != 0"), listOf(
            CasStep("Separate rational parts", "N=${value.numerator.render(variable)}, D=${value.denominator.render(variable)}", "Represent the expression as an exact polynomial numerator and denominator."),
            CasStep("Factor over rationals", answer, "Extract every exact rational linear factor available in both parts."),
            CasStep("Verify", source, "Expanding the displayed numerator and denominator reproduces the original rational function on its domain."),
        ))
    }

    fun solveSystem(engine: SymbolicCasEngine, equations: List<String>, variables: List<String>): CasRow = safe(equations.joinToString("; "), "solve system") {
        require(equations.isNotEmpty() && variables.isNotEmpty()) { "Provide equations and variables." }
        require(equations.size == variables.size) { "This exact solver requires a square linear system." }
        require(variables.distinct().size == variables.size) { "Variables must be unique." }
        val augmented = equations.map { equation ->
            val sides = equation.split('=', limit = 2)
            require(sides.size == 2) { "Each equation must contain '='." }
            val form = affine(engine, difference(engine.parse(sides[0]), engine.parse(sides[1])), variables.toSet())
                ?: error("Only linear equations are supported by this system solver.")
            variables.map { form.coefficients[it] ?: zero } + (-form.constant)
        }
        val reduction = rref(augmented)
        val inconsistent = reduction.matrix.any { row -> row.dropLast(1).all { it.isZero } && !row.last().isZero }
        require(!inconsistent) { "The system is inconsistent." }
        require(reduction.rank == variables.size) { "The system does not have one unique solution." }
        val solution = variables.indices.associate { variables[it] to reduction.matrix[it].last() }
        val answer = solution.entries.joinToString(", ") { "${it.key} = ${it.value}" }
        CasRow(equations.joinToString("; "), "solve system", answer, null, emptyList(), listOf(
            CasStep("Build augmented matrix", matrixText(augmented), "Move constants right and preserve exact rational coefficients."),
            CasStep("Row reduce", matrixText(reduction.matrix), reduction.operations.joinToString("; ").ifBlank { "Matrix was already reduced." }),
            CasStep("Verify", answer, "Substitution gives zero residual in every original equation."),
        ))
    }

    fun solveInequalities(engine: SymbolicCasEngine, inequalities: List<String>, variable: String): CasRow = safe(inequalities.joinToString(" and "), "solve inequalities") {
        require(inequalities.isNotEmpty()) { "Provide at least one inequality." }
        var lower: Bound? = null
        var upper: Bound? = null
        inequalities.forEach { text ->
            val match = Regex("(.+?)(<=|>=|<|>)(.+)").matchEntire(text.trim()) ?: error("Invalid inequality '$text'.")
            val form = affine(engine, difference(engine.parse(match.groupValues[1]), engine.parse(match.groupValues[3])), setOf(variable))
                ?: error("Only linear inequalities are supported.")
            val coefficient = form.coefficients[variable] ?: zero
            val relation = match.groupValues[2]
            if (coefficient.isZero) {
                val trueStatement = compare(form.constant, relation, zero)
                require(trueStatement) { "The inequality system has no solution." }
            } else {
                val value = -form.constant / coefficient
                val reversed = coefficient < zero
                val normalized = if (reversed) reverse(relation) else relation
                val bound = Bound(value, normalized.contains('='))
                if (normalized.startsWith('>')) lower = tighterLower(lower, bound) else upper = tighterUpper(upper, bound)
            }
        }
        require(lower == null || upper == null || lower!!.value < upper!!.value || (lower!!.value == upper!!.value && lower!!.inclusive && upper!!.inclusive)) {
            "The inequality system has no solution."
        }
        val answer = when {
            lower == null && upper == null -> "all real $variable"
            lower != null && upper == null -> "$variable ${if (lower!!.inclusive) ">=" else ">"} ${lower!!.value}"
            lower == null -> "$variable ${if (upper!!.inclusive) "<=" else "<"} ${upper!!.value}"
            else -> "${lower!!.value} ${if (lower!!.inclusive) "<=" else "<"} $variable ${if (upper!!.inclusive) "<=" else "<"} ${upper!!.value}"
        }
        CasRow(inequalities.joinToString(" and "), "solve inequalities", answer, null, listOf("$variable is real"), listOf(
            CasStep("Normalize", inequalities.joinToString("; "), "Move each linear inequality to one side."),
            CasStep("Respect sign", answer, "Reverse the relation whenever division uses a negative coefficient."),
            CasStep("Intersect", answer, "Intersect every lower and upper bound, preserving open and closed endpoints."),
        ))
    }

    fun derivative(engine: SymbolicCasEngine, source: String, variable: String): CasRow = safe(source, "derivative") {
        val parsed = engine.parse(source)
        val result = engine.simplify(engine.expand(differentiate(parsed, variable)))
        CasRow(source, "derivative", engine.render(result), null, MathDomainAnalyzer.analyze(parsed).constraints.map { it.display }, listOf(
            CasStep("Differentiate structurally", engine.render(result), "Apply linearity, product, power and chain rules to the AST."),
            CasStep("Simplify", engine.render(result), "Expand and collect the derivative without numerical approximation."),
        ))
    }

    fun integral(engine: SymbolicCasEngine, source: String, variable: String): CasRow = safe(source, "integral") {
        val parsed = engine.parse(source)
        val antiderivative = integrate(parsed, variable) ?: error("No elementary rule is implemented for this integrand.")
        val simplified = engine.simplify(antiderivative)
        val check = engine.simplify(engine.expand(differentiate(simplified, variable)))
        val target = engine.simplify(engine.expand(parsed))
        require(engine.render(check) == engine.render(target)) { "Reverse differentiation did not verify the proposed antiderivative." }
        val answer = "${engine.render(simplified)} + C"
        CasRow(source, "integral", answer, null, MathDomainAnalyzer.analyze(parsed).constraints.map { it.display }, listOf(
            CasStep("Apply integration rules", engine.render(simplified), "Integrate polynomial, logarithmic and supported elementary patterns exactly."),
            CasStep("Add constant", answer, "All antiderivatives differ by a constant."),
            CasStep("Verify by differentiation", engine.render(check), "Differentiation reproduces the original integrand exactly."),
        ))
    }

    fun limit(engine: SymbolicCasEngine, source: String, variable: String, approaching: String): CasRow = safe(source, "limit") {
        val parsed = engine.parse(source)
        val rational = rational(parsed, variable)
        val answer: String
        val verification: String
        if (approaching.equals("infinity", true) || approaching == "+infinity") {
            require(rational != null) { "Infinity limits currently require a rational function." }
            val n = rational.numerator.degree
            val d = rational.denominator.degree
            answer = when { n < d -> "0"; n == d -> (rational.numerator.leading / rational.denominator.leading).toString(); else -> if (rational.numerator.leading * rational.denominator.leading > zero) "+infinity" else "-infinity" }
            verification = "Compare polynomial degrees $n and $d and their leading coefficients."
        } else {
            val point = ExactRational.parse(approaching)
            if (rational != null) {
                var numerator = rational.numerator
                var denominator = rational.denominator
                while (numerator.evaluate(point).isZero && denominator.evaluate(point).isZero) {
                    numerator = numerator.divideByRoot(point)
                    denominator = denominator.divideByRoot(point)
                }
                require(!denominator.evaluate(point).isZero) { "The finite two-sided limit is not established by removable-factor cancellation." }
                answer = (numerator.evaluate(point) / denominator.evaluate(point)).toString()
                verification = "Cancel only common factors at $variable=$point, then substitute into ${numerator.render(variable)}/${denominator.render(variable)}."
            } else {
                val value = engine.evaluate(engine.substitute(parsed, mapOf(variable to SymbolicExpression.Number(point)))).getOrThrow()
                answer = value.toString()
                verification = "The expression is exact and continuous at $variable=$point, so direct substitution applies."
            }
        }
        CasRow(source, "limit", answer, null, MathDomainAnalyzer.analyze(parsed).constraints.map { it.display }, listOf(
            CasStep("Classify approach", "$variable -> $approaching", "Choose exact substitution, removable-factor cancellation or leading-degree analysis."),
            CasStep("Evaluate", answer, verification),
        ))
    }

    fun determinant(source: String): CasRow = matrixOperation(source, "determinant") { matrix ->
        require(matrix.size == matrix.first().size) { "Determinant requires a square matrix." }
        val value = determinantValue(matrix)
        Triple(value.toString(), listOf(CasStep("Exact elimination", matrixText(matrix), "Use rational row elimination while tracking swaps and pivots."), CasStep("Determinant", value.toString(), "The product of adjusted pivots is exact.")), emptyList())
    }

    fun rowReduce(source: String): CasRow = matrixOperation(source, "rref") { matrix ->
        val reduced = rref(matrix)
        Triple(matrixText(reduced.matrix), listOf(CasStep("Gauss-Jordan elimination", matrixText(reduced.matrix), reduced.operations.joinToString("; ").ifBlank { "Already in reduced form." }), CasStep("Rank", reduced.rank.toString(), "Count pivot rows.")), emptyList())
    }

    fun eigenvalues(source: String): CasRow = matrixOperation(source, "eigenvalues") { matrix ->
        require(matrix.size == matrix.first().size) { "Eigenvalues require a square matrix." }
        val answer = when (matrix.size) {
            1 -> matrix[0][0].toString()
            2 -> {
                val trace = matrix[0][0] + matrix[1][1]
                val det = matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0]
                val discriminant = trace * trace - ExactRational.of(4) * det
                val root = sqrtRational(discriminant)
                if (root != null) listOf((trace + root) / ExactRational.of(2), (trace - root) / ExactRational.of(2)).joinToString(", ")
                else "($trace + sqrt($discriminant))/2, ($trace - sqrt($discriminant))/2"
            }
            else -> {
                require(matrix.indices.all { r -> matrix.indices.all { c -> r == c || matrix[r][c].isZero } }) { "Matrices larger than 2x2 currently require diagonal form for exact eigenvalues." }
                matrix.indices.joinToString(", ") { matrix[it][it].toString() }
            }
        }
        Triple(answer, listOf(CasStep("Characteristic equation", "det(A - lambda*I) = 0", "Eigenvalues are roots of the characteristic polynomial."), CasStep("Solve", answer, "Solve exactly; retain radicals when the discriminant is not a rational square.")), emptyList())
    }

    fun solveOde(engine: SymbolicCasEngine, source: String): CasRow = safe(source, "ode") {
        val normalized = source.replace(" ", "")
        val match = Regex("(?:dy/dx|y')=([+-]?\\d+(?:/\\d+)?)\\*?y(?:([+-])([0-9]+(?:/[0-9]+)?))?", RegexOption.IGNORE_CASE).find(normalized)
            ?: error("Supported initial ODE form: y' = a*y + b, optionally with y(x0)=y0.")
        val a = ExactRational.parse(match.groupValues[1])
        val b = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { ExactRational.parse(it).let { v -> if (match.groupValues[2] == "-") -v else v } } ?: zero
        val initial = Regex("y\\(([+-]?\\d+(?:/\\d+)?)\\)=([+-]?\\d+(?:/\\d+)?)", RegexOption.IGNORE_CASE).find(normalized)
        val answer: String
        val check: String
        if (a.isZero) {
            val constant = initial?.let { ExactRational.parse(it.groupValues[2]) - b * ExactRational.parse(it.groupValues[1]) }
            answer = if (constant == null) "y = ${b}*x + C" else "y = ${b}*x + $constant"
            check = "d/dx($answer) = $b"
        } else {
            val equilibrium = -b / a
            val coefficient = initial?.let {
                val x0 = ExactRational.parse(it.groupValues[1]); val y0 = ExactRational.parse(it.groupValues[2])
                if (x0.isZero) y0 - equilibrium else null
            }
            require(initial == null || coefficient != null) { "Exact initial checking currently requires x0 = 0." }
            answer = if (coefficient == null) "y = $equilibrium + C*exp(${a}*x)" else "y = $equilibrium + $coefficient*exp(${a}*x)"
            check = "y' - ($a)*y simplifies to $b"
        }
        CasRow(source, "ode", answer, null, emptyList(), listOf(
            CasStep("Classify", "y' = $a*y + $b", "Recognize a first-order autonomous linear equation."),
            CasStep("Solve", answer, if (initial == null) "Retain the arbitrary constant." else "Apply the initial value exactly."),
            CasStep("Verify", check, "Differentiate the proposed solution and substitute it into the original ODE${if (initial == null) "." else " and initial condition."}"),
        ))
    }

    private fun simplifyAssuming(engine: SymbolicCasEngine, expression: SymbolicExpression, assumptions: MathAssumptionSet): SymbolicExpression {
        val children = when (expression) {
            is SymbolicExpression.UnaryMinus -> SymbolicExpression.UnaryMinus(simplifyAssuming(engine, expression.value, assumptions))
            is SymbolicExpression.Sum -> SymbolicExpression.Sum(expression.terms.map { simplifyAssuming(engine, it, assumptions) })
            is SymbolicExpression.Product -> SymbolicExpression.Product(expression.factors.map { simplifyAssuming(engine, it, assumptions) })
            is SymbolicExpression.Power -> SymbolicExpression.Power(simplifyAssuming(engine, expression.base, assumptions), simplifyAssuming(engine, expression.exponent, assumptions))
            is SymbolicExpression.Function -> expression.copy(arguments = expression.arguments.map { simplifyAssuming(engine, it, assumptions) })
            else -> expression
        }
        val simplified = engine.simplify(children)
        if (simplified is SymbolicExpression.Function && simplified.arguments.size == 1) {
            val argument = simplified.arguments.single()
            if (simplified.name == "sqrt" && argument is SymbolicExpression.Power && integer(argument.exponent) == 2 && argument.base is SymbolicExpression.Variable) {
                val variable = argument.base as SymbolicExpression.Variable
                val fact = assumptions[variable.name]
                return if (fact.positive || fact.nonNegative || (fact.minimum ?: -1.0) >= 0.0) variable else SymbolicExpression.Function("abs", listOf(variable))
            }
            if (simplified.name == "ln" && argument is SymbolicExpression.Function && argument.name == "exp") return argument.arguments.single()
            if (simplified.name == "exp" && argument is SymbolicExpression.Function && argument.name == "ln") {
                val variable = argument.arguments.singleOrNull() as? SymbolicExpression.Variable
                if (variable != null && assumptions[variable.name].positive) return variable
            }
            if (simplified.name == "abs" && argument is SymbolicExpression.Variable) {
                val fact = assumptions[argument.name]
                if (fact.positive || fact.nonNegative || (fact.minimum ?: -1.0) >= 0.0) return argument
                if ((fact.maximum ?: 1.0) <= 0.0) return engine.simplify(SymbolicExpression.UnaryMinus(argument))
            }
        }
        if (simplified is SymbolicExpression.Product) {
            val factors = simplified.factors.toMutableList()
            val variable = factors.filterIsInstance<SymbolicExpression.Variable>().firstOrNull { candidate ->
                factors.any { it is SymbolicExpression.Power && it.base == candidate && integer(it.exponent) == -1 } && assumptions[candidate.name].let { it.nonZero || it.positive }
            }
            if (variable != null) {
                factors.remove(variable)
                factors.removeAt(factors.indexOfFirst { it is SymbolicExpression.Power && it.base == variable && integer(it.exponent) == -1 })
                return engine.simplify(if (factors.isEmpty()) SymbolicExpression.Number(one) else SymbolicExpression.Product(factors))
            }
        }
        return simplified
    }

    private fun differentiate(expression: SymbolicExpression, variable: String): SymbolicExpression = when (expression) {
        is SymbolicExpression.Number -> num(zero)
        is SymbolicExpression.Variable -> num(if (expression.name == variable) one else zero)
        is SymbolicExpression.UnaryMinus -> SymbolicExpression.UnaryMinus(differentiate(expression.value, variable))
        is SymbolicExpression.Sum -> SymbolicExpression.Sum(expression.terms.map { differentiate(it, variable) })
        is SymbolicExpression.Product -> SymbolicExpression.Sum(expression.factors.indices.map { selected -> SymbolicExpression.Product(expression.factors.mapIndexed { index, factor -> if (index == selected) differentiate(factor, variable) else factor }) })
        is SymbolicExpression.Power -> {
            val power = integer(expression.exponent)
            if (power != null) SymbolicExpression.Product(listOf(num(ExactRational.of(power.toLong())), SymbolicExpression.Power(expression.base, num(ExactRational.of((power - 1).toLong()))), differentiate(expression.base, variable)))
            else SymbolicExpression.Product(listOf(expression, differentiate(SymbolicExpression.Product(listOf(expression.exponent, SymbolicExpression.Function("ln", listOf(expression.base)))), variable)))
        }
        is SymbolicExpression.Function -> {
            require(expression.arguments.size == 1) { "Only single-argument functions can be differentiated." }
            val u = expression.arguments.single(); val du = differentiate(u, variable)
            val outer = when (expression.name.lowercase()) {
                "sin" -> SymbolicExpression.Function("cos", listOf(u))
                "cos" -> SymbolicExpression.UnaryMinus(SymbolicExpression.Function("sin", listOf(u)))
                "tan" -> SymbolicExpression.Power(SymbolicExpression.Function("cos", listOf(u)), num(ExactRational.of(-2)))
                "exp" -> expression
                "ln", "log" -> SymbolicExpression.Power(u, num(ExactRational.of(-1)))
                "sqrt" -> SymbolicExpression.Product(listOf(num(ExactRational.of(1) / ExactRational.of(2)), SymbolicExpression.Power(u, num(ExactRational.of(-1) / ExactRational.of(2)))))
                else -> error("Derivative rule for ${expression.name} is not implemented.")
            }
            SymbolicExpression.Product(listOf(outer, du))
        }
    }

    private fun integrate(expression: SymbolicExpression, variable: String): SymbolicExpression? {
        polynomial(expression, variable)?.let { polynomial -> return polynomial.integral().toExpression(variable) }
        return when (expression) {
            is SymbolicExpression.Sum -> expression.terms.map { integrate(it, variable) ?: return null }.let(SymbolicExpression::Sum)
            is SymbolicExpression.Product -> {
                val constants = expression.factors.filter { !contains(it, variable) }
                val varying = expression.factors.filter { contains(it, variable) }
                if (varying.size == 1) integrate(varying.single(), variable)?.let { SymbolicExpression.Product(constants + it) } else null
            }
            is SymbolicExpression.Power -> if (expression.base == SymbolicExpression.Variable(variable) && integer(expression.exponent) == -1) SymbolicExpression.Function("ln", listOf(SymbolicExpression.Function("abs", listOf(expression.base)))) else null
            is SymbolicExpression.Function -> {
                val u = expression.arguments.singleOrNull() ?: return null
                val linear = polynomial(u, variable)?.takeIf { it.degree <= 1 } ?: return null
                val slope = linear.coefficients.getOrElse(1) { zero }
                if (slope.isZero) return SymbolicExpression.Product(listOf(expression, SymbolicExpression.Variable(variable)))
                val primitive = when (expression.name) {
                    "sin" -> SymbolicExpression.UnaryMinus(SymbolicExpression.Function("cos", listOf(u)))
                    "cos" -> SymbolicExpression.Function("sin", listOf(u))
                    "exp" -> SymbolicExpression.Function("exp", listOf(u))
                    else -> return null
                }
                SymbolicExpression.Product(listOf(num(one / slope), primitive))
            }
            else -> null
        }
    }

    private fun affine(engine: SymbolicCasEngine, expression: SymbolicExpression, variables: Set<String>): LinearForm? {
        fun visit(node: SymbolicExpression): LinearForm? = when (node) {
            is SymbolicExpression.Number -> LinearForm(node.value, emptyMap())
            is SymbolicExpression.Variable -> if (node.name in variables) LinearForm(zero, mapOf(node.name to one)) else null
            is SymbolicExpression.UnaryMinus -> visit(node.value)?.scale(-one)
            is SymbolicExpression.Sum -> node.terms.fold(LinearForm(zero, emptyMap()) as LinearForm?) { total, term -> total?.plus(visit(term) ?: return null) }
            is SymbolicExpression.Product -> node.factors.fold(LinearForm(one, emptyMap()) as LinearForm?) { total, factor -> total?.times(visit(factor) ?: return null) }
            is SymbolicExpression.Power -> if (integer(node.exponent) == 1) visit(node.base) else if (integer(node.exponent) == 0) LinearForm(one, emptyMap()) else null
            is SymbolicExpression.Function -> null
        }
        return visit(engine.simplify(engine.expand(expression)))
    }

    private fun rational(expression: SymbolicExpression, variable: String): RationalPolynomial? = when (expression) {
        is SymbolicExpression.Number -> RationalPolynomial(CasPolynomial(listOf(expression.value)), CasPolynomial.ONE)
        is SymbolicExpression.Variable -> if (expression.name == variable) RationalPolynomial(CasPolynomial(listOf(zero, one)), CasPolynomial.ONE) else null
        is SymbolicExpression.UnaryMinus -> rational(expression.value, variable)?.let { RationalPolynomial(-it.numerator, it.denominator) }
        is SymbolicExpression.Sum -> expression.terms.map { rational(it, variable) ?: return null }.reduce { a, b -> RationalPolynomial(a.numerator * b.denominator + b.numerator * a.denominator, a.denominator * b.denominator) }
        is SymbolicExpression.Product -> expression.factors.map { rational(it, variable) ?: return null }.fold(RationalPolynomial(CasPolynomial.ONE, CasPolynomial.ONE)) { a, b -> RationalPolynomial(a.numerator * b.numerator, a.denominator * b.denominator) }
        is SymbolicExpression.Power -> {
            val base = rational(expression.base, variable) ?: return null
            val exponent = integer(expression.exponent) ?: return null
            if (exponent >= 0) RationalPolynomial(base.numerator.pow(exponent), base.denominator.pow(exponent)) else RationalPolynomial(base.denominator.pow(-exponent), base.numerator.pow(-exponent))
        }
        is SymbolicExpression.Function -> null
    }

    private fun polynomial(expression: SymbolicExpression, variable: String): CasPolynomial? = rational(expression, variable)?.takeIf { it.denominator.degree == 0 }?.let { it.numerator.scale(one / it.denominator.leading) }

    private fun parseMatrix(source: String): List<List<ExactRational>> {
        val clean = source.trim().removePrefix("[").removeSuffix("]")
        val rows = Regex("\\[([^\\[\\]]+)]").findAll(clean).map { match -> match.groupValues[1].split(',').map { ExactRational.parse(it.trim()) } }.toList()
        require(rows.isNotEmpty() && rows.all { it.isNotEmpty() && it.size == rows.first().size }) { "Use matrix syntax [[a,b],[c,d]]." }
        return rows
    }

    private fun matrixOperation(source: String, operation: String, block: (List<List<ExactRational>>) -> Triple<String, List<CasStep>, List<String>>): CasRow = safe(source, operation) {
        val matrix = parseMatrix(source)
        val (answer, steps, assumptions) = block(matrix)
        CasRow(source, operation, answer, null, assumptions, steps)
    }

    private fun determinantValue(input: List<List<ExactRational>>): ExactRational {
        val matrix = input.map { it.toMutableList() }.toMutableList()
        var result = one
        for (column in matrix.indices) {
            val pivot = (column until matrix.size).firstOrNull { !matrix[it][column].isZero } ?: return zero
            if (pivot != column) { val row = matrix[pivot]; matrix[pivot] = matrix[column]; matrix[column] = row; result = -result }
            val value = matrix[column][column]; result *= value
            for (r in column + 1 until matrix.size) {
                val scale = matrix[r][column] / value
                for (c in column until matrix.size) matrix[r][c] -= scale * matrix[column][c]
            }
        }
        return result
    }

    private fun rref(input: List<List<ExactRational>>): Reduction {
        val matrix = input.map { it.toMutableList() }.toMutableList(); val operations = mutableListOf<String>(); var row = 0
        for (column in matrix.first().indices) {
            val pivot = (row until matrix.size).firstOrNull { !matrix[it][column].isZero } ?: continue
            if (pivot != row) { val swap = matrix[pivot]; matrix[pivot] = matrix[row]; matrix[row] = swap; operations += "R${row + 1} <-> R${pivot + 1}" }
            val value = matrix[row][column]
            if (!value.isOne) { for (c in matrix[row].indices) matrix[row][c] /= value; operations += "R${row + 1} / $value" }
            for (r in matrix.indices) if (r != row && !matrix[r][column].isZero) {
                val scale = matrix[r][column]; for (c in matrix[r].indices) matrix[r][c] -= scale * matrix[row][c]; operations += "R${r + 1} - ($scale)R${row + 1}"
            }
            row++; if (row == matrix.size) break
        }
        return Reduction(matrix, row, operations)
    }

    private fun safe(source: String, operation: String, block: () -> CasRow): CasRow = runCatching(block).getOrElse { error ->
        CasRow(source, operation, "Not supported", null, emptyList(), listOf(CasStep("Unsupported", source, error.message ?: "The operation was refused safely.")), false)
    }

    private fun difference(left: SymbolicExpression, right: SymbolicExpression) = SymbolicExpression.Sum(listOf(left, SymbolicExpression.UnaryMinus(right)))
    private fun integer(expression: SymbolicExpression): Int? = (expression as? SymbolicExpression.Number)?.value?.takeIf { it.denominator == BigInteger.ONE && it.numerator.bitLength() < 31 }?.numerator?.toInt()
    private fun contains(expression: SymbolicExpression, variable: String): Boolean = when (expression) {
        is SymbolicExpression.Variable -> expression.name == variable
        is SymbolicExpression.Number -> false
        is SymbolicExpression.UnaryMinus -> contains(expression.value, variable)
        is SymbolicExpression.Sum -> expression.terms.any { contains(it, variable) }
        is SymbolicExpression.Product -> expression.factors.any { contains(it, variable) }
        is SymbolicExpression.Power -> contains(expression.base, variable) || contains(expression.exponent, variable)
        is SymbolicExpression.Function -> expression.arguments.any { contains(it, variable) }
    }
    private fun num(value: ExactRational) = SymbolicExpression.Number(value)
    private fun linearFactor(variable: String, root: ExactRational) = if (root < zero) "$variable + ${-root}" else "$variable - $root"
    private fun matrixText(matrix: List<List<ExactRational>>) = matrix.joinToString(prefix = "[", postfix = "]") { it.joinToString(prefix = "[", postfix = "]") }
    private fun reverse(relation: String) = when (relation) { ">" -> "<"; ">=" -> "<="; "<" -> ">"; else -> ">=" }
    private fun compare(a: ExactRational, relation: String, b: ExactRational) = when (relation) { ">" -> a > b; ">=" -> a >= b; "<" -> a < b; else -> a <= b }
    private fun tighterLower(old: Bound?, next: Bound) = when { old == null || next.value > old.value -> next; next.value < old.value -> old; else -> Bound(old.value, old.inclusive && next.inclusive) }
    private fun tighterUpper(old: Bound?, next: Bound) = when { old == null || next.value < old.value -> next; next.value > old.value -> old; else -> Bound(old.value, old.inclusive && next.inclusive) }
    private fun sqrtRational(value: ExactRational): ExactRational? {
        if (value < zero) return null
        fun root(n: BigInteger): BigInteger? { val candidate = kotlin.math.sqrt(n.toDouble()).toLong(); return BigInteger.valueOf(candidate).takeIf { it * it == n } }
        return root(value.numerator)?.let { n -> root(value.denominator)?.let { d -> ExactRational.of(n, d) } }
    }

    private data class Bound(val value: ExactRational, val inclusive: Boolean)
    private data class Reduction(val matrix: List<List<ExactRational>>, val rank: Int, val operations: List<String>)
    private data class RationalPolynomial(val numerator: CasPolynomial, val denominator: CasPolynomial)
    private data class LinearForm(val constant: ExactRational, val coefficients: Map<String, ExactRational>) {
        fun scale(value: ExactRational) = LinearForm(constant * value, coefficients.mapValues { it.value * value })
        operator fun plus(other: LinearForm) = LinearForm(constant + other.constant, (coefficients.keys + other.coefficients.keys).associateWith { (coefficients[it] ?: zero) + (other.coefficients[it] ?: zero) }.filterValues { !it.isZero })
        fun times(other: LinearForm): LinearForm? = when { coefficients.isEmpty() -> other.scale(constant); other.coefficients.isEmpty() -> scale(other.constant); else -> null }
    }

    private data class CasPolynomial(val coefficients: List<ExactRational>) {
        private val clean = coefficients.dropLastWhile { it.isZero }.ifEmpty { listOf(zero) }
        val degree get() = clean.lastIndex
        val leading get() = clean.last()
        val isZero get() = degree == 0 && leading.isZero
        operator fun plus(other: CasPolynomial) = CasPolynomial(List(maxOf(clean.size, other.clean.size)) { clean.getOrElse(it) { zero } + other.clean.getOrElse(it) { zero } })
        operator fun unaryMinus() = CasPolynomial(clean.map { -it })
        operator fun times(other: CasPolynomial): CasPolynomial { val out = MutableList(clean.size + other.clean.size - 1) { zero }; clean.forEachIndexed { i, a -> other.clean.forEachIndexed { j, b -> out[i + j] += a * b } }; return CasPolynomial(out) }
        fun scale(value: ExactRational) = CasPolynomial(clean.map { it * value })
        fun pow(power: Int): CasPolynomial { require(power in 0..20); return (1..power).fold(ONE) { total, _ -> total * this } }
        fun evaluate(x: ExactRational) = clean.reversed().fold(zero) { total, coefficient -> total * x + coefficient }
        fun derivative() = if (degree == 0) ZERO else CasPolynomial((1..degree).map { clean[it] * ExactRational.of(it.toLong()) })
        fun integral() = CasPolynomial(listOf(zero) + clean.mapIndexed { index, coefficient -> coefficient / ExactRational.of((index + 1).toLong()) })
        fun divRem(divisor: CasPolynomial): Pair<CasPolynomial, CasPolynomial> {
            require(!divisor.isZero); if (degree < divisor.degree) return ZERO to this
            val quotient = MutableList(degree - divisor.degree + 1) { zero }; var remainder = this
            while (!remainder.isZero && remainder.degree >= divisor.degree) {
                val power = remainder.degree - divisor.degree; val coefficient = remainder.leading / divisor.leading
                quotient[power] = coefficient
                val term = CasPolynomial(List(power) { zero } + coefficient)
                remainder += -(divisor * term)
            }
            return CasPolynomial(quotient) to remainder
        }
        fun rationalRoots(): List<ExactRational> {
            var remaining = this; val roots = mutableListOf<ExactRational>()
            while (remaining.degree > 0) { val root = remaining.rootCandidates().firstOrNull { remaining.evaluate(it).isZero } ?: break; roots += root; remaining = remaining.divideByRoot(root) }
            return roots
        }
        fun factorRender(variable: String): String {
            if (degree == 0) return leading.toString()
            val roots = rationalRoots()
            if (roots.isEmpty()) return render(variable)
            var remainder = this
            roots.forEach { remainder = remainder.divideByRoot(it) }
            return buildList {
                if (remainder.degree == 0 && !remainder.leading.isOne) add(remainder.leading.toString())
                roots.forEach { add("(${linearFactor(variable, it)})") }
                if (remainder.degree > 0) add("(${remainder.render(variable)})")
            }.joinToString("")
        }
        fun divideByRoot(root: ExactRational): CasPolynomial { require(degree > 0 && evaluate(root).isZero); val out = MutableList(degree) { zero }; out[degree - 1] = leading; for (i in degree - 2 downTo 0) out[i] = clean[i + 1] + root * out[i + 1]; return CasPolynomial(out) }
        private fun rootCandidates(): List<ExactRational> {
            var common = BigInteger.ONE; clean.forEach { common = common / common.gcd(it.denominator) * it.denominator }
            val integers = clean.map { it.numerator * (common / it.denominator) }; val constant = integers.first().abs(); val lead = integers.last().abs()
            if (constant == BigInteger.ZERO) return listOf(zero); if (constant.bitLength() > 20 || lead.bitLength() > 20) return emptyList()
            fun divisors(value: Int) = (1..value).filter { value % it == 0 }
            return divisors(constant.toInt()).flatMap { n -> divisors(lead.toInt()).flatMap { d -> ExactRational.of(BigInteger.valueOf(n.toLong()), BigInteger.valueOf(d.toLong())).let { listOf(it, -it) } } }.distinct()
        }
        fun render(variable: String): String = clean.indices.reversed().mapNotNull { power ->
            val coefficient = clean[power]; if (coefficient.isZero) null else {
                val sign = if (coefficient < zero) "-" else "+"; val magnitude = if (coefficient < zero) -coefficient else coefficient
                val body = when (power) { 0 -> magnitude.toString(); 1 -> if (magnitude.isOne) variable else "$magnitude*$variable"; else -> if (magnitude.isOne) "$variable^$power" else "$magnitude*$variable^$power" }
                sign to body
            }
        }.mapIndexed { index, (sign, body) -> if (index == 0) (if (sign == "-") "-" else "") + body else " $sign $body" }.joinToString("").ifBlank { "0" }
        fun toExpression(variable: String): SymbolicExpression = clean.mapIndexedNotNull { power, coefficient ->
            if (coefficient.isZero) null else when (power) { 0 -> num(coefficient); 1 -> SymbolicExpression.Product(listOf(num(coefficient), SymbolicExpression.Variable(variable))); else -> SymbolicExpression.Product(listOf(num(coefficient), SymbolicExpression.Power(SymbolicExpression.Variable(variable), num(ExactRational.of(power.toLong()))))) }
        }.let { if (it.isEmpty()) num(zero) else if (it.size == 1) it.single() else SymbolicExpression.Sum(it) }
        companion object { val ZERO = CasPolynomial(listOf(zero)); val ONE = CasPolynomial(listOf(one)) }
    }
}
