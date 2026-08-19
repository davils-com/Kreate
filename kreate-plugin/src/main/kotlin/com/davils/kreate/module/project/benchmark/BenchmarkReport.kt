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

import groovy.json.JsonSlurper

/**
 * One measured benchmark, as recorded in a kotlinx-benchmark JSON report.
 *
 * @since 2.2.0
 */
internal data class BenchmarkResult(
    /**
     * The fully qualified benchmark name, for example `com.example.Bench.parse`.
     * @since 2.2.0
     */
    val benchmark: String,
    /**
     * The measurement mode, for example `thrpt` or `avgt`.
     * @since 2.2.0
     */
    val mode: String,
    /**
     * The `@Param` values this measurement was taken with.
     * @since 2.2.0
     */
    val params: Map<String, String>,
    /**
     * The measured score, in [scoreUnit].
     * @since 2.2.0
     */
    val score: Double,
    /**
     * The half-width of the score's confidence interval, or [Double.NaN] when the run
     * produced no error margin.
     * @since 2.2.0
     */
    val scoreError: Double,
    /**
     * The unit the score is expressed in, for example `ops/s`.
     * @since 2.2.0
     */
    val scoreUnit: String
) {
    /**
     * The identity a baseline entry and a current entry are matched on.
     *
     * Parameters are part of it because the same benchmark run with different `@Param`
     * values produces separate measurements that must not be compared with each other.
     *
     * @since 2.2.0
     */
    val key: String
        get() = if (params.isEmpty()) {
            benchmark
        } else {
            val rendered = params.entries
                .sortedBy { it.key }
                .joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            benchmark + rendered
        }
}

/**
 * Reads kotlinx-benchmark JSON reports.
 *
 * Parsing goes through Groovy's `JsonSlurper`, which every Gradle distribution ships, so
 * that reading a report costs the consumer no additional dependency.
 *
 * @since 2.2.0
 */
internal object BenchmarkReport {
    /**
     * Matches the non-finite numeric literals a report can contain.
     *
     * kotlinx-benchmark writes its JSON by string concatenation rather than through a
     * serializer, so a score error of `NaN` — which is what a single-iteration run
     * produces — is emitted as the bare token `NaN`. JSON has no such literal and a
     * conforming parser rejects the document, so the tokens are turned into `null` before
     * parsing and read back as [Double.NaN].
     *
     * @since 2.2.0
     */
    private val NON_FINITE_LITERAL = Regex(""":\s*(NaN|-?Infinity)\b""")

    /**
     * Parses a report.
     *
     * @param json The raw report contents.
     * @return The measurements it contains, in file order.
     * @throws IllegalArgumentException When the document is not a kotlinx-benchmark report.
     * @since 2.2.0
     */
    fun parse(json: String): List<BenchmarkResult> {
        val sanitized = NON_FINITE_LITERAL.replace(json) { ": null" }

        val parsed = runCatching { JsonSlurper().parseText(sanitized) }.getOrElse { cause ->
            throw IllegalArgumentException("The benchmark report is not valid JSON.", cause)
        }

        require(parsed is List<*>) {
            "A benchmark report must be a JSON array of measurements, but the document is " +
                "a ${parsed?.javaClass?.simpleName ?: "null"}."
        }

        return parsed.map { entry ->
            require(entry is Map<*, *>) { "Every entry of a benchmark report must be an object." }
            readResult(entry)
        }
    }

    /**
     * Builds a result from one parsed report entry.
     *
     * @param entry The parsed JSON object.
     * @return The measurement it describes.
     * @throws IllegalArgumentException When a required field is missing.
     * @since 2.2.0
     */
    private fun readResult(entry: Map<*, *>): BenchmarkResult {
        val benchmark = entry["benchmark"] as? String
        requireNotNull(benchmark) { "A benchmark report entry has no 'benchmark' name." }

        val metric = entry["primaryMetric"] as? Map<*, *>
        require(metric != null) { "Benchmark '$benchmark' has no 'primaryMetric'." }

        val score = (metric["score"] as? Number)?.toDouble()
        require(score != null) { "Benchmark '$benchmark' has no numeric 'score'." }

        return BenchmarkResult(
            benchmark = benchmark,
            mode = entry["mode"] as? String ?: "",
            params = readParams(entry["params"]),
            score = score,
            scoreError = readDouble(metric["scoreError"]),
            scoreUnit = metric["scoreUnit"] as? String ?: ""
        )
    }

    /**
     * Reads a numeric field that may have been written as a string.
     *
     * Two different writers produce these reports. Non-JVM targets use kotlinx-benchmark's
     * own formatter, which emits a bare `NaN`; JVM targets are measured by JMH, which
     * writes the report itself and quotes the same value as `"NaN"`. Neither is a number a
     * parser will hand back, and both mean the run produced no error margin.
     *
     * @param value The parsed field value.
     * @return The value as a double, or [Double.NaN] when it is absent or not finite.
     * @since 2.2.0
     */
    private fun readDouble(value: Any?): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: Double.NaN
        else -> Double.NaN
    }

    /**
     * Renders measurements as a canonical report.
     *
     * A baseline is committed and reviewed, so it holds only the fields the gate reads. The
     * report JMH writes carries the JVM path, the full argument list and a percentile table
     * besides — machine-specific detail that would put an absolute path from whoever ran it
     * last into version control and bury a real score change in noise.
     *
     * @param results The measurements to record.
     * @return The canonical report text.
     * @since 2.2.0
     */
    fun render(results: List<BenchmarkResult>): String =
        results.sortedBy { it.key }.joinToString(
            separator = ",\n",
            prefix = "[\n",
            postfix = "\n]\n"
        ) { result ->
            val params = result.params.entries.sortedBy { it.key }
                .joinToString(", ") { """"${it.key}" : "${it.value}"""" }

            listOf(
                "  {",
                """    "benchmark" : "${result.benchmark}",""",
                """    "mode" : "${result.mode}",""",
                """    "params" : { $params },""",
                """    "primaryMetric" : {""",
                """      "score" : ${result.score},""",
                """      "scoreError" : ${renderDouble(result.scoreError)},""",
                """      "scoreUnit" : "${result.scoreUnit}"""",
                "    }",
                "  }"
            ).joinToString("\n")
        }

    /**
     * Renders a double as valid JSON.
     *
     * @param value The value to render.
     * @return The number, or a quoted token for a value JSON cannot express.
     * @since 2.2.0
     */
    private fun renderDouble(value: Double): String =
        if (value.isFinite()) value.toString() else "\"$value\""

    /**
     * Reads the `params` object of a report entry.
     *
     * @param params The parsed value, which is absent or an empty object for a benchmark
     *   without `@Param` properties.
     * @return The parameter values as strings.
     * @since 2.2.0
     */
    private fun readParams(params: Any?): Map<String, String> {
        if (params !is Map<*, *>) return emptyMap()

        return params.entries
            .filter { it.key is String }
            .associate { (key, value) -> key as String to value.toString() }
    }
}
