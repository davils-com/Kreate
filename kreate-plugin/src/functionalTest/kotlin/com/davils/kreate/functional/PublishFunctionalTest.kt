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

/**
 * Tests for publishing to the GitLab Package Registry.
 *
 * The defect these cover is a quiet one: `publish` is a lifecycle task, so a build with a
 * repository but no publication reports `UP-TO-DATE` and succeeds without uploading
 * anything. Asserting on the task graph rather than on the exit code is the point.
 */
@DisplayName("GitLab publishing")
class PublishFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var fixture: KreateBuildFixture

    @BeforeEach
    fun setUp() {
        fixture = KreateBuildFixture(projectDir)
        fixture.writeSettings(projectName = "sample-library")
        fixture.writeKotlin(
            "com/example/Sample.kt",
            """
            package com.example

            class Sample
            """.trimIndent()
        )
    }

    // Deliberately not the CI_* defaults: the suite has to behave the same whether or not it
    // happens to run inside a GitLab pipeline, and this exercises the env name overrides too.
    private fun publishBlock(name: String = "TestRegistry") = """
        project {
            description = "Fixture"

            publish {
                enabled = true
                website = "https://example.com"

                repositories {
                    gitlab {
                        enabled = true
                        name = "$name"
                        tokenEnv = "KREATE_TEST_TOKEN"
                        projectIdEnv = "KREATE_TEST_PROJECT_ID"
                        apiUrlEnv = "KREATE_TEST_API_URL"
                    }
                }
            }
        }
    """.trimIndent()

    private fun jvmKreateBlock() = """
        ${KreateBuildFixture.platformBlock}

        ${publishBlock()}
    """.trimIndent()

    private fun withCiEnvironment() {
        fixture.withEnvironment("KREATE_TEST_TOKEN", "test-job-token")
        fixture.withEnvironment("KREATE_TEST_PROJECT_ID", "4711")
        fixture.withEnvironment("KREATE_TEST_API_URL", "https://gitlab.example.com/api/v4")
    }

    @Test
    @DisplayName("wires publish to a real upload task")
    fun publishUploadsSomething() {
        withCiEnvironment()
        fixture.writeBuild(jvmKreateBlock(), extraPlugins = listOf("""`maven-publish`"""))

        // --dry-run prints the task graph without executing it, so the assertion needs no
        // reachable registry. An empty `publish` would list nothing but `:publish` itself.
        val result = fixture.build("publish", "--dry-run")

        result.output shouldContain ":publishMavenPublicationToTestRegistryRepository"
    }

    @Test
    @DisplayName("registers the publication outside CI as well")
    fun publicationExistsWithoutCiToken() {
        fixture.writeBuild(jvmKreateBlock(), extraPlugins = listOf("""`maven-publish`"""))

        val result = fixture.build("generatePomFileForMavenPublication")

        result.task(":generatePomFileForMavenPublication")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No CI job token found in KREATE_TEST_TOKEN"

        val pom = fixture.file("build/publications/maven/pom-default.xml").readText()
        pom shouldContain "<artifactId>sample-library</artifactId>"
        pom shouldContain "<description>Fixture</description>"
        pom shouldContain "<url>https://example.com</url>"
    }

    @Test
    @DisplayName("publishes a java-platform as a BOM")
    fun publishesJavaPlatform() {
        withCiEnvironment()
        fixture.write(
            "build.gradle.kts",
            """
            plugins {
                `java-platform`
                `maven-publish`
                id("com.davils.kreate")
            }

            group = "com.example"

            kreate {
                ${publishBlock(name = "BomRegistry")}
            }
            """.trimIndent()
        )

        val result = fixture.build("publish", "--dry-run")

        result.output shouldContain ":publishMavenPublicationToBomRegistryRepository"
    }

    @Test
    @DisplayName("fails with a readable message when the registry URL is incomplete")
    fun failsOnIncompleteRegistryCoordinates() {
        // A token but no project id: the old code built "null/projects/null/packages/maven"
        // and failed much later with an unrelated transport error.
        fixture.withEnvironment("KREATE_TEST_TOKEN", "test-job-token")
        fixture.writeBuild(jvmKreateBlock(), extraPlugins = listOf("""`maven-publish`"""))

        val result = fixture.buildAndFail("publish", "--dry-run")

        result.output shouldContain "KREATE_TEST_PROJECT_ID = unset"
    }
}
