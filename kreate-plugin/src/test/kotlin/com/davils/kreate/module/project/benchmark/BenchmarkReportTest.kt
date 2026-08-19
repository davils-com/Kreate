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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Benchmark report parsing")
class BenchmarkReportTest {

    /**
     * A report in exactly the shape kotlinx-benchmark writes it, including the whitespace
     * its hand-written formatter leaves in an empty `params` object.
     */
    private val report = """
        [
          {
            "benchmark" : "com.example.Bench.parse",
            "mode" : "thrpt",
            "configurationName" : "main",
            "warmupIterations" : 5,
            "params" : {
                  
            },
            "advanced" : {
                  "jvmForks" : "1"
            },
            "primaryMetric" : {
               "score": 1234.5,
               "scoreError": 12.5,
               "scoreConfidence" : [
                  1222.0,
                  1247.0
               ],
               "scorePercentiles" : {
                  "100.00" : 1240.0
               },
               "scoreUnit" : "ops/s",
               "rawData" : [
                   [
                     1230.0,
                     1239.0
                   ]
               ]
            },
            "secondaryMetrics" : {
            }
          }
        ]
    """.trimIndent()

    @Test
    @DisplayName("reads the fields the gate depends on")
    fun readsFields() {
        val result = BenchmarkReport.parse(report).single()

        result.benchmark shouldBe "com.example.Bench.parse"
        result.mode shouldBe "thrpt"
        result.score shouldBe 1234.5
        result.scoreError shouldBe 12.5
        result.scoreUnit shouldBe "ops/s"
        result.params shouldBe emptyMap()
    }

    @Test
    @DisplayName("reads a NaN score error, which is not valid JSON")
    fun readsNonFiniteScoreError() {
        // A single-iteration run produces no error margin, and the formatter writes the
        // bare token `NaN`. A conforming parser rejects that, so the reader has to cope.
        val single = report.replace("\"scoreError\": 12.5", "\"scoreError\": NaN")

        val result = BenchmarkReport.parse(single).single()

        result.scoreError.isNaN() shouldBe true
        result.score shouldBe 1234.5
    }

    @Test
    @DisplayName("keeps parameters as part of the benchmark identity")
    fun readsParams() {
        val parameterised = """
            [
              {
                "benchmark" : "com.example.Bench.parse",
                "mode" : "thrpt",
                "params" : {
                      "size" : "1024",
                      "mode" : "fast"
                },
                "primaryMetric" : {
                   "score": 1.0,
                   "scoreError": 0.1,
                   "scoreUnit" : "ops/s"
                }
              }
            ]
        """.trimIndent()

        val result = BenchmarkReport.parse(parameterised).single()

        result.params shouldBe mapOf("size" to "1024", "mode" to "fast")
        // Sorted by parameter name so that the key does not depend on map iteration order.
        result.key shouldBe "com.example.Bench.parse [mode=fast, size=1024]"
    }

    @Test
    @DisplayName("uses the plain name as the key when there are no parameters")
    fun keyWithoutParams() {
        BenchmarkReport.parse(report).single().key shouldBe "com.example.Bench.parse"
    }

    @Test
    @DisplayName("reads the report JMH writes for a JVM target")
    fun readsJmhReport() {
        // JVM targets are measured by JMH, which writes the report itself: it quotes a
        // non-finite error as a string, omits `params` entirely when there are none, and
        // adds a dozen fields about the machine that the gate must simply step over.
        val jmh = """
            [
                {
                    "jmhVersion" : "1.37",
                    "benchmark" : "com.example.SampleBenchmark.sum",
                    "mode" : "thrpt",
                    "threads" : 1,
                    "forks" : 1,
                    "jvm" : "/usr/lib/jvm/java-17-openjdk/bin/java",
                    "jvmArgs" : [ "-Dfile.encoding=UTF-8" ],
                    "measurementIterations" : 2,
                    "primaryMetric" : {
                        "score" : 1.8033110268836617E7,
                        "scoreError" : "NaN",
                        "scoreConfidence" : [ "NaN", "NaN" ],
                        "scoreUnit" : "ops/s"
                    },
                    "secondaryMetrics" : {
                    }
                }
            ]
        """.trimIndent()

        val result = BenchmarkReport.parse(jmh).single()

        result.benchmark shouldBe "com.example.SampleBenchmark.sum"
        result.score shouldBe 1.8033110268836617E7
        result.scoreError.isNaN() shouldBe true
        result.scoreUnit shouldBe "ops/s"
        result.params shouldBe emptyMap()
    }

    @Test
    @DisplayName("renders a canonical baseline that survives a round trip")
    fun rendersCanonicalBaseline() {
        val results = BenchmarkReport.parse(report)

        val rendered = BenchmarkReport.render(results)

        // What goes into version control holds the six fields the gate reads and nothing
        // about the machine that produced them.
        rendered shouldNotContain "rawData"
        rendered shouldNotContain "jvmArgs"
        BenchmarkReport.parse(rendered) shouldBe results
    }

    @Test
    @DisplayName("renders a non-finite score error as valid JSON")
    fun rendersNonFiniteScoreError() {
        val result = BenchmarkResult(
            benchmark = "com.example.Bench.run",
            mode = "thrpt",
            params = mapOf("size" to "8"),
            score = 1.0,
            scoreError = Double.NaN,
            scoreUnit = "ops/s"
        )

        val rendered = BenchmarkReport.render(listOf(result))

        rendered shouldContain """"scoreError" : "NaN""""
        BenchmarkReport.parse(rendered).single() shouldBe result
    }

    @Test
    @DisplayName("reads an empty report")
    fun readsEmptyReport() {
        BenchmarkReport.parse("[]") shouldBe emptyList()
    }

    @Test
    @DisplayName("rejects a document that is not a report")
    fun rejectsNonReport() {
        shouldThrow<IllegalArgumentException> {
            BenchmarkReport.parse("""{ "benchmark": "x" }""")
        }.message.orEmpty() shouldContain "must be a JSON array"

        shouldThrow<IllegalArgumentException> {
            BenchmarkReport.parse("not json at all {")
        }
    }

    @Test
    @DisplayName("names the benchmark whose measurement is unusable")
    fun rejectsEntryWithoutScore() {
        val broken = report.replace("\"score\": 1234.5,", "")

        val message = shouldThrow<IllegalArgumentException> { BenchmarkReport.parse(broken) }.message
        message.orEmpty() shouldContain "com.example.Bench.parse"
    }
}
