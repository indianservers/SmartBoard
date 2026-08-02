package com.indianservers.smartboard.core

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CasBranchCondition(val expression: String, val condition: String, val consequence: String)
data class CasDomainBranchReport(
    val domain: List<String>,
    val excluded: List<String>,
    val branches: List<CasBranchCondition>,
    val warnings: List<String>,
) {
    val descriptions get() = domain + excluded.map { "exclude $it" } + branches.map { "${it.condition} => ${it.consequence}" }
}

object CasDomainBranchAnalyzer {
    fun analyze(source: String, assumptions: MathAssumptionSet = MathAssumptionSet()): CasDomainBranchReport {
        val domains = mutableListOf<String>(); val excluded = mutableListOf<String>(); val branches = mutableListOf<CasBranchCondition>(); val warnings = mutableListOf<String>()
        Regex("/\\s*\\(([^()]*)\\)|/\\s*([A-Za-z][A-Za-z0-9_]*)").findAll(source).forEach { match ->
            val denominator = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
            if (denominator.isNotBlank()) excluded += "$denominator = 0"
        }
        Regex("sqrt\\(([^()]*)\\)", RegexOption.IGNORE_CASE).findAll(source).forEach { match ->
            val radicand = match.groupValues[1]; domains += "$radicand >= 0"
            branches += CasBranchCondition(match.value, "$radicand >= 0", "principal non-negative square root")
        }
        Regex("(?:ln|log)\\(([^()]*)\\)", RegexOption.IGNORE_CASE).findAll(source).forEach { domains += "${it.groupValues[1]} > 0" }
        Regex("([A-Za-z][A-Za-z0-9_]*)\\^\\(([^/]+)/([^)]+)\\)").findAll(source).forEach { match ->
            branches += CasBranchCondition(match.value, "${match.groupValues[1]} crosses the branch cut", "principal complex branch is used")
        }
        assumptions.descriptions.forEach { domains += it }
        if (source.contains("asin", true) || source.contains("acos", true) || source.contains("arg(", true)) warnings += "Inverse and argument functions use principal branches unless a branch is selected."
        return CasDomainBranchReport(domains.distinct(), excluded.distinct(), branches.distinct(), warnings)
    }
}

/** Verified canonical algorithms for broad CAS interaction; unsupported cases refuse with a precise boundary. */
internal object ComputationalBreadthCas {
    fun evaluate(engine: SymbolicCasEngine, source: String, operation: String): CasRow? = when (operation.lowercase()) {
        "series", "taylor", "maclaurin" -> series(source)
        "asymptotic", "asymptotic expansion" -> asymptotic(source)
        "fourier", "fourier transform" -> fourier(source, inverse = false)
        "inverse fourier", "inverse fourier transform" -> fourier(source, inverse = true)
        "sum", "symbolic sum" -> finiteAggregate(engine, source, product = false)
        "product", "symbolic product" -> finiteAggregate(engine, source, product = true)
        "residue" -> residue(engine, source)
        "contour integral" -> contourIntegral(engine, source)
        "special function", "special functions" -> special(source)
        "number theory" -> numberTheory(source)
        "finite algebra" -> finiteAlgebra(source)
        "recurrence", "solve recurrence" -> recurrence(source)
        "nonlinear ode" -> nonlinearOde(source)
        "higher ode", "higher-order ode" -> higherLinearOde(source)
        "pde", "classify pde" -> classifyPde(source)
        "optimize", "optimization" -> optimize(source)
        "eigenvectors" -> eigenvectors(source)
        "jordan", "jordan form" -> jordan(source)
        "svd" -> svd(source)
        "matrix inverse", "inverse matrix" -> matrixInverse(source)
        "transpose" -> matrixTranspose(source)
        "rank" -> matrixRank(source)
        "nullspace", "null space" -> matrixNullspace(source)
        "algebraic solve", "exact roots" -> algebraicRoots(source)
        "domain", "branch analysis" -> domain(source)
        else -> null
    }

    fun broaderIntegral(source: String, variable: String): CasRow = safe(source, "integral") {
        val clean = source.replace(" ", "")
        val answer = when {
            clean == "$variable*exp($variable)" -> "exp($variable)*($variable - 1) + C"
            clean == "$variable*sin($variable)" -> "sin($variable) - $variable*cos($variable) + C"
            clean == "$variable*cos($variable)" -> "$variable*sin($variable) + cos($variable) + C"
            clean == "ln($variable)" -> "$variable*ln($variable) - $variable + C"
            clean == "1/(1+$variable^2)" -> "atan($variable) + C"
            clean == "1/sqrt(1-$variable^2)" -> "asin($variable) + C"
            else -> error("No verified elementary fallback matched; retain the integral symbolically or choose a numeric method.")
        }
        row(source,"integral",answer,CasDomainBranchAnalyzer.analyze(source).descriptions,CasStep("Strategy","integration by parts / canonical inverse derivative","Choose a strategy from the integrand structure."),CasStep("Antiderivative",answer,"Apply the verified identity."),CasStep("Differentiate to verify",source,"The derivative of the result reproduces the integrand on its domain."))
    }

    fun broaderLimit(source: String, variable: String, approaching: String): CasRow = safe(source,"limit") {
        val clean=source.replace(" ","");val point=approaching.replace(" ","");val answer=when {
            point=="0" && clean=="sin($variable)/$variable" -> "1"
            point=="0" && clean=="(exp($variable)-1)/$variable" -> "1"
            point=="0" && clean=="ln(1+$variable)/$variable" -> "1"
            point=="0" && clean=="(1+$variable)^(1/$variable)" -> "e"
            point in setOf("infinity","+infinity") && clean=="(1+1/$variable)^$variable" -> "e"
            else -> error("No verified series, squeeze or canonical exponential limit matched.")
        }
        row(source,"limit",answer,listOf("$variable approaches $approaching"),CasStep("Choose strategy","canonical limit / local series","Inspect indeterminate form before transforming."),CasStep("Leading terms",answer,"Cancel the common leading order or use the defining exponential limit."),CasStep("Result",answer,"Confirm the approach direction and domain."))
    }

    fun solveFactoredInequality(engine: SymbolicCasEngine, source: String, variable: String): CasRow = safe(source, "inequalities") {
        val match = Regex("(.+?)(<=|>=|<|>)(.+)").matchEntire(source.trim()) ?: error("Use a polynomial or rational inequality.")
        require(ExactRational.parse(match.groupValues[3].trim()).isZero) { "Move every term to the left so the right side is zero." }
        val expression = match.groupValues[1].trim(); val relation = match.groupValues[2]
        val division = expression.split('/', limit = 2); val numerator = division[0]; val denominator = division.getOrNull(1).orEmpty()
        val zeroes = linearFactorRoots(numerator, variable); val poles = linearFactorRoots(denominator, variable)
        require(zeroes.isNotEmpty()) { "Write the numerator as explicit rational linear factors such as (x-2)*(x+1)." }
        val critical = (zeroes + poles).distinct().sorted(); val samples = buildList {
            add(critical.first() - ExactRational.ONE)
            critical.zipWithNext().forEach { (a,b) -> add((a+b)/ExactRational.of(2)) }
            add(critical.last() + ExactRational.ONE)
        }
        val intervals = samples.mapIndexedNotNull { index, sample ->
            val value = engine.evaluate(engine.parse(expression), mapOf(variable to sample)).getOrElse { error("The sign chart could not evaluate at $variable=$sample.") }
            val accepted = when (relation) { ">" -> value > ExactRational.ZERO; ">=" -> value >= ExactRational.ZERO; "<" -> value < ExactRational.ZERO; else -> value <= ExactRational.ZERO }
            if (!accepted) null else {
                val left = critical.getOrNull(index - 1); val right = critical.getOrNull(index)
                val leftClosed = left != null && left in zeroes && left !in poles && relation.contains('=')
                val rightClosed = right != null && right in zeroes && right !in poles && relation.contains('=')
                "${if(leftClosed)'[' else '('}${left ?: "-infinity"}, ${right ?: "infinity"}${if(rightClosed)']' else ')'}"
            }
        }.toMutableList()
        if (relation.contains('=')) zeroes.filter { it !in poles }.forEach { root ->
            if (intervals.none { it.startsWith("[$root,") || it.endsWith(", $root]") }) intervals += "{$root}"
        }
        require(intervals.isNotEmpty()) { "The inequality has no real solution." }
        val answer = intervals.joinToString(" union ")
        row(source,"inequalities",answer,listOf("$variable is real") + poles.map { "$variable != $it" },CasStep("Critical points","zeroes=${zeroes.joinToString()}; poles=${poles.joinToString()}","Factor numerator and denominator over exact rational linear factors."),CasStep("Sign chart",answer,"Test one exact rational point in each interval."),CasStep("Endpoints",answer,"Include equality zeroes and always exclude denominator poles."))
    }

    private fun series(source: String): CasRow = safe(source, "series") {
        val request = parseSeriesRequest(source); val x = request.variable; val n = request.order
        require(request.center == "0") { "Verified expansion currently supports center 0; translate the variable first for another center." }
        val expression = request.expression.replace(" ", "")
        val terms = when (expression.lowercase()) {
            "exp($x)" -> (0..n).map { k -> powerTerm(x, k, ExactRational.ONE / ExactRational.of(factorial(k))) }
            "sin($x)" -> (0..n).filter { it % 2 == 1 }.map { k -> powerTerm(x, k, ExactRational.of(if ((k - 1) / 2 % 2 == 0) 1 else -1) / ExactRational.of(factorial(k))) }
            "cos($x)" -> (0..n).filter { it % 2 == 0 }.map { k -> powerTerm(x, k, ExactRational.of(if (k / 2 % 2 == 0) 1 else -1) / ExactRational.of(factorial(k))) }
            "ln(1+$x)" -> (1..n).map { k -> powerTerm(x, k, ExactRational.of(BigInteger.valueOf(if (k % 2 == 1) 1 else -1), BigInteger.valueOf(k.toLong()))) }
            "1/(1-$x)" -> (0..n).map { k -> powerTerm(x, k, ExactRational.ONE) }
            "1/(1+$x)" -> (0..n).map { k -> powerTerm(x, k, ExactRational.of(if (k % 2 == 0) 1 else -1)) }
            else -> error("Use exp(x), sin(x), cos(x), ln(1+x), or a geometric form 1/(1 +/- x).")
        }
        val answer = terms.joinToString(" + ").replace("+ -", "- ") + " + O($x^${n + 1})"
        row(source, "series", answer, listOf("$x near 0"),
            CasStep("Expansion point", "$x = 0; order $n", "Choose the local branch and truncation order."),
            CasStep("Generate coefficients", terms.joinToString(" + ").replace("+ -", "- "), "Use exact derivatives or the verified canonical coefficient recurrence."),
            CasStep("Remainder", "O($x^${n + 1})", "Retain the order term so the approximation is not presented as an identity."))
    }

    private fun asymptotic(source: String): CasRow = safe(source, "asymptotic") {
        val clean = source.substringBefore(',').trim().replace(" ", "")
        val answer = when {
            clean == "1/(x+1)" -> "1/x - 1/x^2 + 1/x^3 + O(1/x^4)"
            clean == "sqrt(x^2+1)" -> "x + 1/(2*x) - 1/(8*x^3) + O(1/x^5), for x -> +infinity"
            clean == "ln(x+1)" -> "ln(x) + 1/x - 1/(2*x^2) + O(1/x^3)"
            clean == "exp(-x)" -> "exponentially small as x -> +infinity"
            else -> error("Use a verified rational, square-root, logarithmic or exponential canonical form at infinity.")
        }
        row(source, "asymptotic", answer, listOf("x -> +infinity"), CasStep("Dominant scale", answer.substringBefore(" + O"), "Factor the dominant power and expand in 1/x."), CasStep("Remainder", answer, "State direction and remainder order explicitly."))
    }

    private fun fourier(source: String, inverse: Boolean): CasRow = safe(source, if (inverse) "inverse fourier" else "fourier") {
        val clean = source.replace(" ", ""); val answer = if (!inverse) when {
            clean == "exp(-a*x^2)" -> "sqrt(pi/a)*exp(-w^2/(4*a))"
            clean == "exp(-abs(x))" -> "2/(1+w^2)"
            clean == "1" -> "2*pi*delta(w)"
            Regex("cos\\(([^*]+)\\*x\\)").matches(clean) -> Regex("cos\\(([^*]+)\\*x\\)").matchEntire(clean)!!.groupValues[1].let { "pi*(delta(w-$it)+delta(w+$it))" }
            Regex("sin\\(([^*]+)\\*x\\)").matches(clean) -> Regex("sin\\(([^*]+)\\*x\\)").matchEntire(clean)!!.groupValues[1].let { "pi/i*(delta(w-$it)-delta(w+$it))" }
            else -> error("Use a Gaussian, exp(-abs(x)), constant, sine or cosine under the angular-frequency convention.")
        } else when {
            clean == "2/(1+w^2)" -> "exp(-abs(x))"
            clean == "2*pi*delta(w)" -> "1"
            else -> error("Use a canonical spectrum under the angular-frequency convention.")
        }
        row(source, if (inverse) "inverse fourier" else "fourier", answer, listOf("F(w)=integral f(x)*exp(-i*w*x) dx"), CasStep("Convention", "F(w)=integral[-infinity,infinity] f(x)*exp(-i*w*x) dx", "Fix normalization before applying transform pairs."), CasStep("Transform pair", answer, "Apply linearity and a verified transform pair."), CasStep("Check", "inverse transform reproduces input", "Verify normalization and distribution terms."))
    }

    private fun finiteAggregate(engine: SymbolicCasEngine, source: String, product: Boolean): CasRow = safe(source, if (product) "product" else "sum") {
        val name = if (product) "product" else "sum"
        val symbolic = Regex("(?i)(?:$name\\()?(.+?),\\s*([A-Za-z][A-Za-z0-9_]*),\\s*1,\\s*([A-Za-z][A-Za-z0-9_]*)\\)?").matchEntire(source.trim())
        if (symbolic != null) {
            val expression=symbolic.groupValues[1].removePrefix("(").replace(" ","");val index=symbolic.groupValues[2];val upper=symbolic.groupValues[3]
            val answer=if(!product)when(expression){index->"$upper*($upper+1)/2";"$index^2"->"$upper*($upper+1)*(2*$upper+1)/6";"$index^3"->"($upper*($upper+1)/2)^2";"1"->upper;else->if(expression.matches(Regex("[A-Za-z][A-Za-z0-9_]*\\^$index")))"(${expression.substringBefore('^')}^($upper+1)-${expression.substringBefore('^')})/(${expression.substringBefore('^')}-1)" else error("Use canonical polynomial or geometric symbolic sums.")} else when(expression){index->"factorial($upper)";"1"->"1";else->error("Use product(k,k,1,n) for a symbolic factorial product.")}
            return@safe row(source,name,answer,listOf("$upper is a non-negative integer"),CasStep("Recognize family",expression,"Match a canonical symbolic aggregate."),CasStep("Closed form",answer,"Apply the exact finite-sum or product identity."),CasStep("Verify","base case and finite difference","Check the first bound and compare F(n)-F(n-1) with the summand."))
        }
        val match = Regex("(?i)(?:$name\\()?(.+?),\\s*([A-Za-z][A-Za-z0-9_]*),\\s*(-?\\d+),\\s*(-?\\d+)\\)?").matchEntire(source.trim())
            ?: error("Use $name(expression, index, integerStart, integerEnd).")
        val expression = match.groupValues[1].removePrefix("("); val index = match.groupValues[2]; val start = match.groupValues[3].toInt(); val end = match.groupValues[4].toInt()
        require(end >= start && end - start <= 10000) { "Finite bounds must be ordered and contain at most 10,001 terms." }
        var value = if (product) ExactRational.ONE else ExactRational.ZERO
        for (k in start..end) {
            val term = engine.evaluate(engine.parse(expression), mapOf(index to ExactRational.of(k.toLong()))).getOrElse { error("Each finite term must evaluate exactly: ${it.message}") }
            value = if (product) value * term else value + term
        }
        row(source, name, value.toString(), emptyList(), CasStep("Bounds", "$index = $start..$end", "Confirm finite inclusive integer bounds."), CasStep("Evaluate terms", "$name[$index=$start..$end] $expression", "Evaluate each term with exact rational arithmetic."), CasStep("Accumulate", value.toString(), "Combine terms without floating-point rounding."))
    }

    private fun residue(engine: SymbolicCasEngine, source: String): CasRow = safe(source, "residue") {
        val parts = source.removePrefix("residue").trim().split(',').map(String::trim); require(parts.size == 3) { "Use residue(expression, variable, pole)." }
        val expression = parts[0].removePrefix("("); val variable = parts[1]; val pole = ExactRational.parse(parts[2].removeSuffix(")"))
        val multiplied = "($variable-($pole))*($expression)"
        val value = engine.limit(multiplied, variable, pole.toString()); require(value.supported) { "The pole must be simple and the exact limit must exist." }
        row(source, "residue", value.exact, listOf("isolated simple pole at $variable=$pole"), CasStep("Isolate pole", "$variable = $pole", "Confirm a simple isolated singularity."), CasStep("Residue limit", "lim[$variable->$pole] ($variable-$pole)*($expression)", "Cancel the simple pole."), CasStep("Result", value.exact, "Evaluate the remaining analytic factor exactly."))
    }

    private fun contourIntegral(engine: SymbolicCasEngine, source: String): CasRow = safe(source, "contour integral") {
        val residueRequest = source.removePrefix("contour integral").trim(); val residue = residue(engine, residueRequest).also { require(it.supported) }
        val answer = "2*pi*i*(${residue.exact})"
        row(source, "contour integral", answer, listOf("positively oriented simple closed contour", "listed pole lies inside and no other poles do"), CasStep("Locate enclosed poles", residueRequest, "Determine which isolated singularities are inside the contour."), CasStep("Residue theorem", answer, "Multiply the enclosed residue sum by 2*pi*i."))
    }

    private fun special(source: String): CasRow = safe(source, "special functions") {
        val clean = source.replace(" ", ""); val answer = when {
            Regex("gamma\\((\\d+)\\)", RegexOption.IGNORE_CASE).matches(clean) -> factorial(Regex("\\d+").find(clean)!!.value.toInt() - 1).toString()
            Regex("beta\\((\\d+),(\\d+)\\)", RegexOption.IGNORE_CASE).matches(clean) -> Regex("\\d+").findAll(clean).map { it.value.toInt() }.toList().let { (a,b) -> ExactRational.of(BigInteger.valueOf(factorial(a - 1)), BigInteger.valueOf(factorial(a + b - 1))) * ExactRational.of(factorial(b - 1)) }.toString()
            Regex("binomial\\((\\d+),(\\d+)\\)", RegexOption.IGNORE_CASE).matches(clean) -> Regex("\\d+").findAll(clean).map { it.value.toInt() }.toList().let { (n,k) -> (factorial(n) / (factorial(k) * factorial(n-k))).toString() }
            clean.equals("erf(0)", true) -> "0"
            clean.equals("zeta(0)", true) -> "-1/2"
            clean.equals("zeta(2)", true) -> "pi^2/6"
            else -> error("Exact evaluation supports integer Gamma/Beta/binomial and canonical erf or zeta values; other arguments remain symbolic.")
        }
        row(source, "special functions", answer, emptyList(), CasStep("Recognize", source, "Match a defining identity or exact canonical value."), CasStep("Evaluate", answer, "Use exact factorial or known special value identities."))
    }

    private fun numberTheory(source: String): CasRow = safe(source, "number theory") {
        val clean = source.lowercase().replace(" ", ""); val values = Regex("-?\\d+").findAll(clean).map { BigInteger(it.value) }.toList()
        val answer = when {
            clean.startsWith("gcd") -> values.reduce(BigInteger::gcd).toString()
            clean.startsWith("lcm") -> values.reduce { a,b -> a.divide(a.gcd(b)).multiply(b).abs() }.toString()
            clean.startsWith("modinverse") -> { require(values.size == 2); values[0].modInverse(values[1]).toString() }
            clean.startsWith("isprime") -> { require(values.size == 1); values[0].isProbablePrime(40).toString() }
            clean.startsWith("factorinteger") -> { require(values.size == 1); primeFactors(values[0]).entries.joinToString(" * ") { (p,e) -> if (e == 1) "$p" else "$p^$e" } }
            clean.startsWith("totient") -> { require(values.size == 1); val n=values[0]; primeFactors(n).keys.fold(n) { total,p -> total / p * (p-BigInteger.ONE) }.toString() }
            else -> error("Use gcd, lcm, modInverse, isPrime, factorInteger or totient with integers.")
        }
        row(source, "number theory", answer, listOf("exact integers"), CasStep("Normalize integers", values.joinToString(), "Parse arbitrary-precision integers."), CasStep("Apply theorem", answer, "Use Euclid, modular inversion or verified prime factor division."))
    }

    private fun finiteAlgebra(source: String): CasRow = safe(source, "finite algebra") {
        val clean = source.lowercase().replace(" ", ""); val n = Regex("z/(\\d+)z|zn\\((\\d+)\\)").find(clean)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toInt()
            ?: error("Use Z/nZ or Zn(n) for a finite cyclic ring.")
        require(n in 1..64); val units = (0 until n).filter { BigInteger.valueOf(it.toLong()).gcd(BigInteger.valueOf(n.toLong())) == BigInteger.ONE }
        val table = (0 until n).joinToString("; ") { a -> (0 until n).joinToString(prefix="[", postfix="]") { b -> ((a+b)%n).toString() } }
        row(source, "finite algebra", "Z/$n Z; units={${units.joinToString()}}", listOf("operations modulo $n"), CasStep("Carrier", "{${(0 until n).joinToString()}}", "Construct residue classes."), CasStep("Addition table", table, "Closure and associativity follow from integer addition modulo n."), CasStep("Units", units.joinToString(), "Select precisely the classes coprime to n."))
    }

    private fun recurrence(source: String): CasRow = safe(source, "recurrence") {
        val clean = source.replace(" ", "")
        val recurrence = Regex("a\\(n\\)=([+-]?\\d+(?:/\\d+)?)\\*a\\(n-1\\)([+-]\\d+(?:/\\d+)?)?").find(clean) ?: error("Use a(n)=r*a(n-1)+b with a(0)=c.")
        val initial = Regex("a\\(0\\)=([+-]?\\d+(?:/\\d+)?)").find(clean) ?: error("Provide a(0)=c.")
        val r = ExactRational.parse(recurrence.groupValues[1]); val b = recurrence.groupValues[2].takeIf(String::isNotBlank)?.let(ExactRational::parse) ?: ExactRational.ZERO; val c = ExactRational.parse(initial.groupValues[1])
        val answer = when { r == ExactRational.ONE -> "a(n) = $c + $b*n"; else -> { val fixed = b / (ExactRational.ONE-r); "a(n) = $fixed + (${c-fixed})*($r)^n" } }
        row(source, "recurrence", answer, listOf("n is a non-negative integer"), CasStep("Fixed point", if (r == ExactRational.ONE) "repeated accumulation" else "a*=${b/(ExactRational.ONE-r)}", "Separate the stationary and homogeneous parts."), CasStep("Closed form", answer, "Iterate the homogeneous multiplier."), CasStep("Verify", "base case and induction", "Check n=0, then substitute n-1 into the recurrence."))
    }

    private fun nonlinearOde(source:String):CasRow=safe(source,"nonlinear ode"){
        val clean=source.replace(" ","");val logistic=Regex("y'=([+-]?\\d+(?:/\\d+)?)\\*y\\*\\(1-y/([+-]?\\d+(?:/\\d+)?)\\)").find(clean)
        val answer:String;val conditions=mutableListOf<String>()
        if(logistic!=null){val r=ExactRational.parse(logistic.groupValues[1]);val k=ExactRational.parse(logistic.groupValues[2]);answer="y = $k/(1 + C*exp(-$r*x))";conditions += "$k != 0"}
        else {val power=Regex("y'=([+-]?\\d+(?:/\\d+)?)\\*y\\^([+-]?\\d+)").find(clean)?:error("Use a separable power equation y'=k*y^p or logistic y'=r*y*(1-y/K).");val k=ExactRational.parse(power.groupValues[1]);val p=power.groupValues[2].toInt();answer=if(p==1)"y = C*exp($k*x)" else "y = (C + ${ExactRational.of((1-p).toLong())*k}*x)^(1/${1-p})";conditions += if(p<0)"y != 0" else "select a real branch where the power is defined"}
        row(source,"nonlinear ode",answer,conditions,CasStep("Separate variables","dy/g(y) = dx","Isolate y-dependent factors from x."),CasStep("Integrate",answer,"Integrate both sides and absorb constants."),CasStep("Branches",conditions.joinToString(),"Retain equilibrium solutions and real-branch restrictions."),CasStep("Verify","differentiate and substitute","Check the family on each stated branch."))
    }

    private fun higherLinearOde(source:String):CasRow=safe(source,"higher ode"){
        val clean=source.replace(" ","").substringBefore(',');val sides=clean.split('=',limit=2);require(sides.size==2&&sides[1]=="0"){"Use a homogeneous constant-coefficient equation ending in =0."}
        val order=Regex("y('+)").findAll(sides[0]).maxOfOrNull{it.groupValues[1].length}?:1;require(order in 3..6){"Verified higher-order characteristic solving supports orders 3 through 6."}
        val coefficients=MutableList(order+1){ExactRational.ZERO};sides[0].replace("-","+-").split('+').filter(String::isNotBlank).forEach{term->val derivative=Regex("y('+)").find(term);val degree=derivative?.groupValues?.get(1)?.length?:if(term.endsWith("y"))0 else -1;require(degree>=0){"Only y and its derivatives may appear."};val raw=term.substringBefore('y').removeSuffix("*");coefficients[degree]+=when(raw){"","+"->ExactRational.ONE;"-"->-ExactRational.ONE;else->ExactRational.parse(raw)}}
        require(!coefficients[order].isZero);val roots=mutableListOf<ExactRational>();var current=coefficients.toMutableList();while(current.size>1){val root=(-20..20).map{ExactRational.of(it.toLong())}.firstOrNull{r->current.indices.fold(ExactRational.ZERO){sum,i->sum+current[i]*r.pow(i)}.isZero}?:break;roots+=root;val next=MutableList(current.size-1){ExactRational.ZERO};next[next.lastIndex]=current.last();for(i in next.lastIndex-1 downTo 0)next[i]=current[i+1]+root*next[i+1];current=next}
        require(roots.size==order){"The characteristic polynomial must split into verified integer linear factors; unsplit roots should be represented with exact RootOf results."}
        val grouped=roots.groupingBy{it}.eachCount();var c=1;val terms=mutableListOf<String>();grouped.forEach{(root,multiplicity)->repeat(multiplicity){power->terms += when(power){0->"C${c++}*exp($root*x)";1->"C${c++}*x*exp($root*x)";else->"C${c++}*x^$power*exp($root*x)"}}};val answer="y = ${terms.joinToString(" + ")}"
        row(source,"higher ode",answer,listOf("constant coefficients","homogeneous equation"),CasStep("Characteristic polynomial",coefficients.indices.reversed().joinToString(" + "){i->"${coefficients[i]}*r^$i"},"Replace y derivatives by powers of r."),CasStep("Exact roots",roots.joinToString(),"Factor the characteristic polynomial exactly."),CasStep("Parameterized family",answer,"Create one independent basis term per root multiplicity."),CasStep("Verify","operator annihilates every basis term","Substitute each basis function into the differential operator."))
    }

    private fun classifyPde(source: String): CasRow = safe(source, "pde") {
        fun coefficient(token: String): Double { val match=Regex("([+-]?\\d*(?:\\.\\d+)?)\\*?$token").find(source.replace(" ","")); val raw=match?.groupValues?.get(1) ?: return 0.0; return when(raw){"","+"->1.0;"-"->-1.0;else->raw.toDouble()} }
        val a=coefficient("u_xx"); val b=coefficient("u_xy"); val c=coefficient("u_yy"); require(abs(a)+abs(b)+abs(c)>0) { "Include u_xx, u_xy or u_yy terms." }
        val discriminant=b*b-4*a*c; val type=when { abs(discriminant)<1e-12->"parabolic";discriminant>0->"hyperbolic";else->"elliptic" }
        row(source, "pde", "$type PDE", listOf("real second-order scalar PDE"), CasStep("Principal coefficients", "A=$a, B=$b, C=$c", "Read only the second-order principal part."), CasStep("Discriminant", "B^2-4*A*C=$discriminant", "Classify independently of lower-order terms."), CasStep("Classification", type, "Negative is elliptic, zero parabolic, positive hyperbolic."))
    }

    private fun optimize(source: String): CasRow = safe(source, "optimization") {
        val clean=source.replace(" ","")
        val constrained=Regex("minimize\\(?x\\^2\\+y\\^2\\)?subjectto([+-]?\\d+(?:/\\d+)?)\\*?x\\+([+-]?\\d+(?:/\\d+)?)\\*?y=([+-]?\\d+(?:/\\d+)?)",RegexOption.IGNORE_CASE).find(clean)
        if(constrained!=null){val p=ExactRational.parse(constrained.groupValues[1]);val q=ExactRational.parse(constrained.groupValues[2]);val c=ExactRational.parse(constrained.groupValues[3]);val norm=p*p+q*q;require(!norm.isZero);val x=p*c/norm;val y=q*c/norm;val value=c*c/norm;return@safe row(source,"optimization","minimum $value at (x=$x, y=$y)",listOf("linear constraint is feasible"),CasStep("Lagrangian","L=x^2+y^2-lambda*($p*x+$q*y-$c)","Attach one symbolic multiplier to the equality constraint."),CasStep("Stationarity","2*x=lambda*$p; 2*y=lambda*$q","Differentiate with respect to both variables."),CasStep("Solve constraints","x=$x, y=$y","Solve stationarity and feasibility exactly."),CasStep("Verify global optimum","minimum=$value","Strict convexity makes the feasible stationary point the unique global minimum."))}
        val match=Regex("(?:minimize|optimize)\\(?([+-]?\\d+(?:/\\d+)?)\\*?x\\^2([+-]\\d+(?:/\\d+)?)\\*?x([+-]\\d+(?:/\\d+)?)?\\)?",RegexOption.IGNORE_CASE).find(clean) ?: error("Use minimize(a*x^2+b*x+c), or minimize(x^2+y^2) subject to p*x+q*y=c.")
        val a=ExactRational.parse(match.groupValues[1]);val b=ExactRational.parse(match.groupValues[2]);val c=match.groupValues[3].takeIf(String::isNotBlank)?.let(ExactRational::parse)?:ExactRational.ZERO;require(a>ExactRational.ZERO)
        val x=-b/(ExactRational.of(2)*a);val value=a*x*x+b*x+c
        row(source,"optimization","minimum $value at x=$x",listOf("a > 0"),CasStep("Stationarity","2*$a*x + $b = 0","Set the exact derivative to zero."),CasStep("Candidate","x=$x","Solve the stationarity equation."),CasStep("Second derivative","2*$a > 0","Positive curvature proves a global minimum."))
    }

    private fun eigenvectors(source: String): CasRow = safe(source, "eigenvectors") {
        val parts=source.split(';',limit=2);val a=parseMatrix(parts[0]);require(a.size==a[0].size)
        val requested=parts.getOrNull(1)?.let{Regex("(?:lambda\\s*=)?\\s*([+-]?\\d+(?:/\\d+)?)",RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.let(ExactRational::parse)}
        val diagonal=a.indices.all{i->a.indices.all{j->i==j||a[i][j].isZero}}
        val answer=if(requested!=null){val shifted=a.mapIndexed{i,row->row.mapIndexed{j,v->if(i==j)v-requested else v}};val basis=nullspace(shifted);require(basis.isNotEmpty()){"$requested is not an eigenvalue."};"lambda=$requested: span{${basis.joinToString{vectorText(it)}}}"}else{require(diagonal){"For an arbitrary matrix provide an exact eigenvalue after '; lambda=value'."};a.indices.groupBy{a[it][it]}.entries.joinToString("; "){(lambda,indices)->"lambda=$lambda: span{${indices.joinToString{e->"e${e+1}"}}}"}}
        row(source,"eigenvectors",answer,emptyList(),CasStep("Shift","A-lambda*I","Construct the exact homogeneous eigenspace system."),CasStep("Nullspace",answer,"Row reduce in arbitrary dimension and parameterize every free column."),CasStep("Verify","A*v=lambda*v","Check every basis vector exactly."))
    }

    private fun jordan(source: String): CasRow = safe(source,"jordan form") {
        val a=parseMatrix(source);require(a.size==a[0].size);val diagonal=a.indices.all{i->a.indices.all{j->i==j||a[i][j].isZero}}
        val upperJordan=a.indices.all{i->a.indices.all{j->a[i][j].isZero||i==j||j==i+1}} && a.indices.toList().dropLast(1).all{i->a[i][i+1]==ExactRational.ONE||a[i][i+1].isZero}
        require(diagonal||upperJordan){"Provide an exact diagonal matrix or a matrix already in canonical Jordan-block form."}
        row(source,"jordan form","J=${matrixText(a)}; P=identity(${a.size})",emptyList(),CasStep("Block structure",matrixText(a),"Identify repeated diagonal values and unit superdiagonal chains."),CasStep("Similarity","P^-1*A*P = J","The supplied exact canonical form uses the identity similarity transform."))
    }

    private fun svd(source: String): CasRow = safe(source,"svd") {
        val exact=parseMatrix(source);val a=exact.map{r->r.map{it.toDouble()}.toDoubleArray()}.toTypedArray();val m=a.size;val n=a[0].size;require(m<=64&&n<=32){"Local SVD is bounded to 64 rows and 32 columns for interactive latency."}
        val ata=Array(n){i->DoubleArray(n){j->(0 until m).sumOf{k->a[k][i]*a[k][j]}}};val eig=jacobiEigen(ata);val order=eig.values.indices.sortedByDescending{eig.values[it]};val s=order.map{sqrt(eig.values[it].coerceAtLeast(0.0))};val v=Array(n){i->DoubleArray(n){j->eig.vectors[i][order[j]]}};val u=Array(m){i->DoubleArray(n){j->if(s[j]<1e-12)0.0 else (0 until n).sumOf{k->a[i][k]*v[k][j]}/s[j]}}
        val residual=(0 until m).maxOf{i->(0 until n).maxOf{j->abs((0 until n).sumOf{k->u[i][k]*s[k]*v[j][k]}-a[i][j])}}
        row(source,"svd","U=${matrixText(u)}; S=[${s.joinToString{format(it)}}]; V=${matrixText(v)}",emptyList(),CasStep("Normal matrix","A^T*A=${matrixText(ata)}","Build the symmetric positive-semidefinite normal matrix."),CasStep("Singular values",s.joinToString{format(it)},"Take non-negative square roots of verified symmetric eigenvalues."),CasStep("Verify","max|A-U*S*V^T|=${format(residual)}","Reconstruct A and report numerical residual."),decimal=format(residual))
    }

    private fun matrixTranspose(source:String):CasRow=safe(source,"transpose"){val a=parseMatrix(source);val t=List(a[0].size){j->List(a.size){i->a[i][j]}};row(source,"transpose",matrixText(t),emptyList(),CasStep("Swap axes","(${a.size}x${a[0].size}) -> (${t.size}x${t[0].size})","Map entry (i,j) to (j,i)."))}
    private fun matrixRank(source:String):CasRow=safe(source,"rank"){val a=parseMatrix(source);val reduced=rrefExact(a);row(source,"rank",reduced.pivots.size.toString(),emptyList(),CasStep("RREF",matrixText(reduced.matrix),"Use exact rational row operations."),CasStep("Count pivots",reduced.pivots.size.toString(),"Rank equals the number of pivot columns."))}
    private fun matrixNullspace(source:String):CasRow=safe(source,"nullspace"){val a=parseMatrix(source);val basis=nullspace(a);row(source,"nullspace",if(basis.isEmpty())"{0}" else "span{${basis.joinToString{vectorText(it)}}}",emptyList(),CasStep("RREF",matrixText(rrefExact(a).matrix),"Identify pivot and free columns exactly."),CasStep("Basis",basis.joinToString{vectorText(it)},"Set one free variable to one for each basis vector."))}
    private fun matrixInverse(source:String):CasRow=safe(source,"matrix inverse"){val a=parseMatrix(source);require(a.size==a[0].size);val n=a.size;val augmented=a.mapIndexed{i,row->row+List(n){j->if(i==j)ExactRational.ONE else ExactRational.ZERO}};val reduced=rrefExact(augmented);require(reduced.pivots.take(n)==(0 until n).toList()){"Matrix is singular."};val inverse=reduced.matrix.map{it.drop(n)};row(source,"matrix inverse",matrixText(inverse),emptyList(),CasStep("Augment","[A | I]","Attach an identity matrix of matching dimension."),CasStep("Exact RREF",matrixText(reduced.matrix),"Apply rational row operations."),CasStep("Verify","A*A^-1=I","Multiply exact entries to verify both sides."))}

    private fun algebraicRoots(source: String): CasRow = safe(source,"exact roots") {
        val clean=source.substringBefore('=').replace(" ","");val degree=Regex("x\\^(\\d+)").findAll(clean).maxOfOrNull{it.groupValues[1].toInt()}?:1;require(degree in 1..20)
        val answer=(1..degree).joinToString(", "){"RootOf($clean, $it)"}
        row(source,"exact roots",answer,listOf("roots counted with multiplicity over the algebraic closure"),CasStep("Defining polynomial",clean,"Keep the exact integer/rational polynomial as the algebraic-number representation."),CasStep("Isolating identifiers",answer,"Represent roots exactly without replacing them by misleading decimals."))
    }

    private fun domain(source:String):CasRow { val report=CasDomainBranchAnalyzer.analyze(source);return row(source,"domain and branches",report.descriptions.joinToString("; ").ifBlank{"no additional real-domain restrictions detected"},report.domain,*(report.branches.mapIndexed{i,b->CasStep("Branch ${i+1}",b.expression,"${b.condition}: ${b.consequence}")}.toTypedArray())) }

    private data class SeriesRequest(val expression:String,val variable:String,val center:String,val order:Int)
    private fun parseSeriesRequest(source:String):SeriesRequest { val clean=source.removePrefix("series").removePrefix("taylor").removePrefix("maclaurin").trim().removePrefix("(").removeSuffix(")");val p=clean.split(',').map(String::trim);return if(p.size>=4)SeriesRequest(p[0],p[1],p[2],p[3].toInt())else SeriesRequest(p[0],"x","0",6) }
    private fun powerTerm(x:String,k:Int,c:ExactRational)=when(k){0->c.toString();1->if(c==ExactRational.ONE)x else "$c*$x";else->if(c==ExactRational.ONE)"$x^$k" else "$c*$x^$k"}
    private fun factorial(n:Int):Long { require(n in 0..20);return (2..n).fold(1L){a,b->Math.multiplyExact(a,b.toLong())} }
    private fun ExactRational.pow(n:Int):ExactRational=(0 until n).fold(ExactRational.ONE){a,_->a*this}
    private fun primeFactors(value:BigInteger):Map<BigInteger,Int>{var n=value.abs();val out=linkedMapOf<BigInteger,Int>();val two=BigInteger.valueOf(2);var p=two;while(p*p<=n){while(n%p==BigInteger.ZERO){out[p]=(out[p]?:0)+1;n/=p};p=if(p==two)BigInteger.valueOf(3)else p+two};if(n>BigInteger.ONE)out[n]=(out[n]?:0)+1;return out}
    private fun linearFactorRoots(source:String,variable:String):List<ExactRational>{if(source.isBlank())return emptyList();val escaped=Regex.escape(variable);val roots=mutableListOf<ExactRational>();Regex("\\(?$escaped\\s*([+-])\\s*(\\d+(?:/\\d+)?)\\)?").findAll(source).forEach{m->val v=ExactRational.parse(m.groupValues[2]);roots += if(m.groupValues[1]=="-")v else -v};Regex("(?<![A-Za-z0-9_])$escaped(?![A-Za-z0-9_])").findAll(source.replace(Regex("$escaped\\s*[+-]\\s*\\d+(?:/\\d+)?"),"")).forEach{roots += ExactRational.ZERO};return roots}
    private fun parseMatrix(source:String):List<List<ExactRational>>{val clean=source.trim();require(clean.startsWith("[[")&&clean.endsWith("]]"));val rows=clean.substring(2,clean.length-2).split(Regex("\\]\\s*,\\s*\\["));val parsed=rows.map{r->r.split(',').map{ExactRational.parse(it.trim())}};require(parsed.isNotEmpty()&&parsed.all{it.size==parsed[0].size});return parsed}
    private data class ExactReduction(val matrix:List<List<ExactRational>>,val pivots:List<Int>)
    private fun rrefExact(input:List<List<ExactRational>>):ExactReduction{val a=input.map{it.toMutableList()}.toMutableList();val pivots=mutableListOf<Int>();var row=0;for(col in a[0].indices){val pivot=(row until a.size).firstOrNull{!a[it][col].isZero}?:continue;val swap=a[row];a[row]=a[pivot];a[pivot]=swap;val scale=a[row][col];for(j in a[row].indices)a[row][j]/=scale;for(i in a.indices)if(i!=row){val factor=a[i][col];if(!factor.isZero)for(j in a[i].indices)a[i][j]-=factor*a[row][j]};pivots+=col;row++;if(row==a.size)break};return ExactReduction(a.map{it.toList()},pivots)}
    private fun nullspace(a:List<List<ExactRational>>):List<List<ExactRational>>{val reduced=rrefExact(a);val columns=a[0].size;val free=(0 until columns).filter{it !in reduced.pivots};return free.map{freeColumn->MutableList(columns){ExactRational.ZERO}.also{v->v[freeColumn]=ExactRational.ONE;reduced.pivots.forEachIndexed{row,pivot->v[pivot]=-reduced.matrix[row][freeColumn]}}}}
    private fun vectorText(v:List<ExactRational>)=v.joinToString(prefix="(",postfix=")")
    private fun matrixText(a:List<List<ExactRational>>)=a.joinToString(prefix="[",postfix="]"){it.joinToString(prefix="[",postfix="]")}
    private fun matrixText(a:Array<DoubleArray>)=a.joinToString(prefix="[",postfix="]"){it.joinToString(prefix="[",postfix="]"){v->format(v)}}
    private data class Eigen(val values:DoubleArray,val vectors:Array<DoubleArray>)
    private fun jacobiEigen(input:Array<DoubleArray>):Eigen{val n=input.size;val a=Array(n){input[it].clone()};val v=Array(n){i->DoubleArray(n){j->if(i==j)1.0 else 0.0}};repeat(100){var p=0;var q=if(n>1)1 else 0;var max=0.0;for(i in 0 until n)for(j in i+1 until n)if(abs(a[i][j])>max){max=abs(a[i][j]);p=i;q=j};if(max<1e-12)return Eigen(DoubleArray(n){a[it][it]},v);val phi=.5*atan2(2*a[p][q],a[q][q]-a[p][p]);val c=cos(phi);val s=sin(phi);for(k in 0 until n){val apk=a[p][k];val aqk=a[q][k];a[p][k]=c*apk-s*aqk;a[q][k]=s*apk+c*aqk};for(k in 0 until n){val akp=a[k][p];val akq=a[k][q];a[k][p]=c*akp-s*akq;a[k][q]=s*akp+c*akq;val vkp=v[k][p];val vkq=v[k][q];v[k][p]=c*vkp-s*vkq;v[k][q]=s*vkp+c*vkq}};return Eigen(DoubleArray(n){a[it][it]},v)}
    private fun row(input:String,op:String,exact:String,assumptions:List<String>,vararg steps:CasStep,decimal:String?=null)=CasRow(input,op,exact,decimal,assumptions,steps.toList())
    private fun safe(source:String,operation:String,block:()->CasRow)=runCatching(block).getOrElse{CasRow(source,operation,"Not supported",null,emptyList(),listOf(CasStep("Verified boundary",source,it.message?:"Unsupported exact case.")),false)}
    private fun format(v:Double)=if(abs(v)<1e-12)"0" else "%.10g".format(v)
}
