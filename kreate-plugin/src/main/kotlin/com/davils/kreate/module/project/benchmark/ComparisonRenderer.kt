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

import java.util.Locale

/**
 * Turns comparison outcomes into something a person reads.
 *
 * The full table goes to a report file so that a passing run still leaves evidence of what
 * was measured; the failure lines go into the build failure, where brevity matters more.
 *
 * @since 2.2.0
 */
internal object ComparisonRenderer {
    private const val MAX_REPORTED_FAILURES = 25

    /**
     * Renders the complete comparison as a Markdown table.
     *
     * @param deltas Every comparison outcome.
     * @param profile The profile the run used.
     * @return The report contents.
     * @since 2.2.0
     */
    fun renderMarkdown(deltas: List<BenchmarkDelta>, profile: String): String = buildString {
        appendLine("# Benchmark comparison — profile `$profile`")
        appendLine()

        if (deltas.isEmpty()) {
            appendLine("No benchmarks were measured.")
        } else {
            appendTable(deltas)
        }
    }

    private fun StringBuilder.appendTable(deltas: List<BenchmarkDelta>) {
        appendLine("| Benchmark | Baseline | Current | Unit | Change | Limit | Verdict |")
        appendLine("| --- | ---: | ---: | --- | ---: | ---: | --- |")
        deltas.forEach { delta ->
            appendLine(
                "| `${delta.key}` " +
                    "| ${score(delta.baseline)} " +
                    "| ${score(delta.current)} " +
                    "| ${unit(delta)} " +
                    "| ${change(delta)} " +
                    "| ${percent(delta.thresholdPercent)} " +
                    "| ${verdict(delta)}${incomparableSuffix(delta)} |"
            )
        }
        appendLine()
        appendLine(
            "Change is stated in the worse direction: a positive value is a regression, " +
                "a negative value an improvement."
        )
    }

    /**
     * Renders the failing outcomes as one line each.
     *
     * @param failures The outcomes that fail the build.
     * @return The lines, capped so that a wholesale change does not bury the message.
     * @since 2.2.0
     */
    fun renderFailures(failures: List<BenchmarkDelta>): String = buildString {
        failures.take(MAX_REPORTED_FAILURES).forEach { delta ->
            appendLine("  ${describeFailure(delta)}")
        }
        if (failures.size > MAX_REPORTED_FAILURES) {
            appendLine("  ... and ${failures.size - MAX_REPORTED_FAILURES} more")
        }
    }.trimEnd()

    private fun describeFailure(delta: BenchmarkDelta): String = when (delta.verdict) {
        BenchmarkVerdict.MISSING -> {
            "${delta.key}: in the baseline but not in this run"
        }
        BenchmarkVerdict.INCOMPARABLE -> {
            "${delta.key}: cannot be compared, ${delta.incomparableReason}"
        }
        else -> {
            "${delta.key}: ${score(delta.baseline)} -> ${score(delta.current)} ${unit(delta)}, " +
                "${percent(delta.regressionPercent)} worse (limit ${percent(delta.thresholdPercent)})"
        }
    }

    private fun verdict(delta: BenchmarkDelta): String = when (delta.verdict) {
        BenchmarkVerdict.UNCHANGED -> "unchanged"
        BenchmarkVerdict.IMPROVED -> "improved"
        BenchmarkVerdict.REGRESSED -> "**regressed**"
        BenchmarkVerdict.MISSING -> "**missing**"
        BenchmarkVerdict.ADDED -> "new"
        BenchmarkVerdict.INCOMPARABLE -> "**incomparable**"
    }

    private fun change(delta: BenchmarkDelta): String = when {
        delta.regressionPercent.isNaN() -> {
            "—"
        }
        delta.verdict == BenchmarkVerdict.REGRESSED || delta.significant -> {
            percent(delta.regressionPercent)
        }
        else -> {
            // A movement inside the measurement error is not a trend worth reading.
            "${percent(delta.regressionPercent)} (noise)"
        }
    }

    private fun incomparableSuffix(delta: BenchmarkDelta): String =
        delta.incomparableReason?.let { " ($it)" }.orEmpty()

    private fun score(result: BenchmarkResult?): String =
        result?.let { format(it.score) } ?: "—"

    private fun unit(delta: BenchmarkDelta): String =
        delta.current?.scoreUnit ?: delta.baseline?.scoreUnit ?: ""

    private fun percent(value: Double): String = when {
        value.isNaN() -> "—"
        value.isInfinite() -> if (value > 0) "+INF%" else "-INF%"
        else -> String.format(Locale.ROOT, "%+.1f%%", value)
    }

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
}
