package com.indianservers.smartboard.core

import kotlin.math.abs
import kotlin.math.round

data class SymbolicCalculusResult(
    val source: String,
    val variable: String,
    val expression: String,
    val rules: List<String>,
    val warnings: List<String> = emptyList(),
)

class SymbolicCalculusEngine {
    fun differentiate(source: String, variable: String = "x", order: Int = 1): SymbolicCalculusResult {
        require(order in 1..12) { "Derivative order must be between 1 and 12" }
        require(variable.matches(Regex("[a-zA-Z]"))) { "Use a single-letter differentiation variable" }
        var expression = SymbolicParser(source).parse()
        val rules = linkedSetOf<String>()
        repeat(order) { expression = simplify(derivative(expression, variable, rules)) }
        return SymbolicCalculusResult(source, variable, render(expression), rules.toList())
    }

    fun integrate(source: String, variable: String = "x"): SymbolicCalculusResult? {
        require(variable.matches(Regex("[a-zA-Z]")))
        val parsed = SymbolicParser(source).parse()
        val rules = linkedSetOf<String>()
        val result = integrateNode(parsed, variable, rules)?.let(::simplify) ?: return null
        return SymbolicCalculusResult(source, variable, render(result), rules.toList())
    }

    private fun derivative(node: Sym, variable: String, rules: MutableSet<String>): Sym = when (node) {
        is Sym.Number -> { rules += "Constant rule: d(c)/d$variable = 0"; zero }
        is Sym.Variable -> {
            rules += "Variable rule: d($variable)/d$variable = 1"
            if (node.name == variable) one else zero
        }
        is Sym.Negate -> Sym.Negate(derivative(node.value, variable, rules))
        is Sym.Add -> { rules += "Sum rule: differentiate each term"; Sym.Add(derivative(node.left, variable, rules), derivative(node.right, variable, rules)) }
        is Sym.Subtract -> { rules += "Difference rule: differentiate each term"; Sym.Subtract(derivative(node.left, variable, rules), derivative(node.right, variable, rules)) }
        is Sym.Multiply -> {
            rules += "Product rule: (uv)' = u'v + uv'"
            Sym.Add(Sym.Multiply(derivative(node.left, variable, rules), node.right), Sym.Multiply(node.left, derivative(node.right, variable, rules)))
        }
        is Sym.Divide -> {
            rules += "Quotient rule: (u/v)' = (u'v − uv')/v²"
            Sym.Divide(
                Sym.Subtract(Sym.Multiply(derivative(node.left, variable, rules), node.right), Sym.Multiply(node.left, derivative(node.right, variable, rules))),
                Sym.Power(node.right, Sym.Number(2.0)),
            )
        }
        is Sym.Power -> {
            if (node.exponent is Sym.Number) {
                rules += "Power and chain rules: d(uⁿ) = n·uⁿ⁻¹·u'"
                val n = node.exponent.value
                Sym.Multiply(Sym.Multiply(Sym.Number(n), Sym.Power(node.base, Sym.Number(n - 1.0))), derivative(node.base, variable, rules))
            } else {
                rules += "General power rule: d(uᵛ) = uᵛ(v'ln(u) + vu'/u)"
                Sym.Multiply(node, Sym.Add(
                    Sym.Multiply(derivative(node.exponent, variable, rules), Sym.Function("ln", node.base)),
                    Sym.Divide(Sym.Multiply(node.exponent, derivative(node.base, variable, rules)), node.base),
                ))
            }
        }
        is Sym.Function -> {
            val inner = node.argument
            val outer = when (node.name) {
                "sin" -> { rules += "Trig rule: d(sin u) = cos(u)·u'"; Sym.Function("cos", inner) }
                "cos" -> { rules += "Trig rule: d(cos u) = −sin(u)·u'"; Sym.Negate(Sym.Function("sin", inner)) }
                "tan" -> { rules += "Trig rule: d(tan u) = u'/cos²(u)"; Sym.Divide(one, Sym.Power(Sym.Function("cos", inner), Sym.Number(2.0))) }
                "sec" -> { rules += "Trig rule: d(sec u) = sec(u)tan(u)u'"; Sym.Multiply(Sym.Function("sec", inner), Sym.Function("tan", inner)) }
                "csc" -> { rules += "Trig rule: d(csc u) = −csc(u)cot(u)u'"; Sym.Negate(Sym.Multiply(Sym.Function("csc", inner), Sym.Function("cot", inner))) }
                "cot" -> { rules += "Trig rule: d(cot u) = −csc²(u)u'"; Sym.Negate(Sym.Power(Sym.Function("csc", inner), Sym.Number(2.0))) }
                "sinh" -> { rules += "Hyperbolic rule: d(sinh u) = cosh(u)u'"; Sym.Function("cosh", inner) }
                "cosh" -> { rules += "Hyperbolic rule: d(cosh u) = sinh(u)u'"; Sym.Function("sinh", inner) }
                "tanh" -> { rules += "Hyperbolic rule: d(tanh u) = u'/cosh²(u)"; Sym.Divide(one, Sym.Power(Sym.Function("cosh", inner), Sym.Number(2.0))) }
                "asin" -> { rules += "Inverse-trig rule: d(asin u) = u'/√(1−u²)"; Sym.Divide(one, Sym.Function("sqrt", Sym.Subtract(one, Sym.Power(inner, Sym.Number(2.0))))) }
                "acos" -> { rules += "Inverse-trig rule: d(acos u) = −u'/√(1−u²)"; Sym.Negate(Sym.Divide(one, Sym.Function("sqrt", Sym.Subtract(one, Sym.Power(inner, Sym.Number(2.0)))))) }
                "atan" -> { rules += "Inverse-trig rule: d(atan u) = u'/(1+u²)"; Sym.Divide(one, Sym.Add(one, Sym.Power(inner, Sym.Number(2.0)))) }
                "exp" -> { rules += "Exponential rule: d(exp u) = exp(u)·u'"; Sym.Function("exp", inner) }
                "ln" -> { rules += "Log rule: d(ln u) = u'/u"; Sym.Divide(one, inner) }
                "log" -> { rules += "Common-log rule: d(log u) = u'/(u ln 10)"; Sym.Divide(one, Sym.Multiply(inner, Sym.Function("ln", Sym.Number(10.0)))) }
                "sqrt" -> { rules += "Root rule: d(√u) = u'/(2√u)"; Sym.Divide(one, Sym.Multiply(Sym.Number(2.0), Sym.Function("sqrt", inner))) }
                "abs" -> { rules += "Absolute-value rule away from u=0: d|u| = (u/|u|)u'"; Sym.Divide(inner, Sym.Function("abs", inner)) }
                else -> error("Unsupported differentiable function '${node.name}'")
            }
            rules += "Chain rule: multiply by the derivative of the inner expression"
            Sym.Multiply(outer, derivative(inner, variable, rules))
        }
    }

    private fun integrateNode(node: Sym, variable: String, rules: MutableSet<String>): Sym? = when (node) {
        is Sym.Number -> { rules += "Constant rule: ∫c d$variable = c$variable"; Sym.Multiply(node, Sym.Variable(variable)) }
        is Sym.Variable -> if (node.name == variable) {
            rules += "Power rule: ∫$variable d$variable = $variable²/2"
            Sym.Divide(Sym.Power(node, Sym.Number(2.0)), Sym.Number(2.0))
        } else {
            rules += "Treat other variables as constants"
            Sym.Multiply(node, Sym.Variable(variable))
        }
        is Sym.Negate -> integrateNode(node.value, variable, rules)?.let { Sym.Negate(it) }
        is Sym.Add -> {
            rules += "Linearity: integrate each term"
            val left = integrateNode(node.left, variable, rules) ?: return null
            val right = integrateNode(node.right, variable, rules) ?: return null
            Sym.Add(left, right)
        }
        is Sym.Subtract -> {
            rules += "Linearity: integrate each term"
            val left = integrateNode(node.left, variable, rules) ?: return null
            val right = integrateNode(node.right, variable, rules) ?: return null
            Sym.Subtract(left, right)
        }
        is Sym.Multiply -> when {
            !dependsOn(node.left, variable) -> integrateNode(node.right, variable, rules)?.let { rules += "Constant-multiple rule"; Sym.Multiply(node.left, it) }
            !dependsOn(node.right, variable) -> integrateNode(node.left, variable, rules)?.let { rules += "Constant-multiple rule"; Sym.Multiply(node.right, it) }
            else -> null
        }
        is Sym.Divide -> when {
            !dependsOn(node.right, variable) -> integrateNode(node.left, variable, rules)?.let { Sym.Divide(it, node.right) }
            node.left is Sym.Number && node.right is Sym.Variable && node.right.name == variable -> {
                rules += "Log integral: ∫c/$variable d$variable = c ln|$variable|"
                Sym.Multiply(node.left, Sym.Function("ln", Sym.Function("abs", node.right)))
            }
            else -> null
        }
        is Sym.Power -> {
            if (node.base is Sym.Variable && node.base.name == variable && node.exponent is Sym.Number) {
                val n = node.exponent.value
                if (abs(n + 1.0) < 1e-12) {
                    rules += "Log integral: ∫1/$variable d$variable = ln|$variable|"
                    Sym.Function("ln", Sym.Function("abs", node.base))
                } else {
                    rules += "Power rule: ∫${variable}ⁿ d$variable = ${variable}ⁿ⁺¹/(n+1)"
                    Sym.Divide(Sym.Power(node.base, Sym.Number(n + 1.0)), Sym.Number(n + 1.0))
                }
            } else null
        }
        is Sym.Function -> integrateFunction(node, variable, rules)
    }

    private fun integrateFunction(node: Sym.Function, variable: String, rules: MutableSet<String>): Sym? {
        if (node.name == "ln" && node.argument is Sym.Variable && node.argument.name == variable) {
            rules += "Log integral by parts: ∫ln(x)dx = x ln(x) − x"
            return Sym.Subtract(Sym.Multiply(node.argument, node), node.argument)
        }
        val coefficient = linearCoefficient(node.argument, variable) ?: return null
        if (abs(coefficient) < 1e-12) return Sym.Multiply(node, Sym.Variable(variable))
        return when (node.name) {
            "sin" -> { rules += "Trig integral with reverse chain rule"; Sym.Divide(Sym.Negate(Sym.Function("cos", node.argument)), Sym.Number(coefficient)) }
            "cos" -> { rules += "Trig integral with reverse chain rule"; Sym.Divide(Sym.Function("sin", node.argument), Sym.Number(coefficient)) }
            "tan" -> { rules += "Trig integral: ∫tan(u)du = −ln|cos(u)|"; Sym.Divide(Sym.Negate(Sym.Function("ln", Sym.Function("abs", Sym.Function("cos", node.argument)))), Sym.Number(coefficient)) }
            "sec" -> { rules += "Trig integral: ∫sec(u)du = ln|sec(u)+tan(u)|"; Sym.Divide(Sym.Function("ln", Sym.Function("abs", Sym.Add(Sym.Function("sec", node.argument), Sym.Function("tan", node.argument)))), Sym.Number(coefficient)) }
            "csc" -> { rules += "Trig integral: ∫csc(u)du = ln|csc(u)−cot(u)|"; Sym.Divide(Sym.Function("ln", Sym.Function("abs", Sym.Subtract(Sym.Function("csc", node.argument), Sym.Function("cot", node.argument)))), Sym.Number(coefficient)) }
            "cot" -> { rules += "Trig integral: ∫cot(u)du = ln|sin(u)|"; Sym.Divide(Sym.Function("ln", Sym.Function("abs", Sym.Function("sin", node.argument))), Sym.Number(coefficient)) }
            "sinh" -> { rules += "Hyperbolic integral with reverse chain rule"; Sym.Divide(Sym.Function("cosh", node.argument), Sym.Number(coefficient)) }
            "cosh" -> { rules += "Hyperbolic integral with reverse chain rule"; Sym.Divide(Sym.Function("sinh", node.argument), Sym.Number(coefficient)) }
            "tanh" -> { rules += "Hyperbolic integral: ∫tanh(u)du = ln(cosh(u))"; Sym.Divide(Sym.Function("ln", Sym.Function("cosh", node.argument)), Sym.Number(coefficient)) }
            "exp" -> { rules += "Exponential integral with reverse chain rule"; Sym.Divide(Sym.Function("exp", node.argument), Sym.Number(coefficient)) }
            "sqrt" -> { rules += "Root integral with reverse power rule"; Sym.Divide(Sym.Power(node.argument, Sym.Number(1.5)), Sym.Number(1.5 * coefficient)) }
            else -> null
        }
    }

    private fun linearCoefficient(node: Sym, variable: String): Double? {
        val result = simplify(derivative(node, variable, linkedSetOf()))
        return (result as? Sym.Number)?.value
    }

    private fun dependsOn(node: Sym, variable: String): Boolean = when (node) {
        is Sym.Number -> false
        is Sym.Variable -> node.name == variable
        is Sym.Negate -> dependsOn(node.value, variable)
        is Sym.Add -> dependsOn(node.left, variable) || dependsOn(node.right, variable)
        is Sym.Subtract -> dependsOn(node.left, variable) || dependsOn(node.right, variable)
        is Sym.Multiply -> dependsOn(node.left, variable) || dependsOn(node.right, variable)
        is Sym.Divide -> dependsOn(node.left, variable) || dependsOn(node.right, variable)
        is Sym.Power -> dependsOn(node.base, variable) || dependsOn(node.exponent, variable)
        is Sym.Function -> dependsOn(node.argument, variable)
    }

    private fun simplify(node: Sym): Sym = when (node) {
        is Sym.Number, is Sym.Variable -> node
        is Sym.Negate -> when (val value = simplify(node.value)) { is Sym.Number -> Sym.Number(-value.value); is Sym.Negate -> value.value; else -> Sym.Negate(value) }
        is Sym.Add -> binarySimplify(simplify(node.left), simplify(node.right), '+')
        is Sym.Subtract -> binarySimplify(simplify(node.left), simplify(node.right), '-')
        is Sym.Multiply -> binarySimplify(simplify(node.left), simplify(node.right), '*')
        is Sym.Divide -> binarySimplify(simplify(node.left), simplify(node.right), '/')
        is Sym.Power -> binarySimplify(simplify(node.base), simplify(node.exponent), '^')
        is Sym.Function -> Sym.Function(node.name, simplify(node.argument))
    }

    private fun binarySimplify(a: Sym, b: Sym, operation: Char): Sym {
        val av = (a as? Sym.Number)?.value
        val bv = (b as? Sym.Number)?.value
        if (av != null && bv != null) return Sym.Number(when (operation) { '+' -> av + bv; '-' -> av - bv; '*' -> av * bv; '/' -> av / bv; '^' -> Math.pow(av, bv); else -> error("op") })
        return when (operation) {
            '+' -> when { isZero(a) -> b; isZero(b) -> a; else -> Sym.Add(a, b) }
            '-' -> when { isZero(b) -> a; isZero(a) -> Sym.Negate(b); a == b -> zero; else -> Sym.Subtract(a, b) }
            '*' -> when {
                isZero(a) || isZero(b) -> zero
                isOne(a) -> b
                isOne(b) -> a
                a is Sym.Number && b is Sym.Divide && b.right is Sym.Number -> simplify(Sym.Multiply(Sym.Number(a.value / b.right.value), b.left))
                b is Sym.Number && a is Sym.Divide && a.right is Sym.Number -> simplify(Sym.Multiply(Sym.Number(b.value / a.right.value), a.left))
                a == b -> Sym.Power(a, Sym.Number(2.0))
                else -> Sym.Multiply(a, b)
            }
            '/' -> when { isZero(a) -> zero; isOne(b) -> a; a == b -> one; else -> Sym.Divide(a, b) }
            '^' -> when { isZero(b) -> one; isOne(b) -> a; isOne(a) -> one; else -> Sym.Power(a, b) }
            else -> error("Unknown operation")
        }
    }

    private fun render(node: Sym, parentPrecedence: Int = 0): String {
        val precedence = when (node) { is Sym.Add, is Sym.Subtract -> 1; is Sym.Multiply, is Sym.Divide -> 2; is Sym.Power -> 3; is Sym.Negate -> 4; else -> 5 }
        val text = when (node) {
            is Sym.Number -> format(node.value)
            is Sym.Variable -> node.name
            is Sym.Negate -> "-${render(node.value, precedence)}"
            is Sym.Add -> "${render(node.left, precedence)} + ${render(node.right, precedence)}"
            is Sym.Subtract -> "${render(node.left, precedence)} - ${render(node.right, precedence + 1)}"
            is Sym.Multiply -> "${render(node.left, precedence)}*${render(node.right, precedence)}"
            is Sym.Divide -> "${render(node.left, precedence)}/${render(node.right, precedence + 1)}"
            is Sym.Power -> "${render(node.base, precedence)}^${render(node.exponent, precedence)}"
            is Sym.Function -> "${node.name}(${render(node.argument)})"
        }
        return if (precedence < parentPrecedence) "($text)" else text
    }

    private fun format(value: Double): String {
        val rounded = round(value)
        return if (abs(value - rounded) < 1e-10) rounded.toLong().toString()
        else String.format(java.util.Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
    }

    private fun isZero(node: Sym) = node is Sym.Number && abs(node.value) < 1e-12
    private fun isOne(node: Sym) = node is Sym.Number && abs(node.value - 1.0) < 1e-12
    private val zero = Sym.Number(0.0)
    private val one = Sym.Number(1.0)
}

private sealed interface Sym {
    data class Number(val value: Double) : Sym
    data class Variable(val name: String) : Sym
    data class Negate(val value: Sym) : Sym
    data class Add(val left: Sym, val right: Sym) : Sym
    data class Subtract(val left: Sym, val right: Sym) : Sym
    data class Multiply(val left: Sym, val right: Sym) : Sym
    data class Divide(val left: Sym, val right: Sym) : Sym
    data class Power(val base: Sym, val exponent: Sym) : Sym
    data class Function(val name: String, val argument: Sym) : Sym
}

private class SymbolicParser(source: String) {
    private val functions = setOf("sin", "cos", "tan", "sec", "csc", "cot", "sinh", "cosh", "tanh", "asin", "acos", "atan", "exp", "ln", "log", "sqrt", "abs")
    private val tokens = tokenize(source)
    private var index = 0

    fun parse(): Sym {
        val result = expression()
        require(peek() == Token.End) { "Unexpected token '${peek().text}'" }
        return result
    }

    private fun expression(): Sym {
        var node = term()
        while (true) node = when { take("+") -> Sym.Add(node, term()); take("-") -> Sym.Subtract(node, term()); else -> return node }
    }

    private fun term(): Sym {
        var node = unary()
        while (true) node = when { take("*") -> Sym.Multiply(node, unary()); take("/") -> Sym.Divide(node, unary()); else -> return node }
    }

    private fun unary(): Sym = when { take("+") -> unary(); take("-") -> Sym.Negate(unary()); else -> power() }

    private fun power(): Sym {
        var node = primary()
        if (take("^")) node = Sym.Power(node, unary())
        return node
    }

    private fun primary(): Sym {
        val token = peek()
        if (take("(")) { val node = expression(); require(take(")")) { "Missing closing parenthesis" }; return node }
        if (token.kind == TokenKind.Number) { index++; return Sym.Number(token.text.toDouble()) }
        if (token.kind == TokenKind.Identifier) {
            index++
            val name = token.text.lowercase()
            if (name in functions && take("(")) { val argument = expression(); require(take(")")); return Sym.Function(name, argument) }
            return Sym.Variable(name)
        }
        error("Expected a number, variable, function, or parenthesized expression")
    }

    private fun take(value: String): Boolean = if (peek().text == value) { index++; true } else false
    private fun peek() = tokens[index]

    private fun tokenize(source: String): List<Token> {
        val raw = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i++
                    while (i < source.length && (source[i].isDigit() || source[i] == '.')) i++
                    raw += Token(TokenKind.Number, source.substring(start, i))
                }
                c.isLetter() || c == 'π' -> {
                    val start = i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    raw += Token(TokenKind.Identifier, source.substring(start, i).replace("π", "pi"))
                }
                c in "+-*/^()" -> { raw += Token(TokenKind.Operator, c.toString()); i++ }
                else -> error("Unsupported symbol '$c'")
            }
        }
        val result = mutableListOf<Token>()
        raw.forEachIndexed { position, token ->
            val previous = result.lastOrNull()
            val previousEndsValue = previous?.kind in setOf(TokenKind.Number, TokenKind.Identifier) || previous?.text == ")"
            val nextStartsValue = token.kind in setOf(TokenKind.Number, TokenKind.Identifier) || token.text == "("
            val functionCall = previous?.kind == TokenKind.Identifier && previous.text.lowercase() in functions && token.text == "("
            if (position > 0 && previousEndsValue && nextStartsValue && !functionCall) result += Token(TokenKind.Operator, "*")
            result += token
        }
        result += Token.End
        return result
    }

    private enum class TokenKind { Number, Identifier, Operator, End }
    private data class Token(val kind: TokenKind, val text: String) {
        companion object { val End = Token(TokenKind.End, "<end>") }
    }
}
