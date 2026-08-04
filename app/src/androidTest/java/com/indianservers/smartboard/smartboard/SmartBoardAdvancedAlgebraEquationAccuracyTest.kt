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
class SmartBoardAdvancedAlgebraEquationAccuracyTest {
    private data class Case(val number: Int, val expected: String, val ink: String = expected)

    private val cases = listOf(
        Case(1, "3x^2+5x-2=0"),
        Case(2, "2x^2-7x+3=0"),
        Case(3, "x^2-5x+6=0"),
        Case(4, "(x-3)^2=16"),
        Case(5, "(2x+1)^2=25"),
        Case(6, "x^3-6x^2+11x-6=0"),
        Case(7, "x^3+x^2-4x-4=0"),
        Case(8, "x^4-5x^2+4=0"),
        Case(9, "x^5-x^3+x-1=0"),
        Case(10, "2x^3+3x^2-5x+7=0"),
        Case(11, "(x^2+1)(x-2)=0"),
        Case(12, "(x-1)(x^2+3x+2)=0"),
        Case(13, "(x+2)(x-3)(x+1)=0"),
        Case(14, "x(x-2)(x+3)=18"),
        Case(15, "(x^2-4)/(x-2)=0"),
        Case(16, "(x+1)/(x-1)=3"),
        Case(17, "(2x-1)/(x+2)=5"),
        Case(18, "(3x+2)/(x-4)=(x-1)/2"),
        Case(19, "(x^2-1)/(x-1)=4"),
        Case(20, "(x^2+2x)/(x-3)=x+5"),
        Case(21, "1/(x+1)+1/(x-1)=3/2"),
        Case(22, "2/x+3/(x-2)=1"),
        Case(23, "(x-2)/(x+3)+(x+3)/(x-2)=2"),
        Case(24, "(x+1)/(x-2)-(x-1)/(x+2)=1"),
        Case(25, "(2x+3)/(x-1)=(x+7)/(2x-1)"),
        Case(26, "(x^2+x-2)/(x+2)=x-1"),
        Case(27, "(x^2-4)/(x^2+2x)=2/(x+2)"),
        Case(28, "(x^2+3x+2)/(x+1)=x+2"),
        Case(29, "(x^2-5x+6)/(x^2-1)=2"),
        Case(30, "(2x^2+x-3)/(x^2-4)=1"),
        Case(31, "log_2(x)=5"),
        Case(32, "log_3(x-1)=2"),
        Case(33, "log_5(2x+3)=1"),
        Case(34, "log(x^2-4)=2"),
        Case(35, "log_2(x)+log_2(x-1)=3"),
        Case(36, "log_3(x)-log_3(2)=1"),
        Case(37, "log_2(x+3)+log_2(x-3)=4"),
        Case(38, "log_5(x^2)=2"),
        Case(39, "log_2(x+1)-log_2(x-1)=1"),
        Case(40, "log(x+2)+log(x-5)=log(3)"),
        Case(41, "2^x=32"),
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
        Case(65, "xy=12;x+y=7", "xy=12\nx+y=7"),
        Case(66, "(x+1)^3=64"),
        Case(67, "(2x-1)^3=27"),
        Case(68, "x^{2/3}=4"),
        Case(69, "pi*x=6", "πx=6"),
        Case(70, "2*pi*r=22", "2πr=22"),
        Case(71, "[[2,1],[3,4]]*[[x],[y]]=[[5],[11]]", "[[2,1],[3,4]]  *  [[x],[y]]  =  [[5],[11]]"),
        Case(72, "[[1,2],[3,1]]*x=[[7,9],[5,4]]"),
        Case(73, "det([x,2;3,1])=5"),
        Case(74, "[[x,1,-1],[2,x,0],[1,-2,x]]=i"),
        Case(75, "z^2+(1-i)z+2+i=0"),
    )

    @Test
    fun reportExpectedAndDetectedForAdvancedAlgebraEquations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val pack = OfflineMathOcrModelPack(context)
        if (arguments.getString("installFormulaModel").toBoolean() &&
            pack.status().state != OfflineMathModelState.READY
        ) {
            val installation = withTimeout(600_000) { pack.install() }
            assertTrue(
                "Dedicated formula model installation failed: ${installation.exceptionOrNull()?.message}",
                installation.isSuccess,
            )
        }
        val recognizer = MultimodalMathRecognitionEngine(
            MlKitMathRecognitionAdapter(),
            DedicatedOfflineImageMathRecognitionAdapter(context, pack, MlKitImageMathRecognitionAdapter()),
        )
        val start = arguments.getString("caseStart")?.toIntOrNull()?.coerceIn(1, cases.size) ?: 1
        val limit = arguments.getString("caseLimit")?.toIntOrNull()?.coerceIn(1, cases.size) ?: cases.size
        val selected = cases.drop(start - 1).take(limit)
        var primary = 0
        var candidate = 0
        val rows = mutableListOf<String>()
        selected.forEach { case ->
            val strokes = HumanInkWriter.write(case.ink, 3_000 + case.number)
            val bounds = SmartBoardBounds.from(
                strokes.flatMap { stroke -> stroke.points.map { it.position } },
            ).expand(16f)
            val request = MathRecognitionRequestBuilder.build(
                "advanced-equation-${case.number}",
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
            val alternatives = listOf(result.latex) + result.alternatives.map { it.latex }
            val exact = equivalent(case.expected, result.latex)
            val recalled = alternatives.any { equivalent(case.expected, it) }
            if (exact) primary++
            if (recalled) candidate++
            val outcome = if (exact) "EXACT" else if (recalled) "ALTERNATIVE" else "MISS"
            val row = "ROW|${case.number}|${case.expected}|${result.latex}|$outcome"
            rows += row
            Log.i(TAG, row)
        }
        Log.i(TAG, "SUMMARY|primary=$primary/${selected.size}|candidate=$candidate/${selected.size}")
        assertEquals(selected.size, rows.size)
    }

    private fun equivalent(expected: String, actual: String) = canonical(expected) == canonical(actual)

    private fun canonical(source: String): String = runCatching {
        SmartBoardLatexAdapter.toEngineExpression(source)
    }.getOrDefault(source)
        .lowercase()
        .replace("π", "pi")
        .replace("\\pi", "pi")
        .replace("×", "*")
        .replace("−", "-")
        .replace(Regex("""\s+"""), "")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", ";")
        .replace("\n", ";")
        .replace("{", "")
        .replace("}", "")
        .replace(Regex("""\^\(([^()]+)\)"""), "^$1")
        .replace(Regex("""_\(([^()]+)\)"""), "_$1")
        .replace(Regex("""(?<=[0-9a-z)\]])\*(?=[a-z(\[])"""), "")
        .replace(Regex(""";+"""), ";")
        .trim(';')

    private companion object {
        const val TAG = "ADVANCED_EQUATION_ACCURACY"
    }
}
