package com.indianservers.smartboard

internal fun displayLatexFormula(source: String): String {
    val cleaned = source
        .replace("\\\\", "\\")
        .replace(Regex("""\\(?:begin|end)\{[^}]*\}"""), "")
        .replace(Regex("""\\(?:left|right|,|;|!| )"""), "")
        .replace(Regex("""\\text\{([^}]*)\}""")) { it.groupValues[1] }
        .replace(Regex("""\\operatorname\{([^}]*)\}""")) { it.groupValues[1] }

    return cleaned
        .renderLatexFractions()
        .renderLatexRoots()
        .renderLatexCommands()
        .renderScripts('^', superscripts)
        .renderScripts('_', subscripts)
        .replace("{", "")
        .replace("}", "")
        .replace("  ", " ")
        .trim()
}

internal fun latexStyleFormula(source: String): String = displayLatexFormula(source)

private val superscripts = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'T' to 'ᵀ',
)

private val subscripts = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ',
    'j' to 'ⱼ', 'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ',
    't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
)

private fun String.renderLatexFractions(): String {
    var text = this
    while (true) {
        val start = text.indexOf("\\frac")
        if (start < 0) return text
        val numerator = text.latexGroupAfter(start + 5) ?: return text.replace("\\frac", "")
        val denominator = text.latexGroupAfter(numerator.nextIndex) ?: return text.replace("\\frac", "")
        val before = text.substring(0, start)
        val after = text.substring(denominator.nextIndex)
        text = before + numerator.value.renderLatexInline().asFractionPart() + "⁄" + denominator.value.renderLatexInline().asFractionPart() + after
    }
}

private fun String.renderLatexRoots(): String {
    var text = this
    while (true) {
        val start = text.indexOf("\\sqrt")
        if (start < 0) return text
        val degree = if (text.getOrNull(start + 5) == '[') text.bracketGroupAfter(start + 5) else null
        val groupStart = degree?.nextIndex ?: (start + 5)
        val radicand = text.latexGroupAfter(groupStart) ?: return text.replace("\\sqrt", "√")
        val before = text.substring(0, start)
        val after = text.substring(radicand.nextIndex)
        val rootPrefix = degree?.value?.renderLatexInline()?.toSuperscript().orEmpty()
        text = before + rootPrefix + "√(" + radicand.value.renderLatexInline() + ")" + after
    }
}

private fun String.renderLatexCommands(): String {
    val replacements = linkedMapOf(
        "\\pm" to "±", "\\mp" to "∓", "\\times" to "×", "\\cdot" to "·", "\\div" to "÷",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠",
        "\\approx" to "≈", "\\sim" to "∼", "\\equiv" to "≡", "\\to" to "→", "\\infty" to "∞",
        "\\cup" to "∪", "\\cap" to "∩", "\\subseteq" to "⊆", "\\subset" to "⊂", "\\in" to "∈",
        "\\pi" to "π", "\\theta" to "θ", "\\lambda" to "λ", "\\phi" to "φ", "\\alpha" to "α",
        "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\Delta" to "Δ", "\\sigma" to "σ",
        "\\Sigma" to "Σ", "\\mu" to "μ", "\\rho" to "ρ", "\\omega" to "ω", "\\Omega" to "Ω",
        "\\ell" to "ℓ", "\\varepsilon" to "ε", "\\epsilon" to "ε", "\\hat" to "", "\\bar" to "",
        "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\sec" to "sec", "\\csc" to "csc",
        "\\cot" to "cot", "\\log" to "log", "\\ln" to "ln", "\\lim" to "lim", "\\int" to "∫",
        "\\sum" to "Σ", "\\prod" to "Π", "\\nabla" to "∇", "\\partial" to "∂", "\\vec" to "",
        "\\lVert" to "‖", "\\rVert" to "‖", "\\langle" to "⟨", "\\rangle" to "⟩",
        "\\tilde" to "", "\\mathbf" to "", "\\mathrm" to "",
        "\\begin" to "", "\\end" to "",
    )
    var text = this
    replacements.forEach { (raw, pretty) -> text = text.replace(raw, pretty) }
    return text
        .replace("<=>", "⇔")
        .replace("<=", "≤")
        .replace(">=", "≥")
        .replace("!=", "≠")
        .replace("->", "→")
}

private fun String.renderScripts(marker: Char, alphabet: Map<Char, Char>): String {
    var text = this
    while (true) {
        val index = text.indexOf(marker)
        if (index < 0 || index == text.lastIndex) return text
        val group = text.latexGroupAfter(index + 1)
        if (group != null) {
            text = text.substring(0, index) + group.value.renderLatexInline().mapScript(alphabet) + text.substring(group.nextIndex)
            continue
        }
        val value = text.getOrNull(index + 1)?.toString().orEmpty()
        text = text.substring(0, index) + value.mapScript(alphabet) + text.substring((index + 2).coerceAtMost(text.length))
    }
}

private fun String.renderLatexInline() = this.renderLatexFractions().renderLatexRoots().renderLatexCommands().renderScripts('^', superscripts).renderScripts('_', subscripts).replace("{", "").replace("}", "")

private fun String.asFractionPart(): String = if (length <= 3 || all { it.isLetterOrDigit() || it in "₀₁₂₃₄₅₆₇₈₉⁰¹²³⁴⁵⁶⁷⁸⁹" }) this else "($this)"

private fun String.mapScript(alphabet: Map<Char, Char>) = map { alphabet[it] ?: it }.joinToString("")

private fun String.toSuperscript() = mapScript(superscripts)

private data class LatexGroup(val value: String, val nextIndex: Int)

private fun String.latexGroupAfter(start: Int): LatexGroup? = delimitedGroupAfter(start, '{', '}')

private fun String.bracketGroupAfter(start: Int): LatexGroup? = delimitedGroupAfter(start, '[', ']')

private fun String.delimitedGroupAfter(start: Int, open: Char, close: Char): LatexGroup? {
    var index = start
    while (index < length && this[index].isWhitespace()) index++
    if (getOrNull(index) != open) return null
    var depth = 0
    for (cursor in index until length) {
        when (this[cursor]) {
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return LatexGroup(substring(index + 1, cursor), cursor + 1)
            }
        }
    }
    return null
}
