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

package com.davils.kreate.module.project.benchmark.tasks

import com.davils.kreate.KreateTasks
import com.davils.kreate.jobs.Task
import com.davils.kreate.module.project.benchmark.BenchmarkComparator
import com.davils.kreate.module.project.benchmark.BenchmarkReport
import com.davils.kreate.module.project.benchmark.ComparisonRenderer
import com.davils.kreate.module.project.benchmark.RegressionSettings
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Fails the build when a benchmark got measurably slower than its recorded baseline.
 *
 * @since 2.2.0
 */
@CacheableTask
public abstract class BenchmarkCheck : Task(
    "Compares the benchmark results against the committed baseline.",
    KreateTasks.Benchmark.GROUP
) {
    /**
     * The normalized report directory produced by [NormalizeBenchmarkReport].
     *
     * @since 2.2.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val normalizedReports: ConfigurableFileCollection

    /**
     * The committed baseline, as a collection holding at most one file.
     *
     * A collection rather than a `RegularFileProperty` because the baseline legitimately
     * does not exist before the first run, and `@InputFile` fails on a missing file before
     * the task action can explain what to do about it.
     *
     * @since 2.2.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val baselineFile: ConfigurableFileCollection

    /**
     * The profile the comparison covers.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val profile: Property<String>

    /**
     * The default threshold in per cent.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val maxRegressionPercent: Property<Double>

    /**
     * Per-benchmark thresholds in per cent.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val thresholdOverrides: MapProperty<String, Double>

    /**
     * Whether a benchmark missing from the run fails the build.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val failOnMissingBenchmark: Property<Boolean>

    /**
     * Whether a regression must exceed the combined measurement error to count.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val requireSignificance: Property<Boolean>

    /**
     * The path of the project being checked, used in the failure message.
     *
     * Carried as an input rather than read from `project` at execution time, which is what
     * makes the task compatible with the configuration cache.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val projectPath: Property<String>

    /**
     * The path of the task that records a new baseline, named in the failure message.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val baselineTaskPath: Property<String>

    /**
     * The Markdown comparison written on every run, passing or failing.
     *
     * @since 2.2.0
     */
    @get:OutputFile
    public abstract val comparisonReportFile: RegularFileProperty

    /**
     * Compares the run against the baseline.
     *
     * @return Unit
     * @throws GradleException When the baseline is missing or a benchmark regressed.
     * @since 2.2.0
     */
    @TaskAction
    public fun execute() {
        val currentFile = normalizedReports.asFileTree
            .matching { include("**/*.json") }
            .files
            .minByOrNull { it.name }
            ?: throw GradleException(
                listOf(
                    "No normalized benchmark report is available for profile '${profile.get()}'.",
                    "",
                    "Run the benchmarks first:",
                    "",
                    "    ./gradlew benchmark"
                ).joinToString("\n")
            )

        val baseline = baselineFile.files.singleOrNull()?.takeIf { it.isFile }
            ?: missingBaselineFailure()

        val settings = RegressionSettings(
            maxRegressionPercent = maxRegressionPercent.get(),
            thresholdOverrides = thresholdOverrides.get(),
            failOnMissingBenchmark = failOnMissingBenchmark.get(),
            requireSignificance = requireSignificance.get()
        )

        val deltas = BenchmarkComparator.compare(
            baseline = BenchmarkReport.parse(baseline.readText()),
            current = BenchmarkReport.parse(currentFile.readText()),
            settings = settings
        )

        writeComparison(deltas)

        val failures = BenchmarkComparator.failures(deltas, settings)
        if (failures.isEmpty()) {
            logger.lifecycle("Compared ${deltas.size} benchmark(s) against the baseline; no regression.")
            return
        }

        // Assembled line by line rather than as a raw string: `trimIndent` measures the
        // interpolated result, so the unindented table would stop every other line from
        // being trimmed.
        throw GradleException(
            listOf(
                "Benchmark regression in project '${projectPath.get()}' " +
                    "(profile '${profile.get()}'):",
                "",
                ComparisonRenderer.renderFailures(failures),
                "",
                "If the change is expected, record the new baseline and commit the result:",
                "",
                "    ./gradlew ${baselineTaskPath.get()}",
                "",
                "Full comparison: ${comparisonReportFile.get().asFile.path}"
            ).joinToString("\n")
        )
    }

    /**
     * Builds the failure raised when no baseline has been recorded yet.
     *
     * @return Never; the return type lets the caller use it as an elvis branch.
     * @throws GradleException Always.
     * @since 2.2.0
     */
    private fun missingBaselineFailure(): Nothing = throw GradleException(
        listOf(
            "No benchmark baseline has been recorded for project '${projectPath.get()}' yet.",
            "",
            "Create it and commit the result:",
            "",
            "    ./gradlew ${baselineTaskPath.get()}"
        ).joinToString("\n")
    )

    private fun writeComparison(deltas: List<com.davils.kreate.module.project.benchmark.BenchmarkDelta>) {
        val target: File = comparisonReportFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(ComparisonRenderer.renderMarkdown(deltas, profile.get()))
    }
}
