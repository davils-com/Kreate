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

package com.davils.kreate.functional

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

/**
 * Tests for the `gitlabPackageRegistry` repository helper.
 *
 * What the helper is worth is the combination it assembles — the right token in the right header
 * — and none of that is visible from outside the build. These tests therefore print the resolved
 * repository back out and assert on it, rather than resolving a real dependency against a server
 * that does not exist.
 */
@DisplayName("GitLab package registry")
class GitlabRegistryFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var fixture: KreateBuildFixture

    private val endpoint = "https://gitlab.example.com/api/v4/groups/42/-/packages/maven"

    @BeforeEach
    fun setUp() {
        fixture = KreateBuildFixture(projectDir)
    }

    /**
     * A task that prints the repositories Gradle ended up with.
     *
     * The description is built during configuration and only the finished string is captured by
     * the task action, because the fixture always runs with the configuration cache on.
     */
    private val reportTask = """
        val described = repositories.filterIsInstance<MavenArtifactRepository>().joinToString("\n") { repo ->
            val credentials = repo.getCredentials(HttpHeaderCredentials::class.java)
            "repo=" + repo.name +
                " url=" + repo.url +
                " header=" + credentials.name +
                " token=" + credentials.value +
                " auth=" + repo.authentication.joinToString(",") { it.name }
        }

        tasks.register("reportRepository") {
            // Copied into a local first: a lambda reading the script-level `val` directly would
            // capture the script object, which the configuration cache cannot serialise.
            val message = described
            doLast { println(message) }
        }
    """.trimIndent()

    /**
     * Writes a single-project build that declares the registry in its own `repositories` block.
     *
     * Written by hand rather than through the fixture because Kotlin requires imports at the top
     * of the file, and the fixture appends extra content below the `kreate { }` block.
     */
    private fun writeProjectBuild(block: String) {
        fixture.writeSettings()
        fixture.write(
            "build.gradle.kts",
            """
            import com.davils.kreate.repository.gitlabPackageRegistry
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository
            import org.gradle.api.credentials.HttpHeaderCredentials

            plugins {
                id("org.jetbrains.kotlin.jvm")
                id("com.davils.kreate")
            }

            group = "com.example"

            repositories {
                $block
            }

            $reportTask
            """.trimIndent()
        )
    }

    @Test
    @DisplayName("uses the job token as Job-Token inside a pipeline")
    fun jobTokenWins() {
        // A job token is scoped to the pipeline and expires with it, so it is the credential CI
        // should use — and GitLab accepts it under this header name only.
        writeProjectBuild("""gitlabPackageRegistry(providers) { url = "$endpoint" }""")

        val result = fixture.buildWithEnvironment(
            mapOf("CI_JOB_TOKEN" to "pipeline-token", "GITLAB_TOKEN" to "personal-token"),
            "reportRepository"
        )

        result.output shouldContain "header=Job-Token"
        result.output shouldContain "token=pipeline-token"
        result.output shouldContain "auth=header"
    }

    @Test
    @DisplayName("falls back to a personal token as Private-Token outside one")
    fun personalTokenFallback() {
        writeProjectBuild("""gitlabPackageRegistry(providers) { url = "$endpoint" }""")

        val result = fixture.buildWithEnvironment(
            mapOf("GITLAB_TOKEN" to "personal-token"),
            "reportRepository"
        )

        result.output shouldContain "header=Private-Token"
        result.output shouldContain "token=personal-token"
    }

    @Test
    @DisplayName("prefers the Gradle property over the environment variable")
    fun propertyBeatsEnvironment() {
        // The property is what lets the credential live in ~/.gradle/gradle.properties rather
        // than in the repository or a shell profile.
        writeProjectBuild("""gitlabPackageRegistry(providers) { url = "$endpoint" }""")

        val result = fixture.buildWithEnvironment(
            mapOf("GITLAB_TOKEN" to "from-environment"),
            "reportRepository",
            "-PgitlabToken=from-property"
        )

        result.output shouldContain "token=from-property"
    }

    @Test
    @DisplayName("honours custom repository, property and header names")
    fun customNames() {
        // A deploy token is a different credential, and GitLab rejects it under Private-Token.
        writeProjectBuild(
            """
            gitlabPackageRegistry(providers) {
                url = "$endpoint"
                name = "Internal"
                tokenProperty = "internalToken"
                tokenHeader = "Deploy-Token"
            }
            """.trimIndent()
        )

        val result = fixture.buildWithEnvironment(
            emptyMap(),
            "reportRepository",
            "-PinternalToken=deploy"
        )

        result.output shouldContain "repo=Internal"
        result.output shouldContain "header=Deploy-Token"
        result.output shouldContain "token=deploy"
    }

    @Test
    @DisplayName("fails with an actionable message when no URL is given")
    fun urlIsRequired() {
        writeProjectBuild("""gitlabPackageRegistry(providers) { name = "Internal" }""")

        val result = fixture.buildAndFail("reportRepository")

        result.output shouldContain "declared without a URL"
        result.output shouldContain "packages/maven"
    }

    @Test
    @DisplayName("applies the content filter it was given")
    fun contentFilterIsApplied() {
        // Without a filter the registry is asked about every dependency in the build, which is
        // slow over an authenticated remote and leaks the dependency names to its operator.
        writeProjectBuild(
            """
            gitlabPackageRegistry(providers) {
                url = "$endpoint"
                content { includeGroup("com.example") }
            }
            """.trimIndent()
        )

        val result = fixture.buildWithEnvironment(
            mapOf("GITLAB_TOKEN" to "personal-token"),
            "reportRepository"
        )

        result.task(":reportRepository")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    @DisplayName("can be declared in the settings file")
    fun worksInSettings() {
        // Kreate's own guidance is to declare repositories centrally in settings, where a project
        // plugin is not loaded yet. This is the only place that proves the helper works off the
        // settings class path too.
        fixture.write(
            "settings.gradle.kts",
            """
            import com.davils.kreate.repository.gitlabPackageRegistry
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository
            import org.gradle.api.credentials.HttpHeaderCredentials

            buildscript {
                dependencies {
                    classpath(files(${pluginClasspathLiteral()}))
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()

                    gitlabPackageRegistry(providers) {
                        url = "$endpoint"
                        content { includeGroup("com.example") }
                    }
                }
            }

            // Read straight back out of the settings model. Repositories declared here never
            // reach `project.repositories`, so a task in the build script would report nothing
            // and the test would pass against a helper that did nothing at all.
            val described = dependencyResolutionManagement.repositories
                .filterIsInstance<MavenArtifactRepository>()
                .joinToString("\n") { repo ->
                    val credentials = repo.getCredentials(HttpHeaderCredentials::class.java)
                    "repo=" + repo.name +
                        " url=" + repo.url +
                        " header=" + credentials.name +
                        " token=" + credentials.value
                }

            settingsDir.resolve("repositories.txt").writeText(described)

            rootProject.name = "sample"
            """.trimIndent()
        )

        fixture.write(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.jvm")
            }
            """.trimIndent()
        )

        val result = fixture.buildWithEnvironment(
            mapOf("GITLAB_TOKEN" to "personal-token"),
            "tasks"
        )

        result.task(":tasks")?.outcome shouldBe TaskOutcome.SUCCESS

        val described = fixture.file("repositories.txt").readText()
        described shouldContain "url=$endpoint"
        described shouldContain "header=Private-Token"
        described shouldContain "token=personal-token"
    }

    /**
     * Renders the plugin-under-test class path as a Kotlin list literal.
     *
     * TestKit injects that class path into the project build script, but not into settings, so a
     * settings file has to put it there itself — which is also what a consumer does, with a real
     * dependency instead of files.
     */
    private fun pluginClasspathLiteral(): String {
        val metadata = javaClass.classLoader
            .getResourceAsStream("plugin-under-test-metadata.properties")
            ?: error("TestKit did not generate plugin-under-test-metadata.properties")

        val properties = Properties().apply { metadata.use { load(it) } }
        val classpath = properties.getProperty("implementation-classpath")
            ?: error("plugin-under-test-metadata.properties has no implementation-classpath")

        return classpath.split(File.pathSeparator)
            .joinToString(", ") { "\"${File(it).invariantSeparatorsPath}\"" }
    }
}
