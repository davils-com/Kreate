/*
 * Copyright 2026 Davils
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.davils.kreate.module.project.benchmark

import kotlin.math.abs

/**
 * Which way a score has to move for a benchmark to have got better.
 *
 * Getting this backwards would make the gate worse than no gate at all: it would pass every
 * regression and fail every improvement.
 *
 * @since 2.2.0
 */
internal enum class BenchmarkDirection {
    /**
     * Throughput modes, where a larger score is a faster program.
     * @since 2.2.0
     */
    HIGHER_IS_BETTER,

    /**
     * Time-per-operation modes, where a smaller score is a faster program.
     * @since 2.2.0
     */
    LOWER_IS_BETTER;

    internal companion object {
        /**
         * Determines the direction of a measurement mode.
         *
         * @param mode The mode as written in the report.
         * @return The direction, defaulting to [HIGHER_IS_BETTER] for the throughput modes
         *   and [LOWER_IS_BETTER] for everything else, which are all time based.
         * @since 2.2.0
         */
        fun forMode(mode: String): BenchmarkDirection = when (mode.lowercase()) {
            "thrpt", "throughput" -> HIGHER_IS_BETTER
            else -> LOWER_IS_BETTER
        }
    }
}

/**
 * How one benchmark fared against its baseline.
 *
 * @since 2.2.0
 */
internal enum class BenchmarkVerdict {
    /**
     * Within the threshold, or not moved far enough to be distinguishable from noise.
     * @since 2.2.0
     */
    UNCHANGED,

    /**
     * Moved in the better direction beyond the threshold.
     * @since 2.2.0
     */
    IMPROVED,

    /**
     * Moved in the worse direction beyond the threshold. Fails the build.
     * @since 2.2.0
     */
    REGRESSED,

    /**
     * In the baseline but not in the run. Fails the build unless allowed.
     * @since 2.2.0
     */
    MISSING,

    /**
     * In the run but not in the baseline. Reported, never a failure.
     * @since 2.2.0
     */
    ADDED,

    /**
     * Recorded in a different unit or a different mode than the baseline. Fails the build,
     * because the two numbers do not describe the same quantity.
     * @since 2.2.0
     */
    INCOMPARABLE
}

/**
 * The outcome for a single benchmark.
 *
 * @since 2.2.0
 */
internal data class BenchmarkDelta(
    /**
     * The benchmark identity, including its parameter values.
     * @since 2.2.0
     */
    val key: String,
    /**
     * The recorded baseline measurement, or `null` when the benchmark is new.
     * @since 2.2.0
     */
    val baseline: BenchmarkResult?,
    /**
     * The measurement from this run, or `null` when the benchmark disappeared.
     * @since 2.2.0
     */
    val current: BenchmarkResult?,
    /**
     * How far the score moved in the worse direction, in per cent of the baseline. Negative
     * for an improvement, [Double.NaN] when there is nothing to compare.
     * @since 2.2.0
     */
    val regressionPercent: Double,
    /**
     * The threshold that applied to this benchmark, in per cent.
     * @since 2.2.0
     */
    val thresholdPercent: Double,
    /**
     * Whether the movement exceeded the combined measurement error.
     * @since 2.2.0
     */
    val significant: Boolean,
    /**
     * What the gate concluded.
     * @since 2.2.0
     */
    val verdict: BenchmarkVerdict,
    /**
     * Why the two measurements could not be compared, when [verdict] is
     * [BenchmarkVerdict.INCOMPARABLE].
     * @since 2.2.0
     */
    val incomparableReason: String? = null
)

/**
 * The settings the gate applies.
 *
 * @since 2.2.0
 */
internal data class RegressionSettings(
    /**
     * The default threshold in per cent.
     * @since 2.2.0
     */
    val maxRegressionPercent: Double,
    /**
     * Per-benchmark thresholds, keyed by fully qualified benchmark name.
     * @since 2.2.0
     */
    val thresholdOverrides: Map<String, Double> = emptyMap(),
    /**
     * Whether a benchmark missing from the run fails the build.
     * @since 2.2.0
     */
    val failOnMissingBenchmark: Boolean = true,
    /**
     * Whether a regression must exceed the combined measurement error to count.
     * @since 2.2.0
     */
    val requireSignificance: Boolean = true
)

/**
 * Compares a benchmark run against a recorded baseline.
 *
 * @since 2.2.0
 */
internal object BenchmarkComparator {
    private const val PERCENT = 100.0

    /**
     * Compares two reports.
     *
     * @param baseline The measurements recorded in the committed baseline.
     * @param current The measurements from this run.
     * @param settings The thresholds and rules to apply.
     * @return One entry per benchmark seen in either report, ordered by benchmark identity.
     * @since 2.2.0
     */
    fun compare(
        baseline: List<BenchmarkResult>,
        current: List<BenchmarkResult>,
        settings: RegressionSettings
    ): List<BenchmarkDelta> {
        val baselineByKey = baseline.associateBy { it.key }
        val currentByKey = current.associateBy { it.key }

        return (baselineByKey.keys + currentByKey.keys).sorted().map { key ->
            compareOne(key, baselineByKey[key], currentByKey[key], settings)
        }
    }

    /**
     * Reports whether a set of outcomes should fail the build.
     *
     * @param deltas The comparison outcomes.
     * @param settings The rules that were applied.
     * @return The outcomes that constitute a failure, empty when the run passed.
     * @since 2.2.0
     */
    fun failures(deltas: List<BenchmarkDelta>, settings: RegressionSettings): List<BenchmarkDelta> =
        deltas.filter { delta ->
            when (delta.verdict) {
                BenchmarkVerdict.REGRESSED, BenchmarkVerdict.INCOMPARABLE -> true
                BenchmarkVerdict.MISSING -> settings.failOnMissingBenchmark
                else -> false
            }
        }

    private fun compareOne(
        key: String,
        baseline: BenchmarkResult?,
        current: BenchmarkResult?,
        settings: RegressionSettings
    ): BenchmarkDelta {
        val threshold = thresholdFor(baseline ?: current, settings)
        uncomparable(key, baseline, current, threshold)?.let { return it }

        checkNotNull(baseline)
        checkNotNull(current)

        val worseBy = when (BenchmarkDirection.forMode(current.mode)) {
            BenchmarkDirection.HIGHER_IS_BETTER -> baseline.score - current.score
            BenchmarkDirection.LOWER_IS_BETTER -> current.score - baseline.score
        }
        val regressionPercent = percentOf(worseBy, baseline.score)
        val significant = isSignificant(baseline, current, worseBy, settings)

        val verdict = when {
            regressionPercent > threshold && significant -> BenchmarkVerdict.REGRESSED
            -regressionPercent > threshold -> BenchmarkVerdict.IMPROVED
            else -> BenchmarkVerdict.UNCHANGED
        }

        return BenchmarkDelta(
            key = key,
            baseline = baseline,
            current = current,
            regressionPercent = regressionPercent,
            thresholdPercent = threshold,
            significant = significant,
            verdict = verdict
        )
    }

    /**
     * Builds the outcome for a pair that cannot be scored against each other.
     *
     * A benchmark that exists on only one side, or one whose mode or unit changed, has no
     * meaningful percentage; it is decided here so that the scoring path below can assume
     * two comparable measurements.
     *
     * @param key The benchmark identity.
     * @param baseline The baseline measurement, if any.
     * @param current The current measurement, if any.
     * @param threshold The threshold that applies to the benchmark.
     * @return The outcome, or `null` when the two measurements can be compared.
     * @since 2.2.0
     */
    private fun uncomparable(
        key: String,
        baseline: BenchmarkResult?,
        current: BenchmarkResult?,
        threshold: Double
    ): BenchmarkDelta? {
        val reason = if (baseline != null && current != null) {
            incomparableReason(baseline, current)
        } else {
            null
        }

        val verdict = when {
            baseline == null -> BenchmarkVerdict.ADDED
            current == null -> BenchmarkVerdict.MISSING
            reason != null -> BenchmarkVerdict.INCOMPARABLE
            else -> null
        }

        return verdict?.let {
            BenchmarkDelta(
                key = key,
                baseline = baseline,
                current = current,
                regressionPercent = Double.NaN,
                thresholdPercent = threshold,
                significant = false,
                verdict = it,
                incomparableReason = reason
            )
        }
    }

    /**
     * Expresses a score movement as a percentage of the baseline.
     *
     * @param worseBy How far the score moved in the worse direction, in score units.
     * @param baselineScore The baseline score.
     * @return The movement in per cent, or an infinite value when the baseline was zero and
     *   therefore has no meaningful scale.
     * @since 2.2.0
     */
    private fun percentOf(worseBy: Double, baselineScore: Double): Double {
        val scale = abs(baselineScore)
        if (scale != 0.0) return worseBy / scale * PERCENT

        // A baseline of zero has no scale to express a change against, so the movement is
        // reported as unbounded rather than as a division by zero.
        return when {
            worseBy > 0.0 -> Double.POSITIVE_INFINITY
            worseBy < 0.0 -> Double.NEGATIVE_INFINITY
            else -> 0.0
        }
    }

    /**
     * Reports whether a movement is larger than the measurement error behind it.
     *
     * A run that produced no error margin — a single iteration does not — leaves the
     * threshold to decide on its own rather than silently suppressing every finding.
     *
     * @param baseline The baseline measurement.
     * @param current The current measurement.
     * @param worseBy How far the score moved in the worse direction.
     * @param settings The rules to apply.
     * @return `true` when the movement counts as real.
     * @since 2.2.0
     */
    private fun isSignificant(
        baseline: BenchmarkResult,
        current: BenchmarkResult,
        worseBy: Double,
        settings: RegressionSettings
    ): Boolean {
        if (!settings.requireSignificance) return true

        val errors = listOf(baseline.scoreError, current.scoreError)
            .filter { it.isFinite() && it > 0.0 }

        return errors.isEmpty() || abs(worseBy) > errors.sum()
    }

    /**
     * Reports why two measurements cannot be compared, if they cannot.
     *
     * @param baseline The baseline measurement.
     * @param current The current measurement.
     * @return A description of the mismatch, or `null` when the two are comparable.
     * @since 2.2.0
     */
    private fun incomparableReason(baseline: BenchmarkResult, current: BenchmarkResult): String? = when {
        baseline.mode != current.mode -> {
            "mode changed from '${baseline.mode}' to '${current.mode}'"
        }
        baseline.scoreUnit != current.scoreUnit -> {
            "unit changed from '${baseline.scoreUnit}' to '${current.scoreUnit}'"
        }
        else -> {
            null
        }
    }

    private fun thresholdFor(result: BenchmarkResult?, settings: RegressionSettings): Double =
        result?.benchmark
            ?.let { settings.thresholdOverrides[it] }
            ?: settings.maxRegressionPercent
}
