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

import com.davils.kreate.KreateExtension
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * The plugin id Kreate configures but never applies.
 *
 * @since 2.2.0
 */
internal const val BENCHMARK_PLUGIN_ID: String = "org.jetbrains.kotlinx.benchmark"

/**
 * Initializes benchmark support for the project.
 *
 * @param extension The main Kreate extension.
 * @throws GradleException When benchmarks are enabled without the kotlinx-benchmark plugin.
 * @since 2.2.0
 */
internal fun Project.initializeBenchmark(extension: KreateExtension) {
    val benchmarkExtension = extension.project.benchmark
    if (!benchmarkExtension.enabled.get()) return

    // Checked by plugin id, not by a class literal. Kreate depends on kotlinx-benchmark
    // `compileOnly`, so its types are absent at runtime until the consumer applies the
    // plugin; a class literal here would throw NoClassDefFoundError instead of the
    // explanation below. Everything that touches those types lives in `configureBenchmarks`,
    // which is only entered once this check has passed.
    if (!plugins.hasPlugin(BENCHMARK_PLUGIN_ID)) throw missingPluginFailure()

    configureBenchmarks(benchmarkExtension)
}

/**
 * Builds the failure raised when the plugin Kreate configures has not been applied.
 *
 * @return The exception to throw.
 * @since 2.2.0
 */
private fun Project.missingPluginFailure(): GradleException = GradleException(
    """
        Kreate's benchmark integration is enabled, but the kotlinx-benchmark plugin is not
        applied to project '$path'.

        Kreate configures kotlinx-benchmark, it does not apply it — that keeps the version
        under your control instead of pinning it to Kreate's release cycle, and keeps a
        pre-1.0 artifact off your buildscript classpath when you do not want it.

        Add it to your build script:

            plugins {
                id("$BENCHMARK_PLUGIN_ID") version "<version>"
            }

        Or disable the integration with `kreate { project { benchmark { enabled = false } } }`.
    """.trimIndent()
)
