package com.indianservers.smartboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

data class FiveNumberSummary(val minimum: Double, val firstQuartile: Double, val median: Double, val thirdQuartile: Double, val maximum: Double)
data class HistogramBin(val lower: Double, val upper: Double, val count: Int, val relativeFrequency: Double, val density: Double)
data class FrequencyValue(val value: Double, val count: Int, val relativeFrequency: Double, val cumulativeFrequency: Int)

data class DescriptiveStatistics(
    val count: Int,
    val sum: Double,
    val mean: Double,
    val median: Double,
    val modes: List<Double>,
    val fiveNumber: FiveNumberSummary,
    val range: Double,
    val interquartileRange: Double,
    val populationVariance: Double,
    val sampleVariance: Double,
    val populationStandardDeviation: Double,
    val sampleStandardDeviation: Double,
    val standardError: Double,
    val coefficientOfVariation: Double?,
    val skewness: Double?,
    val excessKurtosis: Double?,
    val meanAbsoluteDeviation: Double,
    val medianAbsoluteDeviation: Double,
    val lowerFence: Double,
    val upperFence: Double,
    val outliers: List<Double>,
)

object AdvancedStatisticsEngine {
    fun summarize(values: List<Double>): DescriptiveStatistics {
        require(values.isNotEmpty() && values.all(Double::isFinite)) { "Statistics require finite data" }
        val sorted = values.sorted()
        val n = sorted.size
        val sum = sorted.sum()
        val mean = sum / n
        val median = quantileSorted(sorted, .5)
        val q1 = quantileSorted(sorted, .25)
        val q3 = quantileSorted(sorted, .75)
        val iqr = q3 - q1
        val lowerFence = q1 - 1.5 * iqr
        val upperFence = q3 + 1.5 * iqr
        val centered = sorted.map { it - mean }
        val squaredSum = centered.sumOf { it * it }
        val populationVariance = squaredSum / n
        val sampleVariance = if (n > 1) squaredSum / (n - 1) else Double.NaN
        val populationSd = sqrt(populationVariance)
        val sampleSd = sqrt(sampleVariance)
        val frequencies = sorted.groupingBy { it }.eachCount()
        val maxFrequency = frequencies.values.maxOrNull() ?: 1
        val modes = if (maxFrequency <= 1) emptyList() else frequencies.filterValues { it == maxFrequency }.keys.sorted()
        val skewness = if (n >= 3 && sampleSd > 0) n.toDouble() / ((n - 1.0) * (n - 2.0)) * centered.sumOf { (it / sampleSd).pow(3) } else null
        val kurtosis = if (n >= 4 && sampleSd > 0) {
            val standardizedFourth = centered.sumOf { (it / sampleSd).pow(4) }
            n.toDouble() * (n + 1) / ((n - 1.0) * (n - 2.0) * (n - 3.0)) * standardizedFourth - 3.0 * (n - 1.0).pow(2) / ((n - 2.0) * (n - 3.0))
        } else null
        val deviationsFromMedian = sorted.map { abs(it - median) }.sorted()
        return DescriptiveStatistics(
            n, sum, mean, median, modes,
            FiveNumberSummary(sorted.first(), q1, median, q3, sorted.last()),
            sorted.last() - sorted.first(), iqr, populationVariance, sampleVariance, populationSd, sampleSd,
            if (n > 1) sampleSd / sqrt(n.toDouble()) else Double.NaN,
            mean.takeIf { abs(it) > 1e-12 }?.let { sampleSd / abs(it) },
            skewness, kurtosis,
            sorted.sumOf { abs(it - mean) } / n,
            quantileSorted(deviationsFromMedian, .5),
            lowerFence, upperFence, sorted.filter { it < lowerFence || it > upperFence },
        )
    }

    /** R-7 / NumPy-linear quantile, including interpolated percentiles. */
    fun quantile(values: List<Double>, probability: Double): Double {
        require(values.isNotEmpty() && probability in 0.0..1.0)
        return quantileSorted(values.sorted(), probability)
    }

    fun frequencyTable(values: List<Double>): List<FrequencyValue> {
        require(values.isNotEmpty())
        var cumulative = 0
        return values.groupingBy { it }.eachCount().toSortedMap().map { (value, count) ->
            cumulative += count
            FrequencyValue(value, count, count.toDouble() / values.size, cumulative)
        }
    }

    fun histogram(values: List<Double>, requestedBins: Int? = null): List<HistogramBin> {
        require(values.isNotEmpty() && values.all(Double::isFinite))
        val sorted = values.sorted()
        if (sorted.first() == sorted.last()) return listOf(HistogramBin(sorted.first() - .5, sorted.last() + .5, sorted.size, 1.0, 1.0))
        val bins = (requestedBins ?: recommendedBinCount(sorted)).coerceIn(1, 100)
        val width = (sorted.last() - sorted.first()) / bins
        val counts = IntArray(bins)
        sorted.forEach { value -> counts[min(((value - sorted.first()) / width).toInt(), bins - 1)]++ }
        return counts.indices.map { index ->
            val lower = sorted.first() + index * width
            val upper = if (index == bins - 1) sorted.last() else lower + width
            HistogramBin(lower, upper, counts[index], counts[index].toDouble() / sorted.size, counts[index].toDouble() / (sorted.size * width))
        }
    }

    fun empiricalCdf(values: List<Double>): List<Vec2> = values.sorted().mapIndexed { index, value -> Vec2(value, (index + 1.0) / values.size) }

    fun normalQq(values: List<Double>): List<Vec2> {
        val sorted = values.sorted()
        val normal = NormalDistribution()
        return sorted.mapIndexed { index, value -> Vec2(normal.quantile((index + .5) / sorted.size), value) }
    }

    private fun recommendedBinCount(sorted: List<Double>): Int {
        val iqr = quantileSorted(sorted, .75) - quantileSorted(sorted, .25)
        val width = 2 * iqr / sorted.size.toDouble().pow(1.0 / 3.0)
        return if (width > 0) ceil((sorted.last() - sorted.first()) / width).toInt() else ceil(ln(sorted.size.toDouble()) / ln(2.0) + 1).toInt()
    }

    private fun quantileSorted(sorted: List<Double>, probability: Double): Double {
        if (sorted.size == 1) return sorted.first()
        val index = probability * (sorted.size - 1)
        val lower = floor(index).toInt()
        val upper = ceil(index).toInt()
        val weight = index - lower
        return sorted[lower] * (1 - weight) + sorted[upper] * weight
    }
}

data class ConfidenceInterval(val estimate: Double, val lower: Double, val upper: Double, val confidence: Double)
data class HypothesisTestResult(val statistic: Double, val degreesOfFreedom: Double, val pValueTwoSided: Double, val rejectAtFivePercent: Boolean, val method: String)
data class CorrelationStatistics(val pearson: Double, val spearman: Double, val covariancePopulation: Double, val covarianceSample: Double)

object InferentialStatistics {
    fun meanConfidenceInterval(values: List<Double>, confidence: Double = .95): ConfidenceInterval {
        require(values.size >= 2 && confidence in .5..<1.0)
        val summary = AdvancedStatisticsEngine.summarize(values)
        val alpha = 1 - confidence
        val critical = studentTQuantile(1 - alpha / 2, values.size - 1.0)
        val margin = critical * summary.standardError
        return ConfidenceInterval(summary.mean, summary.mean - margin, summary.mean + margin, confidence)
    }

    fun oneSampleT(values: List<Double>, hypothesizedMean: Double): HypothesisTestResult {
        val summary = AdvancedStatisticsEngine.summarize(values)
        require(values.size >= 2 && summary.sampleStandardDeviation > 0)
        val statistic = (summary.mean - hypothesizedMean) / summary.standardError
        val df = values.size - 1.0
        val p = 2 * (1 - studentTCdf(abs(statistic), df))
        return HypothesisTestResult(statistic, df, p.coerceIn(0.0, 1.0), p < .05, "One-sample Student t test")
    }

    fun welchT(first: List<Double>, second: List<Double>): HypothesisTestResult {
        val a = AdvancedStatisticsEngine.summarize(first)
        val b = AdvancedStatisticsEngine.summarize(second)
        require(first.size >= 2 && second.size >= 2)
        val va = a.sampleVariance / first.size
        val vb = b.sampleVariance / second.size
        val statistic = (a.mean - b.mean) / sqrt(va + vb)
        val df = (va + vb).pow(2) / (va.pow(2) / (first.size - 1) + vb.pow(2) / (second.size - 1))
        val p = 2 * (1 - studentTCdf(abs(statistic), df))
        return HypothesisTestResult(statistic, df, p.coerceIn(0.0, 1.0), p < .05, "Welch two-sample t test")
    }

    fun correlation(x: List<Double>, y: List<Double>): CorrelationStatistics {
        require(x.size == y.size && x.size >= 2)
        val meanX = x.average(); val meanY = y.average()
        val cross = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) }
        val sx = x.sumOf { (it - meanX).pow(2) }; val sy = y.sumOf { (it - meanY).pow(2) }
        fun ranks(values: List<Double>): List<Double> {
            val result = DoubleArray(values.size)
            values.withIndex().groupBy { it.value }.toSortedMap().values.let { groups ->
                var rank = 1.0
                groups.forEach { group ->
                    val averageRank = rank + (group.size - 1) / 2.0
                    group.forEach { result[it.index] = averageRank }
                    rank += group.size
                }
            }
            return result.toList()
        }
        val rx = ranks(x); val ry = ranks(y); val rmx = rx.average(); val rmy = ry.average()
        val rankCross = rx.indices.sumOf { (rx[it] - rmx) * (ry[it] - rmy) }
        val rankScale = sqrt(rx.sumOf { (it - rmx).pow(2) } * ry.sumOf { (it - rmy).pow(2) })
        return CorrelationStatistics(cross / sqrt(sx * sy), rankCross / rankScale, cross / x.size, cross / (x.size - 1))
    }

    private fun studentTQuantile(probability: Double, df: Double): Double {
        var low = -20.0; var high = 20.0
        repeat(90) { val middle = (low + high) / 2; if (studentTCdf(middle, df) < probability) low = middle else high = middle }
        return (low + high) / 2
    }

    private fun studentTCdf(value: Double, df: Double): Double {
        val x = df / (df + value * value)
        val tail = .5 * regularizedBeta(x, df / 2, .5)
        return if (value >= 0) 1 - tail else tail
    }

    private fun regularizedBeta(x: Double, a: Double, b: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val front = exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * ln(x) + b * ln(1 - x))
        return if (x < (a + 1) / (a + b + 2)) front * betaFraction(x, a, b) / a
        else 1 - front * betaFraction(1 - x, b, a) / b
    }

    private fun betaFraction(x: Double, a: Double, b: Double): Double {
        var c = 1.0; var d = 1.0 - (a + b) * x / (a + 1); if (abs(d) < 1e-30) d = 1e-30; d = 1 / d
        var h = d
        for (m in 1..200) {
            val m2 = 2.0 * m
            var aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2))
            d = 1 + aa * d; if (abs(d) < 1e-30) d = 1e-30; c = 1 + aa / c; if (abs(c) < 1e-30) c = 1e-30; d = 1 / d; h *= d * c
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1))
            d = 1 + aa * d; if (abs(d) < 1e-30) d = 1e-30; c = 1 + aa / c; if (abs(c) < 1e-30) c = 1e-30; d = 1 / d
            val delta = d * c; h *= delta
            if (abs(delta - 1) < 3e-12) break
        }
        return h
    }

    private fun logGamma(value: Double): Double {
        val coefficients = doubleArrayOf(676.5203681218851, -1259.1392167224028, 771.3234287776531, -176.6150291621406, 12.507343278686905, -.13857109526572012, 9.984369578019571e-6, 1.5056327351493116e-7)
        if (value < .5) return ln(PI) - ln(abs(sin(PI * value))) - logGamma(1 - value)
        val z = value - 1
        var x = .9999999999998099
        coefficients.forEachIndexed { index, coefficient -> x += coefficient / (z + index + 1) }
        val t = z + coefficients.size - .5
        return .5 * ln(2 * PI) + (z + .5) * ln(t) - t + ln(x)
    }
}

enum class StatisticsStudyLevel(val label: String) { School("School"), HigherSecondary("Higher Secondary"), Undergraduate("Undergraduate"), Postgraduate("Postgraduate") }
data class StatisticsLesson(val id: String, val title: String, val concepts: List<String>, val lab: String, val outcome: String)

object StatisticsCurriculum {
    val lessons = mapOf(
        StatisticsStudyLevel.School to listOf(
            StatisticsLesson("data-basics", "Data, tables and pictographs", listOf("categorical/numerical data", "frequency tables", "bar and pie charts"), "Build a frequency table from classroom data", "Read and compare everyday datasets"),
            StatisticsLesson("center-school", "Mean, median, mode and range", listOf("mean", "median", "mode", "range"), "Move values and observe all four summaries", "Choose a suitable measure of centre"),
        ),
        StatisticsStudyLevel.HigherSecondary to listOf(
            StatisticsLesson("dispersion", "Dispersion and position", listOf("variance", "standard deviation", "quartiles", "percentiles", "box plots"), "Compare datasets with equal means", "Explain centre, spread and outliers"),
            StatisticsLesson("probability-rv", "Probability and random variables", listOf("conditional probability", "Bayes theorem", "expectation", "binomial", "normal"), "Explore distribution parameters", "Model discrete and continuous uncertainty"),
        ),
        StatisticsStudyLevel.Undergraduate to listOf(
            StatisticsLesson("estimation", "Sampling and estimation", listOf("sampling distributions", "standard error", "confidence intervals", "bias"), "Resample and compare estimators", "Quantify sampling uncertainty"),
            StatisticsLesson("testing", "Hypothesis testing", listOf("p-values", "power", "t tests", "chi-square", "ANOVA"), "Run and interpret a one-sample t test", "Report evidence without binary overclaiming"),
            StatisticsLesson("regression", "Correlation and regression", listOf("Pearson/Spearman", "least squares", "residuals", "diagnostics"), "Fit a line and inspect residuals", "Separate association from causation"),
        ),
        StatisticsStudyLevel.Postgraduate to listOf(
            StatisticsLesson("glm", "Generalized linear models", listOf("link functions", "likelihood", "logistic/Poisson regression", "deviance"), "Compare link functions", "Formulate and diagnose GLMs"),
            StatisticsLesson("multivariate", "Multivariate methods", listOf("matrix covariance", "PCA", "MANOVA", "clustering", "discriminant analysis"), "Explore principal components", "Reason about correlated high-dimensional data"),
            StatisticsLesson("bayesian", "Bayesian inference", listOf("priors/posteriors", "credible intervals", "MCMC", "hierarchical models"), "Update a conjugate prior", "Interpret posterior uncertainty"),
            StatisticsLesson("time-survival", "Time series and survival", listOf("stationarity", "ARIMA", "hazards", "Kaplan-Meier", "Cox models"), "Compare temporal and censored-data models", "Select methods for dependent/censored observations"),
            StatisticsLesson("research", "Research statistics and reproducibility", listOf("effect sizes", "multiple testing", "missing data", "robust/nonparametric methods", "causal inference"), "Audit a complete analysis workflow", "Design reproducible, assumption-aware analyses"),
        ),
    )
}
