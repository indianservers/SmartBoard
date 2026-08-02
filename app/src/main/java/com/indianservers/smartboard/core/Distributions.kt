package com.indianservers.smartboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class DistributionKind { Normal, Binomial, Poisson, Uniform, Exponential }
enum class DistributionDomain { Continuous, Discrete }

data class DistributionPoint(val x: Double, val probability: Double)
data class DistributionSummary(
    val kind: DistributionKind,
    val domain: DistributionDomain,
    val mean: Double,
    val variance: Double,
    val standardDeviation: Double,
    val parameters: Map<String, Double>,
)

interface ProbabilityDistribution {
    val summary: DistributionSummary
    fun density(x: Double): Double
    fun cumulative(x: Double): Double
    fun quantile(probability: Double): Double
    fun plotPoints(count: Int = 161): List<DistributionPoint>

    fun probabilityBetween(lower: Double, upper: Double): Double {
        require(lower <= upper)
        val left = if (summary.domain == DistributionDomain.Discrete) cumulative(lower - 1.0) else cumulative(lower)
        return (cumulative(upper) - left).coerceIn(0.0, 1.0)
    }
}

data class NormalDistribution(val mean: Double = 0.0, val standardDeviation: Double = 1.0) : ProbabilityDistribution {
    init { require(mean.isFinite() && standardDeviation.isFinite() && standardDeviation > 0.0) }
    override val summary = DistributionSummary(DistributionKind.Normal, DistributionDomain.Continuous, mean, standardDeviation * standardDeviation, standardDeviation, mapOf("μ" to mean, "σ" to standardDeviation))
    override fun density(x: Double): Double {
        val z = (x - mean) / standardDeviation
        return exp(-z * z / 2.0) / (standardDeviation * sqrt(2.0 * PI))
    }
    override fun cumulative(x: Double) = (0.5 * (1.0 + erf((x - mean) / (standardDeviation * sqrt(2.0))))).coerceIn(0.0, 1.0)
    override fun quantile(probability: Double): Double = continuousQuantile(probability, mean - 10 * standardDeviation, mean + 10 * standardDeviation, ::cumulative)
    override fun plotPoints(count: Int): List<DistributionPoint> = continuousPlot(mean - 4 * standardDeviation, mean + 4 * standardDeviation, count, ::density)
}

data class BinomialDistribution(val trials: Int, val probability: Double) : ProbabilityDistribution {
    init { require(trials in 1..100_000 && probability in 0.0..1.0) }
    private val mean = trials * probability
    private val variance = trials * probability * (1.0 - probability)
    override val summary = DistributionSummary(DistributionKind.Binomial, DistributionDomain.Discrete, mean, variance, sqrt(variance), mapOf("n" to trials.toDouble(), "p" to probability))
    override fun density(x: Double): Double {
        val k = x.roundToInt()
        if (abs(x - k) > 1e-9 || k !in 0..trials) return 0.0
        if (probability == 0.0) return if (k == 0) 1.0 else 0.0
        if (probability == 1.0) return if (k == trials) 1.0 else 0.0
        val logCombination = logFactorial(trials) - logFactorial(k) - logFactorial(trials - k)
        return exp(logCombination + k * ln(probability) + (trials - k) * ln(1.0 - probability))
    }
    override fun cumulative(x: Double): Double {
        val last = floor(x).toInt()
        if (last < 0) return 0.0
        if (last >= trials) return 1.0
        return (0..last).sumOf { density(it.toDouble()) }.coerceIn(0.0, 1.0)
    }
    override fun quantile(probability: Double): Double = discreteQuantile(probability, 0, trials, ::cumulative).toDouble()
    override fun plotPoints(count: Int): List<DistributionPoint> = (0..trials).map { DistributionPoint(it.toDouble(), density(it.toDouble())) }
}

data class PoissonDistribution(val rate: Double) : ProbabilityDistribution {
    init { require(rate.isFinite() && rate > 0.0 && rate <= 700.0) }
    override val summary = DistributionSummary(DistributionKind.Poisson, DistributionDomain.Discrete, rate, rate, sqrt(rate), mapOf("λ" to rate))
    override fun density(x: Double): Double {
        val k = x.roundToInt()
        if (abs(x - k) > 1e-9 || k < 0) return 0.0
        return exp(-rate + k * ln(rate) - logFactorial(k))
    }
    override fun cumulative(x: Double): Double {
        val last = floor(x).toInt()
        if (last < 0) return 0.0
        var term = exp(-rate)
        var sum = term
        for (k in 1..last) { term *= rate / k; sum += term }
        return sum.coerceIn(0.0, 1.0)
    }
    override fun quantile(probability: Double): Double {
        require(probability in 0.0..1.0)
        if (probability == 1.0) return Double.POSITIVE_INFINITY
        val high = (rate + 12 * sqrt(rate) + 20).toInt().coerceAtLeast(20)
        return discreteQuantile(probability, 0, high, ::cumulative).toDouble()
    }
    override fun plotPoints(count: Int): List<DistributionPoint> {
        val high = (rate + 4 * sqrt(rate) + 5).toInt().coerceAtLeast(8)
        return (0..high).map { DistributionPoint(it.toDouble(), density(it.toDouble())) }
    }
}

data class UniformDistribution(val minimum: Double, val maximum: Double) : ProbabilityDistribution {
    init { require(minimum.isFinite() && maximum.isFinite() && minimum < maximum) }
    private val width = maximum - minimum
    private val mean = (minimum + maximum) / 2.0
    private val variance = width * width / 12.0
    override val summary = DistributionSummary(DistributionKind.Uniform, DistributionDomain.Continuous, mean, variance, sqrt(variance), mapOf("a" to minimum, "b" to maximum))
    override fun density(x: Double) = if (x in minimum..maximum) 1.0 / width else 0.0
    override fun cumulative(x: Double) = when { x <= minimum -> 0.0; x >= maximum -> 1.0; else -> (x - minimum) / width }
    override fun quantile(probability: Double): Double { require(probability in 0.0..1.0); return minimum + probability * width }
    override fun plotPoints(count: Int) = continuousPlot(minimum - width * .1, maximum + width * .1, count, ::density)
}

data class ExponentialDistribution(val rate: Double) : ProbabilityDistribution {
    init { require(rate.isFinite() && rate > 0.0) }
    private val mean = 1.0 / rate
    private val variance = 1.0 / (rate * rate)
    override val summary = DistributionSummary(DistributionKind.Exponential, DistributionDomain.Continuous, mean, variance, mean, mapOf("λ" to rate))
    override fun density(x: Double) = if (x < 0.0) 0.0 else rate * exp(-rate * x)
    override fun cumulative(x: Double) = if (x <= 0.0) 0.0 else 1.0 - exp(-rate * x)
    override fun quantile(probability: Double): Double { require(probability in 0.0..1.0); return if (probability == 1.0) Double.POSITIVE_INFINITY else -ln(1.0 - probability) / rate }
    override fun plotPoints(count: Int) = continuousPlot(0.0, 6.0 / rate, count, ::density)
}

object DistributionEngine {
    fun create(kind: DistributionKind, first: Double, second: Double = 1.0): ProbabilityDistribution = when (kind) {
        DistributionKind.Normal -> NormalDistribution(first, second)
        DistributionKind.Binomial -> BinomialDistribution(first.roundToInt(), second)
        DistributionKind.Poisson -> PoissonDistribution(first)
        DistributionKind.Uniform -> UniformDistribution(first, second)
        DistributionKind.Exponential -> ExponentialDistribution(first)
    }
}

private fun continuousPlot(minimum: Double, maximum: Double, count: Int, density: (Double) -> Double): List<DistributionPoint> {
    require(count in 2..10_000)
    return (0 until count).map { index ->
        val x = minimum + (maximum - minimum) * index / (count - 1)
        DistributionPoint(x, density(x))
    }
}

private fun continuousQuantile(probability: Double, minimum: Double, maximum: Double, cdf: (Double) -> Double): Double {
    require(probability in 0.0..1.0)
    if (probability == 0.0) return Double.NEGATIVE_INFINITY
    if (probability == 1.0) return Double.POSITIVE_INFINITY
    var low = minimum
    var high = maximum
    repeat(80) { val middle = (low + high) / 2.0; if (cdf(middle) < probability) low = middle else high = middle }
    return (low + high) / 2.0
}

private fun discreteQuantile(probability: Double, minimum: Int, maximum: Int, cdf: (Double) -> Double): Int {
    require(probability in 0.0..1.0)
    var low = minimum
    var high = maximum
    while (low < high) { val middle = (low + high) / 2; if (cdf(middle.toDouble()) >= probability) high = middle else low = middle + 1 }
    return low
}

private fun logFactorial(n: Int): Double {
    require(n >= 0)
    if (n < 2) return 0.0
    if (n < 256) return (2..n).sumOf { ln(it.toDouble()) }
    val x = n.toDouble()
    return (x + .5) * ln(x) - x + .5 * ln(2 * PI) + 1.0 / (12 * x)
}

// Abramowitz-Stegun 7.1.26; maximum error is approximately 1.5e-7.
private fun erf(value: Double): Double {
    val sign = if (value < 0) -1 else 1
    val x = abs(value)
    val t = 1.0 / (1.0 + .3275911 * x)
    val polynomial = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - .284496736) * t + .254829592) * t
    return sign * (1.0 - polynomial * exp(-x * x))
}
