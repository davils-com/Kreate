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
import com.davils.kreate.module.project.benchmark.BenchmarkReport
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Records the current benchmark results as the baseline the gate compares against.
 *
 * The baseline is committed, so a change to it is a change a reviewer sees and approves —
 * the same arrangement as the checked-in API dump.
 *
 * @since 2.2.0
 */
@CacheableTask
public abstract class BenchmarkBaseline : Task(
    "Records the current benchmark results as the committed baseline.",
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
     * The profile the baseline is recorded for, used in messages.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val profile: Property<String>

    /**
     * The baseline file to write.
     *
     * @since 2.2.0
     */
    @get:OutputFile
    public abstract val baselineFile: RegularFileProperty

    /**
     * Writes the baseline.
     *
     * @return Unit
     * @throws GradleException When no normalized report is available.
     * @since 2.2.0
     */
    @TaskAction
    public fun execute() {
        val report = normalizedReports.asFileTree
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

        // Rewritten rather than copied. A raw JMH report carries the JVM path, the full
        // argument list and a percentile table; committing that would put one developer's
        // absolute paths into version control and bury the scores a reviewer came to read.
        val results = BenchmarkReport.parse(report.readText())

        val target = baselineFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(BenchmarkReport.render(results))

        logger.lifecycle("Recorded ${results.size} benchmark result(s) as the baseline in ${target.path}.")
    }
}
