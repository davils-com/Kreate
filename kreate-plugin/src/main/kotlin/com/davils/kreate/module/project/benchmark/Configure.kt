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

import com.davils.kreate.KreateTasks
import com.davils.kreate.module.project.benchmark.extension.BenchmarkExtension
import com.davils.kreate.module.project.benchmark.extension.BenchmarkProfileExtension
import com.davils.kreate.module.project.benchmark.tasks.BenchmarkBaseline
import com.davils.kreate.module.project.benchmark.tasks.BenchmarkCheck
import com.davils.kreate.module.project.benchmark.tasks.NormalizeBenchmarkReport
import kotlinx.benchmark.gradle.BenchmarkConfiguration
import kotlinx.benchmark.gradle.BenchmarksExtension
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

private const val MAIN_PROFILE = "main"
private const val MAX_THROUGHPUT_REGRESSION_PERCENT = 100.0
private const val JSON_REPORT_FORMAT = "json"

/**
 * Configures kotlinx-benchmark and registers Kreate's benchmark tasks.
 *
 * Kept in its own file, and therefore its own class, on purpose: it is the only code that
 * touches kotlinx-benchmark types, and Kreate depends on that plugin `compileOnly`. Keeping
 * the reference out of [initializeBenchmark] is what lets the missing-plugin check report a
 * readable failure instead of a `NoClassDefFoundError`.
 *
 * @param extension The benchmark configuration.
 * @since 2.2.0
 */
internal fun Project.configureBenchmarks(extension: BenchmarkExtension) {
    val targetName = setUpBenchmarkSourceSet(extension)

    extensions.configure<BenchmarksExtension> {
        extension.profiles.forEach { profile ->
            configurations.maybeCreate(profile.name).applyProfile(profile)
        }

        // Registered after the profiles: the plugin materializes a target through a live
        // `all` hook and builds one execution task per configuration known at that moment.
        targets.register(targetName) {
            if (this is JvmBenchmarkTarget) jmhVersion = extension.jmhVersion.get()
        }

        registerBenchmarkTasks(extension, targetName, reportsDir)
    }
}

/**
 * Copies a Kreate profile onto the kotlinx-benchmark configuration of the same name.
 *
 * Only the settings Kreate manages are written. Everything else the plugin offers stays
 * available through its own `benchmark { }` block and is left untouched here, so that the
 * two ways of configuring a profile do not fight over the same fields.
 *
 * @param profile The Kreate profile to copy.
 * @since 2.2.0
 */
private fun BenchmarkConfiguration.applyProfile(profile: BenchmarkProfileExtension) {
    warmups = profile.warmups.get()
    iterations = profile.iterations.get()
    iterationTime = profile.iterationTime.get()
    iterationTimeUnit = profile.iterationTimeUnit.get()
    mode = profile.mode.get()
    reportFormat = profile.reportFormat.get()
    profile.outputTimeUnit.orNull?.let { outputTimeUnit = it }

    profile.includes.get().forEach(::include)
    profile.excludes.get().forEach(::exclude)
    profile.params.get().forEach { (name, values) -> values.forEach { param(name, it) } }
    profile.advanced.get().forEach { (name, value) -> advanced(name, value) }
}

/**
 * Registers the normalization, baseline and check tasks for the gate's profile.
 *
 * @param extension The benchmark configuration.
 * @param targetName The name of the registered benchmark target.
 * @param reportsDir The directory kotlinx-benchmark writes reports into, relative to the
 *   build directory.
 * @since 2.2.0
 */
private fun Project.registerBenchmarkTasks(
    extension: BenchmarkExtension,
    targetName: String,
    reportsDir: String
) {
    val regression = extension.regression
    if (!regression.enabled.get()) return

    val profileName = regression.profile.get()
    verifyGateProfile(extension, profileName)
    warnOnUnreachableThreshold(extension, profileName, regression.maxRegressionPercent.get())

    // Named rather than resolved: kotlinx-benchmark creates this task inside its own
    // `afterEvaluate`, which may run before or after Kreate's depending on the order the
    // plugins were declared in. A task name is resolved lazily and works either way.
    val executionTaskName = targetName + profileName.execSuffix() + "Benchmark"

    val normalizeTask = tasks.register<NormalizeBenchmarkReport>(KreateTasks.Benchmark.REPORT) {
        dependsOn(executionTaskName)
        profile.set(profileName)
        timestampedReportsDirectory.set(layout.buildDirectory.dir("$reportsDir/$profileName"))
        normalizedDirectory.set(layout.buildDirectory.dir("reports/kreate/benchmark/$profileName"))
    }

    val baselineTask = tasks.register<BenchmarkBaseline>(KreateTasks.Benchmark.BASELINE) {
        normalizedReports.from(normalizeTask)
        profile.set(profileName)
        baselineFile.set(regression.baselineFile)
    }

    val projectPath = path
    val baselineTaskPath = qualifiedTaskPath(KreateTasks.Benchmark.BASELINE)

    tasks.register<BenchmarkCheck>(KreateTasks.Benchmark.CHECK) {
        // Asking for both in one invocation only makes sense in that order. Without this the
        // check may run against the previous baseline and report a regression the user has
        // just recorded away.
        mustRunAfter(baselineTask)
        normalizedReports.from(normalizeTask)
        baselineFile.from(regression.baselineFile)
        profile.set(profileName)
        maxRegressionPercent.set(regression.maxRegressionPercent)
        thresholdOverrides.set(regression.thresholdOverrides)
        failOnMissingBenchmark.set(regression.failOnMissingBenchmark)
        requireSignificance.set(regression.requireSignificance)
        this.projectPath.set(projectPath)
        this.baselineTaskPath.set(baselineTaskPath)
        comparisonReportFile.set(
            layout.buildDirectory.file("reports/kreate/benchmark/comparison.md")
        )
    }
}

/**
 * Fails when the profile the gate reads cannot produce a report the gate can parse.
 *
 * @param extension The benchmark configuration.
 * @param profileName The profile the gate is configured to read.
 * @throws GradleException When the profile is unknown or does not report JSON.
 * @since 2.2.0
 */
private fun verifyGateProfile(extension: BenchmarkExtension, profileName: String) {
    val profile = extension.profiles.findByName(profileName)
        ?: if (profileName == MAIN_PROFILE) {
            return
        } else {
            throw GradleException(
                "The benchmark regression gate reads profile '$profileName', which is not " +
                    "configured. Register it under `benchmark { profiles { register(\"" +
                    "$profileName\") { } } }`, or point `regression { profile }` at one that exists."
            )
        }

    val format = profile.reportFormat.get()
    if (!format.equals(JSON_REPORT_FORMAT, ignoreCase = true)) {
        throw GradleException(
            "The benchmark regression gate reads profile '$profileName', which is " +
                "configured to report '$format'. The gate can only read '$JSON_REPORT_FORMAT'; " +
                "passing a run it cannot parse would be worse than failing here."
        )
    }
}

/**
 * Warns when the configured threshold can never be reached.
 *
 * In a throughput mode the score cannot fall below zero, so the worst regression expressible
 * is 100%. A larger threshold silently turns the gate off, which looks like a passing build
 * rather than like a disabled check — the one failure mode a gate must not have.
 *
 * @param extension The benchmark configuration.
 * @param profileName The profile the gate reads.
 * @param threshold The configured threshold in per cent.
 * @since 2.2.0
 */
private fun Project.warnOnUnreachableThreshold(
    extension: BenchmarkExtension,
    profileName: String,
    threshold: Double
) {
    val mode = extension.profiles.findByName(profileName)?.mode?.get()
    val unreachable = mode != null &&
        BenchmarkDirection.forMode(mode) == BenchmarkDirection.HIGHER_IS_BETTER &&
        threshold >= MAX_THROUGHPUT_REGRESSION_PERCENT
    if (!unreachable) return

    logger.warn(
        "Kreate's benchmark gate for project '$path' is set to $threshold%, but profile " +
            "'$profileName' measures '$mode', where a score cannot drop by more than " +
            "$MAX_THROUGHPUT_REGRESSION_PERCENT%. No regression can reach that threshold, so " +
            "the gate will never fail. Lower `regression { maxRegressionPercent }`, or set " +
            "`regression { enabled = false }` if switching it off is what you meant."
    )
}

/**
 * Renders the profile name as kotlinx-benchmark spells it inside a task name.
 *
 * The `main` profile contributes nothing, every other profile contributes its capitalized
 * name — the same rule the plugin's own `capitalizedName()` applies.
 *
 * @return The task name segment for this profile.
 * @since 2.2.0
 */
private fun String.execSuffix(): String =
    if (this == MAIN_PROFILE) "" else replaceFirstChar { it.titlecase() }

/**
 * Builds the fully qualified path of a task in this project.
 *
 * @param taskName The task name.
 * @return The path a user can type, correct for the root project as well.
 * @since 2.2.0
 */
private fun Project.qualifiedTaskPath(taskName: String): String =
    if (path == Project.PATH_SEPARATOR) {
        "${Project.PATH_SEPARATOR}$taskName"
    } else {
        "$path${Project.PATH_SEPARATOR}$taskName"
    }
