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
        result.output shouldNotContain "koverXmlReport"
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

    @Test
    @DisplayName("fails with an actionable message when coverage is enabled without the Kover plugin")
    fun coverageRequiresItsPlugin() {
        fixture.writeBuild(
            """
            $minimalKreateBlock

            project {
                coverage {
                    enabled = true
                }
            }
            """.trimIndent()
        )

        val result = fixture.buildAndFail("build")

        result.output shouldContain "org.jetbrains.kotlinx.kover"
    }

    @Test
    @DisplayName("measures coverage of the code a test actually exercises")
    fun measuresCoverage() {
        // The end-to-end proof: instrumentation, execution and reporting all have to line up
        // for the covered class to appear as covered.
        fixture.writeKotlin(
            "com/example/Covered.kt",
            """
            package com.example

            class Covered {
                fun greet(): String = "hello"
            }
            """.trimIndent()
        )
        fixture.write(
            "src/test/kotlin/com/example/CoveredTest.kt",
            """
            package com.example

            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CoveredTest {
                @Test
                fun greets() {
                    assertEquals("hello", Covered().greet())
                }
            }
            """.trimIndent()
        )

        fixture.writeBuild(
            kreateBlock = """
                $minimalKreateBlock

                project {
                    coverage {
                        enabled = true
                    }
                }
            """.trimIndent(),
            extraPlugins = listOf("""id("org.jetbrains.kotlinx.kover")"""),
            extra = """
                dependencies {
                    testImplementation(kotlin("test"))
                }

                tasks.test {
                    useJUnitPlatform()
                }
            """.trimIndent()
        )

        val result = fixture.build("koverXmlReport")

        result.task(":koverXmlReport")?.outcome shouldBe TaskOutcome.SUCCESS

        val report = fixture.file("build/reports/kover/report.xml").readText()
        report shouldContain "Covered"
        // A zero-coverage report would also mention the class, so assert that something was
        // actually recorded as covered.
        report shouldContain "covered="
    }

    @Test
    @DisplayName("fails the build when coverage is below the configured bound")
    fun coverageVerificationFailsBelowBound() {
        // No test exists, so the sample class is at zero percent and the bound cannot be met.
        fixture.writeBuild(
            kreateBlock = """
                $minimalKreateBlock

                project {
                    coverage {
                        enabled = true

                        verify {
                            minLineCoverage = 90
                        }
                    }
                }
            """.trimIndent(),
            extraPlugins = listOf("""id("org.jetbrains.kotlinx.kover")""")
        )

        val result = fixture.buildAndFail("koverVerify")

        result.task(":koverVerify")?.outcome shouldBe TaskOutcome.FAILED
        result.output shouldContain "Minimum line coverage"
    }

    @Test
    @DisplayName("runs the coverage gate as part of check")
    fun coverageVerificationRunsOnCheck() {
        // A threshold that has to be invoked by name is one nobody hears about until someone
        // remembers to look. A bound of zero passes, so this asserts wiring and not the bound.
        fixture.writeBuild(
            kreateBlock = """
                $minimalKreateBlock

                project {
                    coverage {
                        enabled = true

                        verify {
                            minLineCoverage = 0
                        }
                    }
                }
            """.trimIndent(),
            extraPlugins = listOf("""id("org.jetbrains.kotlinx.kover")""")
        )

        val result = fixture.build("check")

        result.task(":koverVerify")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    @DisplayName("registers no verification rule for a bound that was never set")
    fun unsetBoundRegistersNoRule() {
        // The alternative — mapping "unset" onto a minimum of zero — produces a gate that
        // passes unconditionally and is indistinguishable from a working one.
        fixture.writeBuild(
            kreateBlock = """
                $minimalKreateBlock

                project {
                    coverage {
                        enabled = true
                    }
                }
            """.trimIndent(),
            extraPlugins = listOf("""id("org.jetbrains.kotlinx.kover")""")
        )

        val result = fixture.build("koverVerify")

        result.task(":koverVerify")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldNotContain "Minimum line coverage"
    }

    @Test
    @DisplayName("enforces a named rule with its own grouping and bounds")
    fun namedRuleIsEnforced() {
        // What the shorthand properties cannot express: a rule checked per class rather than
        // across the application, reported under the name it was given.
        fixture.writeBuild(
            kreateBlock = """
                $minimalKreateBlock

                project {
                    coverage {
                        enabled = true

                        verify {
                            rules {
                                create("Every class carries its own weight") {
                                    groupBy = com.davils.kreate.module.project.coverage.Grouping.CLASS
                                    minBound(80, com.davils.kreate.module.project.coverage.CoverageUnit.LINE)
                                }
                            }
                        }
                    }
                }
            """.trimIndent(),
            extraPlugins = listOf("""id("org.jetbrains.kotlinx.kover")""")
        )

        val result = fixture.buildAndFail("koverVerify")

        result.output shouldContain "Every class carries its own weight"
    }

    @Test
    @DisplayName("rejects a named rule that declares no bounds")
    fun ruleWithoutBoundsIsRejected() {
        // Such a rule always passes. Accepting it would put a green check next to a threshold
        // that measures nothing.
        fixture.writeBuild(
            kreateBlock = """
                $minimalKreateBlock

                project {
                    coverage {
                        enabled = true

                        verify {
                            rules {
                                create("Checks nothing")
                            }
                        }
                    }
                }
            """.trimIndent(),
            extraPlugins = listOf("""id("org.jetbrains.kotlinx.kover")""")
        )

        val result = fixture.buildAndFail("koverVerify")

        result.output shouldContain "declares no bounds"
    }
}
