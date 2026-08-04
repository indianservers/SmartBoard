package com.indianservers.smartboard.smartboard

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.smartboard.core.TypedGraphEngine
import com.indianservers.smartboard.core.TypedGraphExpressionParser
import com.indianservers.smartboard.smartboard.integration.SmartBoardGraphAdapter
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInputRenderer
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import com.indianservers.smartboard.smartboard.recognition.MlKitMathRecognitionAdapter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartBoardHandwrittenGraphCorpusTest {
    private data class Case(val name: String, val ink: String, val graph: String)

    private val cases = listOf(
        Case("Linear", "y=x", "y=x"),
        Case("Constant", "y=5", "y=5"),
        Case("Absolute Value", "y=|x|", "y=abs(x)"),
        Case("Quadratic", "y=x^2", "y=x^2"),
        Case("Cubic", "y=x^3", "y=x^3"),
        Case("Quartic", "y=x^4", "y=x^4"),
        Case("Square Root", "y=√x", "y=sqrt(x)"),
        Case("Cube Root", "y=^3√x", "y=x^(1/3)"),
        Case("Reciprocal", "y=1/x", "y=1/x"),
        Case("Reciprocal Squared", "y=1/x^2", "y=1/x^2"),
        Case("Exponential", "y=2^x", "y=2^x"),
        Case("Logarithmic", "y=logx", "y=log(x)"),
        Case("Sine", "y=sinx", "y=sin(x)"),
        Case("Cosine", "y=cosx", "y=cos(x)"),
        Case("Tangent", "y=tanx", "y=tan(x)"),
        Case("Shifted Absolute Value", "y=|x-a|+b", "y=abs(x-a)+b"),
        Case("Vertex-form Parabola", "y=a(x-h)^2+k", "y=a*(x-h)^2+k"),
        Case("Natural Exponential", "y=e^x", "y=e^x"),
        Case("Natural Logarithm", "y=lnx", "y=ln(x)"),
        Case("Secant", "y=secx", "y=sec(x)"),
        Case("Cosecant", "y=cscx", "y=csc(x)"),
        Case("Cotangent", "y=cotx", "y=cot(x)"),
        Case("Hyperbola", "x^2/9-y^2/4=1", "x^2/9-y^2/4=1"),
        Case("Ellipse", "x^2/9+y^2/4=1", "x^2/9+y^2/4=1"),
        Case("Circle", "x^2+y^2=25", "x^2+y^2=25"),
        Case("General Parabola", "y=2x^2+3x+1", "y=2*x^2+3*x+1"),
        Case("Polar Cardioid", "r=2+2cost", "r=2+2*cos(theta)"),
        Case("Parametric Circle", "x(t)=cost;y(t)=sint", "x(t)=cos(t);y(t)=sin(t)"),
        Case("Piecewise", "piecewise{x<0:-x;x>=0:x}", "piecewise{x<0:-x;x>=0:x}"),
        Case("Rational", "y=(x+1)/(x-1)", "y=(x+1)/(x-1)"),
        Case("Logistic", "y=1/(1+e^(-x))", "y=1/(1+e^(-x))"),
        Case("Gaussian", "y=e^(-x^2)", "y=e^(-x^2)"),
        Case("Floor", "y=floorx", "y=floor(x)"),
        Case("Ceiling", "y=ceilx", "y=ceil(x)"),
        Case("Signum", "y=signx", "y=sign(x)"),
        Case("Hyperbolic Sine", "y=sinhx", "y=sinh(x)"),
        Case("Hyperbolic Cosine", "y=coshx", "y=cosh(x)"),
        Case("Hyperbolic Tangent", "y=tanhx", "y=tanh(x)"),
    )

    private val imageCases = listOf(
        Case("01 Linear", "y=x", "y=x"),
        Case("02 Shifted linear", "y=2x+1", "y=2*x+1"),
        Case("03 Negative linear", "y=-x+2", "y=-x+2"),
        Case("04 Positive constant", "y=5", "y=5"),
        Case("05 Negative constant", "y=-3", "y=-3"),
        Case("06 Quadratic", "y=x^2", "y=x^2"),
        Case("07 Negative quadratic", "y=-x^2", "y=-x^2"),
        Case("08 Cubic", "y=x^3", "y=x^3"),
        Case("09 Negative cubic", "y=-x^3", "y=-x^3"),
        Case("10 Quartic", "y=x^4", "y=x^4"),
        Case("11 Negative quartic", "y=-x^4", "y=-x^4"),
        Case("12 Absolute value", "y=|x|", "y=abs(x)"),
        Case("13 Shifted absolute right", "y=|x-1|", "y=abs(x-1)"),
        Case("14 Shifted absolute left", "y=|x+2|", "y=abs(x+2)"),
        Case("15 Square root", "y=√x", "y=sqrt(x)"),
        Case("16 Cube root", "y=^3√x", "y=x^(1/3)"),
        Case("17 Reciprocal", "y=1/x", "y=1/x"),
        Case("18 Negative reciprocal", "y=-1/x", "y=-1/x"),
        Case("19 Reciprocal squared", "y=1/x^2", "y=1/x^2"),
        Case("20 Negative reciprocal squared", "y=-1/x^2", "y=-1/x^2"),
        Case("21 Exponential base two", "y=2^x", "y=2^x"),
        Case("22 Exponential decay", "y=(1/2)^x", "y=(1/2)^x"),
        Case("23 Natural exponential", "y=e^x", "y=e^x"),
        Case("24 Natural logarithm", "y=lnx", "y=ln(x)"),
        Case("25 Common logarithm", "y=logx", "y=log(x)"),
        Case("26 Sine", "y=sinx", "y=sin(x)"),
        Case("27 Cosine", "y=cosx", "y=cos(x)"),
        Case("28 Tangent", "y=tanx", "y=tan(x)"),
        Case("29 Cotangent", "y=cotx", "y=cot(x)"),
        Case("30 Secant", "y=secx", "y=sec(x)"),
        Case("31 Cosecant", "y=cscx", "y=csc(x)"),
        Case("32 Double-frequency sine", "y=sin2x", "y=sin(2*x)"),
        Case("33 Double-frequency cosine", "y=cos2x", "y=cos(2*x)"),
        Case("34 Phase-shifted sine", "y=sin(x+1)", "y=sin(x+1)"),
        Case("35 Phase-shifted cosine", "y=cos(x+1)", "y=cos(x+1)"),
        Case("36 Vertically shifted sine", "y=sinx+1", "y=sin(x)+1"),
        Case("37 Vertically shifted cosine", "y=cosx-1", "y=cos(x)-1"),
        Case("38 Scaled sine", "y=2sinx", "y=2*sin(x)"),
        Case("39 Scaled cosine", "y=1/2cosx", "y=(1/2)*cos(x)"),
        Case("40 Raised sine", "y=sinx+2", "y=sin(x)+2"),
        Case("41 Sinc", "y=sinx/x", "y=sin(x)/x"),
        Case("42 Absolute sine", "y=|sinx|", "y=abs(sin(x))"),
        Case("43 Greatest integer", "y=floorx", "y=floor(x)"),
        Case("44 Fractional part", "y=x-floorx", "y=x-floor(x)"),
        Case("45 Signum", "y=signx", "y=sign(x)"),
        Case("46 Removable rational", "y=(x^2-1)/(x+1)", "y=(x^2-1)/(x+1)"),
        Case("47 Rational asymptote", "y=(x+1)/(x-1)", "y=(x+1)/(x-1)"),
        Case("48 Bounded rational", "y=x^2/(x^2+1)", "y=x^2/(x^2+1)"),
        Case("49 Absolute rational", "y=|x|/(1+|x|)", "y=abs(x)/(1+abs(x))"),
        Case("50 Logistic", "y=1/(1+e^(-x))", "y=1/(1+e^(-x))"),
        Case("51 Quintic", "y=x^5-5x^3+x", "y=x^5-5*x^3+x"),
        Case("52 Cubic rational", "y=(x^3-3x)/(x^2+1)", "y=(x^3-3*x)/(x^2+1)"),
        Case("53 Radical difference", "y=√(x+1)-√(x-1)", "y=sqrt(x+1)-sqrt(x-1)"),
        Case("54 Log difference", "y=ln(x+1)-ln(x-1)", "y=ln(x+1)-ln(x-1)"),
        Case("55 Gaussian", "y=e^(-x^2)", "y=e^(-x^2)"),
        Case("56 Trig rational sine", "y=sinx/(1+cosx)", "y=sin(x)/(1+cos(x))"),
        Case("57 Trig rational cosine", "y=cosx/(1+sinx)", "y=cos(x)/(1+sin(x))"),
        Case("58 Chirped absolute envelope", "y=|x|sinx", "y=abs(x)*sin(x)"),
        Case("59 Oscillatory limit", "y=xsin(1/x)", "y=x*sin(1/x)"),
        Case("60 Cosine limit", "y=(1-cosx)/x^2", "y=(1-cos(x))/x^2"),
        Case("61 Log quotient", "y=lnx/x", "y=ln(x)/x"),
        Case("62 Exponential quotient", "y=(e^x-1)/(e^x+1)", "y=(e^x-1)/(e^x+1)"),
        Case("63 Damped sine", "y=sinx/(x^2+1)", "y=sin(x)/(x^2+1)"),
        Case("64 Gaussian absolute", "y=|x|e^(-x^2)", "y=abs(x)*e^(-x^2)"),
        Case("65 Bernoulli kernel", "y=x/(e^x-1)", "y=x/(e^x-1)"),
    )

    @Test
    fun humanStyleInkIsDetectedAndEveryEquationProducesDrawableGraphOutput() = runBlocking {
        val recognizer = MlKitMathRecognitionAdapter()
        val report = mutableListOf<String>()
        imageCases.forEachIndexed { index, case ->
            val strokes = HumanInkWriter.write(case.ink, seed = index + 17)
            val bounds = SmartBoardBounds.from(strokes.flatMap { stroke -> stroke.points.map { it.position } }).expand(16f)
            val request = MathRecognitionRequestBuilder.build("human-graph-$index", strokes, index.toLong())
            val png = MathRecognitionInputRenderer.render(strokes, bounds)
            val recognition = withTimeout(120_000) {
                recognizer.recognize(
                    MathRecognitionInput(strokes, bounds, png, MathRecognitionRequestBuilder.fingerprint(request)),
                    MathRecognitionOptions(languageTag = "en-US", maximumAlternatives = 8),
                )
            }
            val candidates = listOf(recognition.latex) + recognition.alternatives.map { it.latex }
            val detected = candidates.firstOrNull { candidate -> compatible(case.graph, candidate) }
            val handoff = SmartBoardGraphAdapter.prepare(case.graph).getOrThrow()
            assertTrue("${case.name} did not route to the 2D graph module", handoff.route == "graph2d")
            val typed = TypedGraphExpressionParser.parse(handoff.expression)
            val sample = TypedGraphEngine().sample(
                typed,
                parameterValues = typed.parameters.associateWith { 1.0 },
                samples = 96,
            )
            val drawableCount = sample.curves.sumOf { it.points.size } +
                sample.implicitSegments.size +
                sample.inequalityCells.count { it.satisfied }
            assertTrue("${case.name} produced no drawable plot output", drawableCount > 0)
            report += "${case.name}: ink=${case.ink}, detected=${candidates.joinToString(" | ")}, " +
                "matched=${detected ?: "NO"}, drawable=$drawableCount"
        }
        report.forEach { Log.i("SMARTBOARD_HANDWRITING", it) }
        val matches = report.count { !it.contains("matched=NO") }
        Log.i("SMARTBOARD_HANDWRITING", "SUMMARY $matches/${imageCases.size} handwriting matches; ${imageCases.size}/${imageCases.size} drawable graphs")
        assertTrue("Every human-style formula must be available as a recognized candidate; report=$report", matches == imageCases.size)
    }

    private fun compatible(expected: String, actual: String): Boolean {
        fun canonical(value: String) = value.lowercase()
            .replace(" ", "")
            .replace("×", "*")
            .replace("²", "^2")
            .replace("³", "^3")
            .replace("∣", "|")
            .replace(Regex("""[()]"""), "")
            .removePrefix("y=")
            .replace("absx-a", "|x-a|")
            .replace("absx", "|x|")
            .replace("sqrtx", "√x")
        val want = canonical(expected)
        val got = canonical(actual)
        return got == want || got.contains(want)
    }
}

internal object HumanInkWriter {
    private typealias Point = Pair<Float, Float>
    private typealias Glyph = List<List<Point>>

    private val glyphs: Map<Char, Glyph> = mapOf(
        'y' to listOf(listOf(.05f to .08f, .38f to .52f, .68f to .08f), listOf(.38f to .52f, .28f to 1.02f)),
        'x' to listOf(listOf(.05f to .08f, .68f to .92f), listOf(.68f to .08f, .05f to .92f)),
        '=' to listOf(listOf(.02f to .36f, .72f to .36f), listOf(.02f to .66f, .72f to .66f)),
        '|' to listOf(listOf(.35f to .02f, .35f to .98f)),
        '/' to listOf(listOf(.08f to .98f, .68f to .02f)),
        '+' to listOf(listOf(.05f to .50f, .70f to .50f), listOf(.38f to .16f, .38f to .84f)),
        '-' to listOf(listOf(.05f to .52f, .70f to .52f)),
        '*' to listOf(listOf(.08f to .18f, .68f to .82f), listOf(.68f to .18f, .08f to .82f)),
        '(' to listOf(listOf(.58f to .02f, .28f to .22f, .14f to .52f, .28f to .82f, .58f to .98f)),
        ')' to listOf(listOf(.14f to .02f, .44f to .22f, .58f to .52f, .44f to .82f, .14f to .98f)),
        '[' to listOf(listOf(.62f to .04f, .18f to .04f, .18f to .96f, .62f to .96f)),
        ']' to listOf(listOf(.12f to .04f, .56f to .04f, .56f to .96f, .12f to .96f)),
        '{' to listOf(listOf(.58f to .02f, .30f to .12f, .30f to .40f, .08f to .50f, .30f to .60f, .30f to .88f, .58f to .98f)),
        '}' to listOf(listOf(.08f to .02f, .36f to .12f, .36f to .40f, .58f to .50f, .36f to .60f, .36f to .88f, .08f to .98f)),
        '<' to listOf(listOf(.68f to .12f, .08f to .50f, .68f to .88f)),
        '>' to listOf(listOf(.08f to .12f, .68f to .50f, .08f to .88f)),
        ':' to listOf(listOf(.35f to .28f, .36f to .30f), listOf(.35f to .72f, .36f to .74f)),
        ';' to listOf(listOf(.35f to .25f, .36f to .27f), listOf(.38f to .62f, .36f to .78f, .24f to .92f)),
        ',' to listOf(listOf(.40f to .72f, .36f to .84f, .24f to .98f)),
        '.' to listOf(listOf(.35f to .88f, .37f to .90f)),
        '!' to listOf(listOf(.35f to .04f, .35f to .68f), listOf(.35f to .90f, .36f to .92f)),
        '√' to listOf(listOf(.02f to .55f, .20f to .86f, .42f to .12f, .82f to .12f)),
        'π' to listOf(
            listOf(.06f to .26f, .70f to .26f),
            listOf(.20f to .26f, .18f to .92f),
            listOf(.58f to .26f, .60f to .92f),
        ),
        '1' to listOf(listOf(.18f to .25f, .38f to .05f, .38f to .92f), listOf(.15f to .92f, .65f to .92f)),
        '2' to listOf(listOf(.08f to .22f, .28f to .04f, .58f to .08f, .72f to .30f, .10f to .92f, .72f to .92f)),
        '3' to listOf(listOf(.08f to .10f, .45f to .03f, .68f to .25f, .38f to .48f, .70f to .68f, .52f to .94f, .08f to .88f)),
        '4' to listOf(listOf(.58f to .96f, .58f to .04f, .05f to .65f, .75f to .65f)),
        '5' to listOf(listOf(.70f to .08f, .12f to .08f, .10f to .46f, .52f to .42f, .72f to .62f, .60f to .90f, .08f to .88f)),
        '6' to listOf(listOf(.65f to .10f, .38f to .04f, .12f to .30f, .10f to .72f, .38f to .94f, .68f to .75f, .62f to .48f, .36f to .42f, .12f to .58f)),
        '7' to listOf(listOf(.08f to .08f, .70f to .08f, .38f to .92f)),
        '8' to listOf(listOf(.38f to .04f, .12f to .22f, .38f to .50f, .08f to .74f, .38f to .94f, .68f to .74f, .38f to .50f, .66f to .22f, .38f to .04f)),
        '0' to listOf(listOf(.38f to .04f, .12f to .20f, .08f to .70f, .36f to .94f, .68f to .76f, .72f to .24f, .38f to .04f)),
        '9' to listOf(listOf(.62f to .48f, .38f to .62f, .10f to .42f, .14f to .10f, .46f to .04f, .68f to .26f, .62f to .72f, .38f to .96f, .12f to .88f)),
        's' to listOf(listOf(.70f to .14f, .38f to .04f, .10f to .25f, .56f to .50f, .72f to .72f, .48f to .94f, .08f to .84f)),
        'i' to listOf(listOf(.35f to .30f, .35f to .92f), listOf(.35f to .05f, .36f to .07f)),
        'n' to listOf(listOf(.10f to .92f, .10f to .32f, .36f to .14f, .66f to .34f, .66f to .92f)),
        'c' to listOf(listOf(.70f to .20f, .44f to .06f, .12f to .28f, .10f to .70f, .40f to .94f, .70f to .80f)),
        'o' to listOf(listOf(.40f to .06f, .12f to .24f, .08f to .68f, .38f to .94f, .70f to .74f, .72f to .30f, .40f to .06f)),
        't' to listOf(listOf(.38f to .08f, .38f to .90f), listOf(.08f to .35f, .70f to .35f)),
        'a' to listOf(listOf(.65f to .92f, .65f to .28f, .38f to .08f, .10f to .30f, .12f to .72f, .40f to .92f, .65f to .65f)),
        'b' to listOf(listOf(.12f to .04f, .12f to .92f, .12f to .42f, .42f to .18f, .70f to .40f, .66f to .76f, .40f to .94f, .12f to .76f)),
        'e' to listOf(listOf(.10f to .52f, .68f to .52f, .58f to .22f, .34f to .08f, .10f to .30f, .12f to .74f, .40f to .94f, .68f to .78f)),
        'h' to listOf(listOf(.12f to .04f, .12f to .92f), listOf(.12f to .48f, .38f to .18f, .66f to .38f, .66f to .92f)),
        'k' to listOf(listOf(.12f to .04f, .12f to .94f), listOf(.68f to .20f, .12f to .58f, .68f to .94f)),
        'l' to listOf(listOf(.35f to .04f, .35f to .88f, .62f to .92f)),
        'g' to listOf(listOf(.64f to .22f, .40f to .06f, .10f to .30f, .12f to .70f, .42f to .88f, .66f to .66f, .64f to .20f, .65f to 1.15f, .36f to 1.28f, .12f to 1.12f)),
        'r' to listOf(listOf(.12f to .92f, .12f to .30f, .38f to .10f, .66f to .24f)),
        'f' to listOf(listOf(.54f to .06f, .30f to .04f, .24f to .92f), listOf(.06f to .36f, .62f to .36f)),
        'p' to listOf(listOf(.12f to 1.20f, .12f to .24f, .42f to .08f, .68f to .30f, .62f to .68f, .34f to .82f, .12f to .66f)),
        'w' to listOf(listOf(.04f to .18f, .18f to .90f, .38f to .48f, .56f to .90f, .76f to .18f)),
        'm' to listOf(listOf(.08f to .92f, .08f to .30f, .27f to .12f, .42f to .34f, .55f to .12f, .72f to .32f, .72f to .92f)),
        'u' to listOf(listOf(.10f to .28f, .10f to .72f, .34f to .92f, .64f to .72f, .64f to .28f, .64f to .92f)),
        'd' to listOf(listOf(.64f to .04f, .64f to .92f, .64f to .38f, .38f to .12f, .10f to .36f, .12f to .74f, .38f to .92f, .64f to .72f)),
        'j' to listOf(listOf(.52f to .28f, .52f to .92f, .32f to 1.10f, .10f to 1.00f), listOf(.52f to .04f, .53f to .06f)),
        'z' to listOf(listOf(.08f to .18f, .68f to .18f, .10f to .88f, .70f to .88f)),
        'C' to listOf(listOf(.70f to .18f, .44f to .04f, .12f to .26f, .10f to .72f, .42f to .96f, .70f to .82f)),
        '∑' to listOf(listOf(.72f to .06f, .12f to .06f, .48f to .50f, .12f to .94f, .72f to .94f)),
        '∏' to listOf(listOf(.08f to .94f, .08f to .08f, .70f to .08f, .70f to .94f)),
    )

    fun write(text: String, seed: Int): List<StrokeElement> {
        val output = mutableListOf<StrokeElement>()
        var cursor = 18f
        var lineOffset = 0f
        var time = 1_000L + seed * 10_000L
        var superscript = false
        var subscript = false
        var superscriptGroup = false
        var subscriptGroup = false
        text.forEachIndexed { charIndex, char ->
            if (char == ' ') {
                cursor += 18f
                return@forEachIndexed
            }
            if (char == '\n') {
                cursor = 18f
                lineOffset += 86f
                return@forEachIndexed
            }
            if (char == '^') {
                superscript = true
                superscriptGroup = text.getOrNull(charIndex + 1) == '{'
                return@forEachIndexed
            }
            if (char == '_') {
                subscript = true
                subscriptGroup = text.getOrNull(charIndex + 1) == '{'
                return@forEachIndexed
            }
            if (char == '{' && (superscriptGroup || subscriptGroup)) return@forEachIndexed
            if (char == '}' && superscriptGroup) {
                superscript = false
                superscriptGroup = false
                return@forEachIndexed
            }
            if (char == '}' && subscriptGroup) {
                subscript = false
                subscriptGroup = false
                return@forEachIndexed
            }
            val glyph = glyphs[char] ?: return@forEachIndexed
            val scale = if (superscript || subscript || char == '³') .58f else 1f
            val yOffset = when {
                superscript || char == '³' -> 0f
                subscript -> 42f
                else -> 24f
            }
            val glyphWidth = 30f * scale
            glyph.forEachIndexed { strokeIndex, line ->
                val points = interpolate(line).mapIndexed { pointIndex, point ->
                    val wobbleX = wobble(seed, charIndex, strokeIndex, pointIndex) * 1.25f
                    val wobbleY = wobble(seed + 31, charIndex, strokeIndex, pointIndex) * 1.5f
                    StrokePoint(
                        x = cursor + point.first * glyphWidth + wobbleX,
                        y = 18f + lineOffset + yOffset + point.second * 48f * scale + wobbleY,
                        pressure = .62f + .12f * wobble(seed + 7, charIndex, strokeIndex, pointIndex),
                        timestampMillis = time.also { time += 14L },
                    )
                }
                output += StrokeElement(
                    id = "human-$seed-$charIndex-$strokeIndex",
                    points = points,
                    tool = StrokeTool.PEN,
                    width = 3.1f,
                    opacity = 1f,
                    argbColor = 0xFFF4F7FF,
                    bounds = SmartBoardBounds.from(points.map(StrokePoint::position)),
                    createdAt = time,
                )
                time += 55L
            }
            cursor += glyphWidth + 7f
            if (!superscriptGroup) superscript = false
            if (!subscriptGroup) subscript = false
        }
        return output
    }

    private fun interpolate(line: List<Point>): List<Point> = buildList {
        line.zipWithNext().forEachIndexed { index, (start, end) ->
            val steps = 5
            repeat(steps) { step ->
                if (index > 0 && step == 0) return@repeat
                val t = step / (steps - 1f)
                add(start.first + (end.first - start.first) * t to start.second + (end.second - start.second) * t)
            }
        }
    }

    private fun wobble(seed: Int, a: Int, b: Int, c: Int): Float {
        val value = (seed * 73 + a * 37 + b * 17 + c * 11) % 19
        return (value - 9) / 9f
    }
}
