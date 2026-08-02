package com.indianservers.smartboard.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

data class StatisticalDiagnostic(val name: String, val passed: Boolean, val detail: String)
data class AdvancedTestResult(
    val method: String, val statistic: Double, val degreesOfFreedom: Double, val pValue: Double, val effectSize: Double?,
    val assumptions: List<String>, val diagnostics: List<StatisticalDiagnostic>,
)
data class RegressionInterval(val x: Double, val estimate: Double, val confidenceLower: Double, val confidenceUpper: Double, val predictionLower: Double, val predictionUpper: Double)
data class RegressionModel(
    val method: String, val coefficients: List<Double>, val fitted: List<Double>, val residuals: List<Double>,
    val rSquared: Double?, val intervals: List<RegressionInterval>, val assumptions: List<String>, val diagnostics: List<StatisticalDiagnostic>,
)
data class BootstrapResult(val observed: Double, val lower: Double, val upper: Double, val samples: List<Double>, val confidence: Double, val seed: Long)

object Phase4Statistics {
    fun oneWayAnova(groups: List<List<Double>>, permutations: Int = 2_000, seed: Long = 1L): AdvancedTestResult {
        require(groups.size >= 2 && groups.all { it.size >= 2 && it.all(Double::isFinite) })
        fun fStatistic(data: List<List<Double>>): Double {
            val all = data.flatten(); val grand = all.average()
            val between = data.sumOf { it.size * (it.average() - grand).pow(2) }
            val within = data.sumOf { group -> val mean = group.average(); group.sumOf { (it - mean).pow(2) } }
            return (between / (data.size - 1)) / (within / (all.size - data.size))
        }
        val observed = fStatistic(groups); val all = groups.flatten(); val sizes = groups.map { it.size }; val random = Random(seed)
        var extreme = 0
        repeat(permutations) {
            val shuffled = shuffled(all, random); var offset = 0
            val permuted = sizes.map { size -> shuffled.subList(offset, offset + size).also { offset += size } }
            if (fStatistic(permuted) >= observed - 1e-12) extreme++
        }
        val grand = all.average(); val between = groups.sumOf { it.size * (it.average() - grand).pow(2) }
        val total = all.sumOf { (it - grand).pow(2) }
        val variances = groups.map(::variance); val positive = variances.filter { it > 0 }
        val ratio = (variances.maxOrNull() ?: 0.0) / (positive.minOrNull() ?: 1.0)
        return AdvancedTestResult(
            "One-way ANOVA", observed, groups.size - 1.0, (extreme + 1.0) / (permutations + 1), between / total,
            listOf("independent observations", "approximately normal residuals", "similar group variances"),
            listOf(
                StatisticalDiagnostic("Group size", groups.all { it.size >= 2 }, sizes.joinToString(prefix = "n=")),
                StatisticalDiagnostic("Variance ratio", ratio < 4, "largest/smallest=" + fmt(ratio)),
                StatisticalDiagnostic("Reproducible p-value", true, "seed=$seed, permutations=$permutations"),
            ),
        )
    }

    fun chiSquare(observed: List<List<Int>>): AdvancedTestResult {
        require(observed.size >= 2 && observed.all { it.size == observed.first().size && it.size >= 2 })
        val rowTotals = observed.map { it.sum().toDouble() }; val columnTotals = observed.first().indices.map { c -> observed.sumOf { it[c] }.toDouble() }
        val total = rowTotals.sum(); require(total > 0)
        var statistic = 0.0; var minimumExpected = Double.POSITIVE_INFINITY
        observed.indices.forEach { r -> observed[r].indices.forEach { c ->
            val expected = rowTotals[r] * columnTotals[c] / total; minimumExpected = minOf(minimumExpected, expected)
            if (expected > 0) statistic += (observed[r][c] - expected).pow(2) / expected
        } }
        val df = (observed.size - 1.0) * (observed.first().size - 1.0)
        val z = ((statistic / df).pow(1.0 / 3.0) - (1 - 2 / (9 * df))) / sqrt(2 / (9 * df))
        val p = (1 - normalCdf(z)).coerceIn(0.0, 1.0)
        val cramer = sqrt(statistic / (total * minOf(observed.size - 1, observed.first().size - 1)))
        return AdvancedTestResult("Pearson chi-square", statistic, df, p, cramer,
            listOf("independent counts", "exclusive categories", "expected counts preferably ≥5"),
            listOf(StatisticalDiagnostic("Expected counts", minimumExpected >= 5, "minimum=" + fmt(minimumExpected))),
        )
    }

    fun mannWhitney(first: List<Double>, second: List<Double>): AdvancedTestResult {
        require(first.isNotEmpty() && second.isNotEmpty())
        val combined = (first.map { 0 to it } + second.map { 1 to it }).sortedBy { it.second }
        val ranks = DoubleArray(combined.size); var start = 0
        while (start < combined.size) {
            var end = start + 1; while (end < combined.size && combined[end].second == combined[start].second) end++
            val rank = (start + 1 + end) / 2.0; for (index in start until end) ranks[index] = rank; start = end
        }
        val rankSum = combined.indices.filter { combined[it].first == 0 }.sumOf { ranks[it] }
        val u = rankSum - first.size * (first.size + 1) / 2.0; val mean = first.size * second.size / 2.0
        val z = (u - mean) / sqrt(first.size * second.size * (first.size + second.size + 1) / 12.0)
        return AdvancedTestResult("Mann–Whitney U", u, Double.NaN, (2 * (1 - normalCdf(abs(z)))).coerceIn(0.0, 1.0), z / sqrt((first.size + second.size).toDouble()),
            listOf("independent observations", "ordinal or continuous outcome", "similar shapes for location interpretation"),
            listOf(StatisticalDiagnostic("Samples", true, "n1=" + first.size + ", n2=" + second.size)),
        )
    }

    fun linearRegression(x: List<Double>, y: List<Double>): RegressionModel {
        require(x.size == y.size && x.size >= 3)
        val mx = x.average(); val my = y.average(); val sxx = x.sumOf { (it - mx).pow(2) }; require(sxx > 0)
        val slope = x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sxx; val intercept = my - slope * mx
        val fitted = x.map { intercept + slope * it }; val residuals = y.indices.map { y[it] - fitted[it] }
        val sse = residuals.sumOf { it * it }; val mse = sse / (x.size - 2); val sst = y.sumOf { (it - my).pow(2) }; val critical = 1.95996398454
        val intervals = x.mapIndexed { index, value ->
            val meanSe = sqrt(mse * (1.0 / x.size + (value - mx).pow(2) / sxx)); val predictionSe = sqrt(meanSe * meanSe + mse); val estimate = fitted[index]
            RegressionInterval(value, estimate, estimate - critical * meanSe, estimate + critical * meanSe, estimate - critical * predictionSe, estimate + critical * predictionSe)
        }
        val leverage = x.maxOf { 1.0 / x.size + (it - mx).pow(2) / sxx }
        return RegressionModel("Linear least squares", listOf(intercept, slope), fitted, residuals, 1 - sse / sst, intervals,
            listOf("linearity", "independent errors", "constant variance", "approximately normal residuals"),
            listOf(StatisticalDiagnostic("Residual mean", abs(residuals.average()) < 1e-9, fmt(residuals.average())), StatisticalDiagnostic("Leverage", leverage < .5, "max=" + fmt(leverage))),
        )
    }

    fun multipleRegression(features: List<List<Double>>, y: List<Double>): RegressionModel {
        require(features.size == y.size && features.isNotEmpty())
        val design = features.map { listOf(1.0) + it }; val p = design.first().size; require(design.all { it.size == p } && design.size > p)
        val matrix = Array(p) { r -> DoubleArray(p) { c -> design.sumOf { it[r] * it[c] } } }
        val vector = DoubleArray(p) { c -> design.indices.sumOf { design[it][c] * y[it] } }
        val coefficients = solveLinear(matrix, vector).toList(); val fitted = design.map { row -> row.indices.sumOf { row[it] * coefficients[it] } }
        val residuals = y.indices.map { y[it] - fitted[it] }; val mean = y.average(); val sse = residuals.sumOf { it * it }; val sst = y.sumOf { (it - mean).pow(2) }
        return RegressionModel("Multiple linear regression", coefficients, fitted, residuals, 1 - sse / sst, emptyList(),
            listOf("linear predictor", "independent errors", "no exact multicollinearity", "constant variance"),
            listOf(StatisticalDiagnostic("Design rank", true, "solved $p coefficients"), StatisticalDiagnostic("Residual mean", abs(residuals.average()) < 1e-8, fmt(residuals.average()))),
        )
    }

    fun logisticRegression(x: List<Double>, outcomes: List<Int>, iterations: Int = 1_000): RegressionModel {
        require(x.size == outcomes.size && x.size >= 4 && outcomes.all { it == 0 || it == 1 } && outcomes.distinct().size == 2)
        var intercept = 0.0; var slope = 0.0
        repeat(iterations) {
            var gi = 0.0; var gs = 0.0
            x.indices.forEach { i -> val error = outcomes[i] - sigmoid(intercept + slope * x[i]); gi += error; gs += error * x[i] }
            intercept += .05 * gi / x.size; slope += .05 * gs / x.size
        }
        val fitted = x.map { sigmoid(intercept + slope * it) }; val residuals = outcomes.indices.map { outcomes[it] - fitted[it] }
        val accuracy = outcomes.indices.count { (fitted[it] >= .5) == (outcomes[it] == 1) }.toDouble() / outcomes.size
        return RegressionModel("Binary logistic regression", listOf(intercept, slope), fitted, residuals, null, emptyList(),
            listOf("binary independent outcomes", "linear log-odds", "no complete separation"),
            listOf(StatisticalDiagnostic("Both classes", true, outcomes.groupingBy { it }.eachCount().toString()), StatisticalDiagnostic("Classification", accuracy >= .5, "accuracy=" + fmt(accuracy))),
        )
    }

    fun bootstrapMean(values: List<Double>, repetitions: Int = 2_000, confidence: Double = .95, seed: Long = 1L): BootstrapResult {
        require(values.size >= 2 && repetitions >= 100 && confidence in .5..<1.0)
        val random = Random(seed); val samples = List(repetitions) { List(values.size) { values[random.nextInt(values.size)] }.average() }.sorted()
        val alpha = (1 - confidence) / 2
        return BootstrapResult(values.average(), samples[(alpha * (samples.size - 1)).toInt()], samples[((1 - alpha) * (samples.size - 1)).toInt()], samples, confidence, seed)
    }

    private fun solveLinear(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val n = vector.size; val a = Array(n) { matrix[it].copyOf() + vector[it] }
        for (column in 0 until n) {
            val pivot = (column until n).maxBy { abs(a[it][column]) }; val swap = a[column]; a[column] = a[pivot]; a[pivot] = swap
            require(abs(a[column][column]) > 1e-12) { "Singular design matrix" }
            val d = a[column][column]; for (j in column..n) a[column][j] /= d
            for (row in 0 until n) if (row != column) { val factor = a[row][column]; for (j in column..n) a[row][j] -= factor * a[column][j] }
        }
        return DoubleArray(n) { a[it][n] }
    }
    private fun variance(values: List<Double>): Double { val mean = values.average(); return values.sumOf { (it - mean).pow(2) } / (values.size - 1) }
    private fun sigmoid(value: Double) = if (value >= 0) 1 / (1 + exp(-value)) else { val e = exp(value); e / (1 + e) }
    private fun normalCdf(value: Double): Double {
        val x = abs(value); val t = 1 / (1 + .2316419 * x); val density = exp(-x * x / 2) / sqrt(2 * PI)
        val tail = density * t * (.319381530 + t * (-.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (value >= 0) 1 - tail else tail
    }
    private fun shuffled(values: List<Double>, random: Random): List<Double> = values.toMutableList().also { list ->
        for (i in list.lastIndex downTo 1) { val j = random.nextInt(i + 1); val value = list[i]; list[i] = list[j]; list[j] = value }
    }
    private fun fmt(value: Double) = if (!value.isFinite()) value.toString() else String.format(java.util.Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
}
