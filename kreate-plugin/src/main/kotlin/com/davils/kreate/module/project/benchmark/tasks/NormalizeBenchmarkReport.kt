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
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Copies the newest kotlinx-benchmark report to a stable, declared location.
 *
 * kotlinx-benchmark writes to `build/reports/benchmarks/<profile>/<timestamp>/<target>.json`.
 * The timestamp is added by the plugin itself and cannot be configured away — `reportsDir`
 * moves the parent directory but not the timestamped segment below it. That makes the
 * report unusable as a task output: nothing downstream can declare it as an input, nothing
 * can be up to date against it, and the directory grows by one entry per run forever.
 *
 * This task resolves the newest run once and republishes it under a fixed path, which is
 * what makes the regression gate an ordinary cacheable task with declared inputs.
 *
 * @since 2.2.0
 */
@DisableCachingByDefault(because = "Reads a directory whose name changes on every run")
public abstract class NormalizeBenchmarkReport : Task(
    "Copies the newest benchmark report to a stable location.",
    KreateTasks.Benchmark.GROUP
) {
    /**
     * The directory kotlinx-benchmark writes its timestamped runs into.
     *
     * Declared `@Internal` rather than as an input: its contents change on every run by
     * design, so an up-to-date check over it would only ever be wrong.
     *
     * @since 2.2.0
     */
    @get:Internal
    public abstract val timestampedReportsDirectory: DirectoryProperty

    /**
     * The profile whose reports are normalized, used in messages.
     *
     * @since 2.2.0
     */
    @get:Input
    public abstract val profile: Property<String>

    /**
     * The directory the newest run is copied into.
     *
     * @since 2.2.0
     */
    @get:OutputDirectory
    public abstract val normalizedDirectory: DirectoryProperty

    init {
        // The source directory is a new path on every run, so there is nothing to compare
        // against and the task must simply do its work whenever it is asked to.
        outputs.upToDateWhen { false }
    }

    /**
     * Copies the reports of the newest run.
     *
     * @return Unit
     * @throws GradleException When no run has been recorded for the profile.
     * @since 2.2.0
     */
    @TaskAction
    public fun execute() {
        val source = timestampedReportsDirectory.get().asFile
        val newestRun = newestRunDirectory(source)
            ?: throw GradleException(
                listOf(
                    "No benchmark report was found for profile '${profile.get()}'.",
                    "",
                    "Expected a run below ${source.path}.",
                    "Run the benchmarks first — the report is written by kotlinx-benchmark, " +
                        "not by this task."
                ).joinToString("\n")
            )

        val target = normalizedDirectory.get().asFile
        target.deleteRecursively()
        target.mkdirs()

        val reports = newestRun.listFiles()?.filter { it.isFile }.orEmpty()
        reports.forEach { report -> report.copyTo(target.resolve(report.name), overwrite = true) }

        logger.lifecycle(
            "Normalized ${reports.size} benchmark report(s) from ${newestRun.name} to ${target.path}."
        )
    }

    /**
     * Finds the most recently written run directory.
     *
     * The directory names are ISO timestamps with the colons replaced, so they sort
     * lexicographically in chronological order; the modification time is used as the
     * tie-breaker for two runs within the same second.
     *
     * @param reportsDirectory The parent of the timestamped run directories.
     * @return The newest run, or `null` when the profile has never been run.
     * @since 2.2.0
     */
    private fun newestRunDirectory(reportsDirectory: File): File? =
        reportsDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.maxWithOrNull(compareBy({ it.lastModified() }, { it.name }))
}
