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

import com.davils.kreate.module.project.benchmark.extension.BenchmarkExtension
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.allopen.gradle.AllOpenGradleSubplugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

private const val JMH_STATE_ANNOTATION = "org.openjdk.jmh.annotations.State"
private const val BENCHMARK_RUNTIME_MODULE = "org.jetbrains.kotlinx:kotlinx-benchmark-runtime"

/**
 * Builds the source set the benchmarks live in.
 *
 * Benchmarks are kept out of `main` deliberately: JMH generates code into the source set it
 * measures, and the `allopen` transformation that JMH needs would otherwise apply to the
 * classes you publish.
 *
 * Three things have to be true for the source set to be usable, and the third is the one
 * that is easy to miss:
 *
 * 1. it carries `kotlinx-benchmark-runtime`,
 * 2. it is associated with `main`, so it sees `internal` declarations and inherits `main`'s
 *    dependencies — without that you benchmark a facade,
 * 3. the `allopen` plugin opens every `@State` class, because JMH subclasses them.
 *
 * @param extension The benchmark configuration.
 * @return The name of the source set that was set up.
 * @since 2.2.0
 */
internal fun Project.setUpBenchmarkSourceSet(extension: BenchmarkExtension): String {
    val name = extension.sourceSetName.get()

    if (extension.applyAllOpen.get()) applyAllOpenForJmh()
    if (!extension.createSourceSet.get()) return name

    val runtime = "$BENCHMARK_RUNTIME_MODULE:${extension.runtimeVersion.get()}"

    plugins.withId("java") {
        extensions.getByType<SourceSetContainer>().maybeCreate(name)
        dependencies.add("${name}Implementation", runtime)

        plugins.withId("org.jetbrains.kotlin.jvm") {
            extensions.configure<KotlinJvmProjectExtension> {
                val compilations = target.compilations
                compilations.getByName(name)
                    .associateWith(compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME))
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinJvmTarget>().configureEach {
                val benchmarkCompilation = compilations.maybeCreate(name)
                benchmarkCompilation.associateWith(
                    compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                )
                dependencies.add(
                    benchmarkCompilation.compileDependencyConfigurationName,
                    runtime
                )
            }
        }
    }

    return name
}

/**
 * Applies and configures the `allopen` compiler plugin for JMH's state annotation.
 *
 * Unlike kotlinx-benchmark itself, this plugin is applied by Kreate rather than left to the
 * consumer. There is nothing to decide: JMH subclasses every `@State` class, so a final
 * benchmark class fails during generation with an error that never mentions `allopen`, and
 * no project that enables benchmarks wants a different setting here.
 *
 * @since 2.2.0
 */
private fun Project.applyAllOpenForJmh() {
    pluginManager.apply(AllOpenGradleSubplugin::class)
    extensions.configure<AllOpenExtension> { annotation(JMH_STATE_ANNOTATION) }
}
