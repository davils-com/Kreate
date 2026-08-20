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

import com.davils.buildlogic.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("kreate.kotlin-conventions")
    id("kreate.quality-conventions")
    id("kreate.publish-conventions")
}

/**
 * Carries kotlinx-benchmark into the TestKit plugin classpath.
 *
 * The functional builds have to apply the plugin without a version. Resolving it from the
 * Plugin Portal instead would give it a classloader of its own, which cannot see the Kotlin
 * Gradle plugin TestKit injects, and kotlinx-benchmark fails on `KotlinBasePlugin`.
 */
val benchmarkTestPlugin: Configuration = configurations.create("benchmarkTestPlugin") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(gradleApi())
    implementation(libs.bundles.kreate.plugin)

    // Kreate configures kotlinx-benchmark but never applies it, so it compiles against the
    // plugin's types without shipping them. The consumer's own `id("...benchmark")` supplies
    // the classes at runtime, which is why the presence check in the benchmark module goes
    // through the plugin id rather than a class literal — a class literal would load a type
    // that is not on the runtime classpath.
    compileOnly(libs.benchmark.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(gradleTestKit())
    testImplementation(libs.bundles.kreate.test)
    testImplementation(libs.benchmark.gradle.plugin)
    testRuntimeOnly(libs.junit.platform.launcher)

    benchmarkTestPlugin(libs.benchmark.gradle.plugin)
}


/**
 * TestKit based tests live in their own source set so that a slow, tool dependent
 * suite (CMake, Cargo, Trivy) never blocks the fast unit tests.
 */
val functionalTest: SourceSet = sourceSets.create("functionalTest")

configurations[functionalTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(benchmarkTestPlugin)
}

gradlePlugin {
    vcsUrl = Project.VersionControl.SCM_URL
    website = Project.Organization.WEBSITE_URL

    // Injects the plugin under test onto the classpath of the functional tests.
    testSourceSets(functionalTest)

    plugins {
        create(Project.Identity.NAME.lowercase()) {
            id = "${Project.Identity.GROUP}.${Project.Identity.NAME.lowercase()}"
            description = Project.Identity.DESCRIPTION
            displayName = Project.Identity.NAME
            implementationClass =
                "${Project.Identity.GROUP}.${Project.Identity.NAME.lowercase()}.${Project.Identity.NAME}"
            tags = listOf(
                "kotlin",
                "multiplatform",
                "jni",
                "cinterop",
                "detekt",
                "trivy",
                "publishing",
                "conventions",
                "davils"
            )
        }
    }
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the TestKit based functional tests against real Gradle builds."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath

    useJUnitPlatform {
        // One functional test runs a real JMH benchmark end to end, which costs far more
        // than the rest of the suite combined. It earns its place — nothing else proves
        // that scaffolding, allopen and JMH generation fit together — but a developer
        // iterating on something unrelated should be able to leave it out.
        if (providers.gradleProperty("kreate.test.skipSlow").isPresent) excludeTags("slow")
    }

    systemProperty("kreate.test.gradleVersion", gradle.gradleVersion)
    systemProperty("kreate.test.minGradleVersion", Project.Compatibility.MIN_GRADLE_VERSION)

    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
    dependsOn(functionalTestTask)
}

tasks.named<KotlinCompile>("compileFunctionalTestKotlin") {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=disable")
}
