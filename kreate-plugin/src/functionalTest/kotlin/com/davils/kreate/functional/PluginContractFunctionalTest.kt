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
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests for the behaviour the plugin guarantees regardless of which feature is enabled.
 */
@DisplayName("Plugin contract")
class PluginContractFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var fixture: KreateBuildFixture

    @BeforeEach
    fun setUp() {
        fixture = KreateBuildFixture(projectDir)
        fixture.writeSettings()
        fixture.writeKotlin(
            "com/example/Sample.kt",
            """
            package com.example

            class Sample
            """.trimIndent()
        )
    }

    private val minimalKreateBlock = """
        ${KreateBuildFixture.platformBlock}

        project {
            name = "Sample"
            description = "Fixture"
        }
    """.trimIndent()

    @Test
    @DisplayName("applies cleanly with no feature enabled")
    fun appliesWithoutFeatures() {
        fixture.writeBuild(minimalKreateBlock)

        val result = fixture.build("build")

        result.task(":build")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    @DisplayName("registers no feature tasks when nothing is enabled")
    fun registersNoTasksByDefault() {
        fixture.writeBuild(minimalKreateBlock)

        val result = fixture.build("tasks", "--all")

        // Opt-in features must not clutter a consumer's task list.
        result.output shouldNotContain "kreateJniBuild"
        result.output shouldNotContain "kreateTrivyScan"
    }

    @Test
    @DisplayName("reuses the configuration cache on a second run")
    fun reusesConfigurationCache() {
        fixture.writeBuild(minimalKreateBlock)
        fixture.build("build")

        val result = fixture.build("build")

        // The fixture always passes --configuration-cache, so a problem would fail the run
        // outright; this asserts the entry is actually reusable rather than rebuilt.
        result.output shouldContain "Configuration cache entry reused"
    }

    @Test
    @DisplayName("generates build constants and compiles them")
    fun generatesBuildConstants() {
        fixture.writeBuild(
            """
            $minimalKreateBlock

            project {
                buildConstant {
                    enabled = true
                    className = "SampleConstants"
                    path = "generated/constants"

                    constant("flavour", "enterprise")
                }
            }
            """.trimIndent()
        )

        val result = fixture.build("build")

        result.task(":kreateBuildConstants")?.outcome shouldBe TaskOutcome.SUCCESS
        val generated = fixture.file("build/generated/constants").walkTopDown()
            .single { it.name == "SampleConstants.kt" }
        generated.readText() shouldContain "FLAVOUR"
        generated.readText() shouldContain "enterprise"
    }

    @Test
    @DisplayName("registers the Trivy scan tasks when the feature is enabled")
    fun registersTrivyTasks() {
        fixture.writeBuild(
            """
            $minimalKreateBlock

            trivy {
                enabled = true
            }
            """.trimIndent()
        )

        val result = fixture.build("tasks", "--all")

        result.output shouldContain "kreateTrivyScan"
        result.output shouldContain "kreateTrivySecretScan"
        result.output shouldContain "kreateTrivyLicenseScan"
        result.output shouldContain "kreateTrivyVulnerabilityScan"
    }

    @Test
    @DisplayName("skips a Trivy scan with an actionable message when no lock files exist")
    fun trivySkipsWithoutLockFiles() {
        fixture.writeBuild(
            """
            $minimalKreateBlock

            trivy {
                enabled = true
            }
            """.trimIndent()
        )

        val result = fixture.build("kreateTrivyVulnerabilityScan")

        // Failing here would be hostile: the user has simply not generated lock files yet,
        // and the message has to say how to.
        result.output shouldContain "--write-locks"
    }

    @Test
    @DisplayName("fails with an actionable message when Detekt is enabled without its plugin")
    fun detektRequiresItsPlugin() {
        fixture.writeBuild(
            """
            $minimalKreateBlock

            project {
                detekt {
                    enabled = true
                }
            }
            """.trimIndent()
        )

        val result = fixture.buildAndFail("build")

        result.output shouldContain "dev.detekt"
    }
}
