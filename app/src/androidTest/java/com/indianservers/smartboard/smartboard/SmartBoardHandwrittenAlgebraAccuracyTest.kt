package com.indianservers.smartboard.smartboard

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInputRenderer
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import com.indianservers.smartboard.smartboard.recognition.DedicatedOfflineImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MlKitImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MlKitMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MultimodalMathRecognitionEngine
import com.indianservers.smartboard.smartboard.recognition.OfflineMathOcrModelPack
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartBoardHandwrittenAlgebraAccuracyTest {
    private data class Case(val number: Int, val expected: String, val ink: String = expected)

    private val cases = listOf(
        Case(1, "3x^2+4x-7"),
        Case(2, "a^2-5a+6"),
        Case(3, "2m^3-7m^2+4m-1"),
        Case(4, "5p^4+3p^2-p+8"),
        Case(5, "x^2y+3xy^2-4y"),
        Case(6, "7a^3b^2-2ab+5"),
        Case(7, "(x+3)^2"),
        Case(8, "(2y-5)^3"),
        Case(9, "(3a+2b)(a-b)"),
        Case(10, "(x^2-4)(x+2)"),
        Case(11, "(p^2+3p+2)/(p+1)"),
        Case(12, "(2x^3-5x^2+7x)/x"),
        Case(13, "x^{-2}+3x^{-1}-4"),
        Case(14, "(a^{-1}+b^{-1})^2"),
        Case(15, "(x^3y^{-2}z)^2"),
        Case(16, "a^{3/2}+2a^{1/2}-1"),
        Case(17, "(x^{1/2}-y^{1/2})^2"),
        Case(18, "(x^2-y^2)/(x+y)"),
        Case(19, "(a^3+b^3)/(a+b)"),
        Case(20, "(x-y)^2-(x+y)^2"),
        Case(21, "2x^2+3x^{-1}-5x^{-2}"),
        Case(22, "(x^2+1)(x^2-1)"),
        Case(23, "x^4-5x^2+4"),
        Case(24, "(a+b)^3"),
        Case(25, "(a-b)^3"),
        Case(26, "x^2+1/x^2"),
        Case(27, "a^2+b^2+2ab"),
        Case(28, "a^2+b^2-2ab"),
        Case(29, "(x+1/x)^2"),
        Case(30, "(a+1/a)^3"),
        Case(31, "(x^3-1)/(x-1)"),
        Case(32, "(x^3+1)/(x+1)"),
        Case(33, "x^5-x^3+x"),
        Case(34, "8x^3+27y^3"),
        Case(35, "27a^3-64b^3"),
        Case(36, "(2x+3y)^2"),
        Case(37, "(3a-2b)^2"),
        Case(38, "(x+y)(x^2-xy+y^2)"),
        Case(39, "(x-y)(x^2+xy+y^2)"),
        Case(40, "x^{10}+x^5+1"),
        Case(41, "(x^2+2x+1)^3"),
        Case(42, "(2a^2-3b^3)^2"),
        Case(43, "(2x^{-2}y^3)/(3x^2y^{-1})"),
        Case(44, "((x^2y^{-3})^2)/(x^{-1}y^4)"),
        Case(45, "(a^m)^n"),
        Case(46, "a^m*a^n"),
        Case(47, "a^m/a^n"),
        Case(48, "(ab)^n"),
        Case(49, "(a/b)^n"),
        Case(50, "a^0+a^1+a^2+...+a^n"),
        Case(51, "(1-r^{n+1})/(1-r)"),
        Case(52, "x^n-y^n"),
        Case(53, "x^n+y^n"),
        Case(54, "(x+1)^n"),
        Case(55, "(1-x)^n"),
        Case(56, "C(n,r)=n!/(r!(n-r)!)"),
        Case(57, "(x+y)^n"),
        Case(58, "(x-y)^n"),
        Case(59, "∑_{k=1}^{n}k^2"),
        Case(60, "∑_{k=1}^{n}k^3"),
        Case(61, "x_i^2+y_j^2"),
        Case(62, "∑_{i=1}^{n}x_i"),
        Case(63, "∏_{i=1}^{n}a_i"),
        Case(64, "a_{n+1}=a_n+d"),
        Case(65, "a_n=a_1+(n-1)d"),
        Case(66, "b_{n+1}=r*b_n"),
        Case(67, "b_n=b_1*r^{n-1}"),
        Case(68, "log_a(xy)=log_a(x)+log_a(y)"),
        Case(69, "log_a(x/y)=log_a(x)-log_a(y)"),
        Case(70, "log_a(x^n)=n*log_a(x)"),
        Case(71, "e^{x+y}=e^x*e^y"),
        Case(72, "e^{x-y}=e^x/e^y"),
        Case(73, "ln(xy)=ln(x)+ln(y)"),
        Case(74, "ln(x/y)=ln(x)-ln(y)"),
        Case(75, "e^{ln(x)}=x"),
        Case(76, "sin^2(x)+cos^2(x)=1"),
        Case(77, "1+tan^2(x)=sec^2(x)"),
        Case(78, "1+cot^2(x)=csc^2(x)"),
        Case(79, "sin(x+y)=sin(x)cos(y)+cos(x)sin(y)"),
        Case(80, "cos(x+y)=cos(x)cos(y)-sin(x)sin(y)"),
    )

    @Test
    fun reportPrimaryAndAlternativeAccuracyForAllHumanWrittenExpressions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val modelPack = OfflineMathOcrModelPack(context)
        if (arguments.getString("installFormulaModel").toBoolean()) {
            val installation = withTimeout(600_000) { modelPack.install() }
            assertTrue(
                "Dedicated formula model installation failed: ${installation.exceptionOrNull()?.message}",
                installation.isSuccess,
            )
        }
        val recognizer = MultimodalMathRecognitionEngine(
            MlKitMathRecognitionAdapter(),
            DedicatedOfflineImageMathRecognitionAdapter(
                context,
                modelPack,
                MlKitImageMathRecognitionAdapter(),
            ),
        )
        var primaryMatches = 0
        var candidateMatches = 0
        val detectedRows = mutableListOf<String>()
        val requestedLimit = arguments
            .getString("caseLimit")
            ?.toIntOrNull()
            ?.coerceIn(1, cases.size)
            ?: cases.size
        val requestedStart = arguments
            .getString("caseStart")
            ?.toIntOrNull()
            ?.coerceIn(1, cases.size)
            ?: 1
        val selectedCases = cases.drop(requestedStart - 1).take(requestedLimit)
        selectedCases.forEach { case ->
            val strokes = HumanInkWriter.write(case.ink, seed = 700 + case.number)
            assertTrue("Case ${case.number} generated no handwriting strokes", strokes.isNotEmpty())
            val bounds = SmartBoardBounds.from(
                strokes.flatMap { stroke -> stroke.points.map { it.position } },
            ).expand(16f)
            val request = MathRecognitionRequestBuilder.build(
                "algebra-accuracy-${case.number}",
                strokes,
                case.number.toLong(),
            )
            val png = MathRecognitionInputRenderer.render(strokes, bounds)
            val input = MathRecognitionInput(
                strokes,
                bounds,
                png,
                MathRecognitionRequestBuilder.fingerprint(request),
            )
            val result = withTimeout(120_000) {
                recognizer.recognize(
                    input,
                    MathRecognitionOptions(languageTag = "en-US", maximumAlternatives = 8),
                ).result
            }
            val candidates = listOf(result.latex) + result.alternatives.map { it.latex }
            val primaryMatch = equivalent(case.expected, result.latex)
            val candidateMatch = candidates.any { equivalent(case.expected, it) }
            if (primaryMatch) primaryMatches++
            if (candidateMatch) candidateMatches++
            val row = "ROW|${case.number}|${case.expected}|${result.latex}|" +
                "${if (primaryMatch) "EXACT" else if (candidateMatch) "ALTERNATIVE" else "MISS"}"
            detectedRows += row
            Log.i(TAG, row)
        }
        val summary = "SUMMARY|primary=$primaryMatches/${selectedCases.size}|candidate=$candidateMatches/${selectedCases.size}"
        Log.i(TAG, summary)
        assertEquals(selectedCases.size, detectedRows.size)
        assertTrue("The recognizer returned an empty primary result", detectedRows.none { it.endsWith("||MISS") })
    }

    private fun equivalent(expected: String, detected: String): Boolean =
        canonical(expected) == canonical(detected)

    private fun canonical(value: String): String = runCatching {
        SmartBoardLatexAdapter.toEngineExpression(value)
    }.getOrDefault(value).lowercase()
        .replace(Regex("""\s+"""), "")
        .replace("×", "*")
        .replace("−", "-")
        .replace("²", "^2")
        .replace("³", "^3")
        .replace("\\left", "")
        .replace("\\right", "")
        .replace("\\cdot", "*")
        .replace("\\times", "*")
        .replace(Regex("""\\(sin|cos|tan|sec|csc|cot|log|ln|exp)(?=\b|_)"""), "$1")
        .replace("∑", "sum")
        .replace("∏", "product")
        .replace("\\sum", "sum")
        .replace("\\prod", "product")
        .replace("{", "")
        .replace("}", "")
        .replace(Regex("""(sum|product)_?([a-z])=1\^([a-z])(.+)"""), "$1($2=1..$3,$4)")
        .replace(Regex("""_\(([^()]+)\)"""), "_$1")
        .replace(Regex("""\^\((ln\([^)]+\))\)"""), "^$1")
        .replace(Regex("""\^\(([^()]+)\)"""), "^$1")
        .replace(Regex("""\*"""), "")

    private companion object {
        const val TAG = "ALGEBRA_ACCURACY"
    }
}
