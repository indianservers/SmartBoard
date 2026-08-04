package com.indianservers.smartboard.smartboard

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.recognition.DedicatedOfflineImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInputRenderer
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import com.indianservers.smartboard.smartboard.recognition.MlKitImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MlKitMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MultimodalMathRecognitionEngine
import com.indianservers.smartboard.smartboard.recognition.OfflineMathModelState
import com.indianservers.smartboard.smartboard.recognition.OfflineMathOcrModelPack
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartBoardHandwrittenEquationAccuracyTest {
    private data class Case(val number: Int, val expected: String, val ink: String = expected)

    private val cases = listOf(
        Case(1, "2x^2+5x-3=0"),
        Case(2, "3x^2-7x+2=0"),
        Case(3, "x^2-9=0"),
        Case(4, "(x+4)^2=25"),
        Case(5, "(2x-3)^2=49"),
        Case(6, "x^3-8=0"),
        Case(7, "x^3+27=0"),
        Case(8, "x^3-3x^2+2x=0"),
        Case(9, "x^4-5x^2+4=0"),
        Case(10, "2x^3+x^2-5x+2=0"),
        Case(11, "3x^4-4x^2+x-1=0"),
        Case(12, "(x^2+1)(x-3)=0"),
        Case(13, "x^5-x^3+x=0"),
        Case(14, "2x^2-3x-2=0"),
        Case(15, "(x-1)(x^2+x+1)=0"),
        Case(16, "(x-2)/(x+1)=3"),
        Case(17, "(2x+1)/(x-2)=5"),
        Case(18, "(3x-4)/(2x+5)=7"),
        Case(19, "(x^2-1)/(x-1)=6"),
        Case(20, "(x^2+3x)/(x-2)=4"),
        Case(21, "x/(x-3)+2=5"),
        Case(22, "1/(x+2)-3=4/(x+2)"),
        Case(23, "2/x+3/(x-1)=1"),
        Case(24, "x/(x+1)=2/(x-3)"),
        Case(25, "(2x+3)/(x-5)=(x+7)/(2x-1)"),
        Case(26, "(x-1)/(x+2)+(x+2)/(x-1)=5"),
        Case(27, "(x^2-4)/(x^2+2x)=2/(x+4)"),
        Case(28, "(x^2+2x+1)/(x+1)=3"),
        Case(29, "(x^2-5x+6)/(x^2-1)=2"),
        Case(30, "(x+3)/(x-3)-(x-3)/(x+3)=2"),
        Case(31, "log_2(x)=3"),
        Case(32, "log_3(x-1)=2"),
        Case(33, "log_5(2x+3)=1"),
        Case(34, "log(x^2-4)=2"),
        Case(35, "log_2(x)+log_2(x-1)=3"),
        Case(36, "log_3(x)-log_3(2)=1"),
        Case(37, "log_2(x+3)+log_2(x-3)=4"),
        Case(38, "log_5(x^2)=2"),
        Case(39, "log_2(x+1)-log_2(x-1)=1"),
        Case(40, "log(x+2)+log(x-5)=log(3)"),
        Case(41, "2^{x+1}=16"),
        Case(42, "3^{2x}=27"),
        Case(43, "5^{x-1}=25"),
        Case(44, "2^x+2^{x+1}=12"),
        Case(45, "3^x-3^{x-1}=18"),
        Case(46, "sqrt(x+5)=7", "√(x+5)=7"),
        Case(47, "sqrt(2x-1)=x+1", "√(2x-1)=x+1"),
        Case(48, "sqrt(x^2-9)=4", "√(x^2-9)=4"),
        Case(49, "sqrt(x+3)+sqrt(x-3)=4", "√(x+3)+√(x-3)=4"),
        Case(50, "sqrt(2x+1)-sqrt(x-2)=1", "√(2x+1)-√(x-2)=1"),
        Case(51, "|x-3|=7"),
        Case(52, "|2x+1|=5"),
        Case(53, "|x^2-4|=5"),
        Case(54, "|x+2|+|x-1|=5"),
        Case(55, "sin(x)=1/2"),
        Case(56, "cos(x)=sqrt(3)/2", "cos(x)=√(3)/2"),
        Case(57, "tan(x)=1"),
        Case(58, "2sin(x)+1=0"),
        Case(59, "2cos(x)-1=0"),
        Case(60, "sin^2(x)+cos^2(x)=1"),
        Case(61, "2x+3y=13;x-2y=1", "2x+3y=13\nx-2y=1"),
        Case(62, "3x-y=7;2x+y=5", "3x-y=7\n2x+y=5"),
        Case(63, "x+y+z=6;2x-y+z=3;x+2y-z=4", "x+y+z=6\n2x-y+z=3\nx+2y-z=4"),
        Case(64, "x^2+y^2=25"),
        Case(65, "x^2-y^2=9"),
        Case(66, "xy=12;x+y=7", "xy=12\nx+y=7"),
        Case(67, "xy+x+y=6"),
        Case(68, "x^2+3x+2=0"),
        Case(69, "x^2-6x+8=0"),
        Case(70, "(x+1)^3=64"),
        Case(71, "(2x-1)^3=27"),
        Case(72, "x^{2/3}=4"),
        Case(73, "pi*x=6", "πx=6"),
        Case(74, "2*pi*r=22", "2πr=22"),
        Case(75, "pi*r^2=49*pi", "πr^2=49π"),
    )

    @Test
    fun reportExpectedAndDetectedForAllHandwrittenEquations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val modelPack = OfflineMathOcrModelPack(context)
        if (arguments.getString("installFormulaModel").toBoolean() &&
            modelPack.status().state != OfflineMathModelState.READY
        ) {
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
        val start = arguments.getString("caseStart")?.toIntOrNull()?.coerceIn(1, cases.size) ?: 1
        val limit = arguments.getString("caseLimit")?.toIntOrNull()?.coerceIn(1, cases.size) ?: cases.size
        val selected = cases.drop(start - 1).take(limit)
        var primaryMatches = 0
        var candidateMatches = 0
        val rows = mutableListOf<String>()
        selected.forEach { case ->
            val strokes = HumanInkWriter.write(case.ink, seed = 2_000 + case.number)
            assertTrue("Case ${case.number} generated no handwriting strokes", strokes.isNotEmpty())
            val bounds = SmartBoardBounds.from(
                strokes.flatMap { stroke -> stroke.points.map { it.position } },
            ).expand(16f)
            val request = MathRecognitionRequestBuilder.build(
                "equation-accuracy-${case.number}",
                strokes,
                case.number.toLong(),
            )
            val input = MathRecognitionInput(
                strokes,
                bounds,
                MathRecognitionInputRenderer.render(strokes, bounds),
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
            val outcome = when {
                primaryMatch -> "EXACT"
                candidateMatch -> "ALTERNATIVE"
                else -> "MISS"
            }
            val row = "ROW|${case.number}|${case.expected}|${result.latex}|$outcome"
            rows += row
            Log.i(TAG, row)
        }
        Log.i(TAG, "SUMMARY|primary=$primaryMatches/${selected.size}|candidate=$candidateMatches/${selected.size}")
        assertEquals(selected.size, rows.size)
    }

    private fun equivalent(expected: String, detected: String): Boolean =
        canonical(expected) == canonical(detected)

    private fun canonical(source: String): String {
        var value = runCatching { SmartBoardLatexAdapter.toEngineExpression(source) }.getOrDefault(source)
            .lowercase()
            .replace("π", "pi")
            .replace("×", "*")
            .replace("−", "-")
            .replace(Regex("""\s+"""), "")
            .replace("\\,", "")
            .replace("\\;", ";")
            .replace("\\pi", "pi")
            .replace("\\sqrt", "sqrt")
            .replace(Regex("""\\(sin|cos|tan|log)(?=\b|_)"""), "$1")
            .replace("{", "")
            .replace("}", "")
            .replace(Regex("""\^\(([^()]+)\)"""), "^$1")
            .replace(Regex("""_\(([^()]+)\)"""), "_$1")
            .replace("\\\\", ";")
            .replace("\n", ";")
            .replace(Regex(""";+"""), ";")
            .trim(';')
        value = value.replace(Regex("""(?<=[0-9a-z)])\*(?=[a-z(])"""), "")
        return value
    }

    private companion object {
        const val TAG = "EQUATION_ACCURACY"
    }
}
