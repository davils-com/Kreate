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
 * Tests for the dependency locking feature and its `kreateResolveAndLockAll` task.
 */
@DisplayName("Dependency locking")
class DependencyLockingFunctionalTest {

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

    private fun writeBuild(lockingBlock: String = "enabled = true") {
        fixture.writeBuild(
            kreateBlock = """
                ${KreateBuildFixture.platformBlock}

                project {
                    name = "Sample"
                    description = "Fixture"

                    dependencyLocking {
                        $lockingBlock
                    }
                }
            """.trimIndent(),
            // A dependency nothing else pulls in: the Kotlin plugin contributes a newer
            // stdlib of its own, so pinning that would only ever exercise Gradle's conflict
            // resolution rather than the lock state.
            extra = """
                dependencies {
                    implementation("org.apache.commons:commons-lang3:3.14.0")
                }
            """.trimIndent()
        )
    }

    private val lockFile: File get() = fixture.file("gradle.lockfile")

    @Test
    @DisplayName("writes a lock file for the locked classpaths")
    fun writesLockFile() {
        writeBuild()

        val result = fixture.build("kreateResolveAndLockAll", "--write-locks")

        result.task(":kreateResolveAndLockAll")?.outcome shouldBe TaskOutcome.SUCCESS
        lockFile.isFile shouldBe true
        lockFile.readText() shouldContain "org.apache.commons:commons-lang3:3.14.0"
        lockFile.readText() shouldContain "compileClasspath"
        lockFile.readText() shouldContain "runtimeClasspath"
    }

    @Test
    @DisplayName("refuses to run without --write-locks and says what to type instead")
    fun refusesWithoutWriteLocks() {
        writeBuild()

        val result = fixture.buildAndFail("kreateResolveAndLockAll")

        result.output shouldContain "only makes sense with --write-locks"
        result.output shouldContain "./gradlew kreateResolveAndLockAll --write-locks"
        lockFile.isFile shouldBe false
    }

    @Test
    @DisplayName("locks only the configured classpaths")
    fun locksOnlyConfiguredClasspaths() {
        writeBuild(
            """
            enabled = true
            lockedClasspaths = setOf("runtimeClasspath")
            """.trimIndent()
        )

        fixture.build("kreateResolveAndLockAll", "--write-locks")

        lockFile.readText() shouldContain "runtimeClasspath"
        lockFile.readText() shouldNotContain "compileClasspath"
    }

    @Test
    @DisplayName("locks the build tool classpaths too when asked to lock everything")
    fun locksAllConfigurations() {
        writeBuild(
            """
            enabled = true
            lockAllConfigurations = true
            """.trimIndent()
        )

        fixture.build("kreateResolveAndLockAll", "--write-locks")

        // The point of the default is that this is what it avoids: the Kotlin compiler's
        // own classpath ends up pinned alongside the dependencies that actually ship.
        lockFile.readText() shouldContain "kotlinCompilerClasspath"
    }

    @Test
    @DisplayName("fails a build that pulls in a dependency the lock file does not know")
    fun failsOnDrift() {
        writeBuild()
        fixture.build("kreateResolveAndLockAll", "--write-locks")

        // A version change alone is not drift: the lock is applied as a strict constraint,
        // so a lower declared version is silently raised back to the locked one. What the
        // lock does reject is a module that was not there when it was written.
        fixture.write(
            "build.gradle.kts",
            fixture.file("build.gradle.kts").readText().replace(
                """implementation("org.apache.commons:commons-lang3:3.14.0")""",
                """implementation("org.apache.commons:commons-lang3:3.14.0")
        implementation("org.apache.commons:commons-io:1.3.2")"""
            )
        )
        val result = fixture.buildAndFail("build")

        result.output shouldContain "dependency lock state"
        result.output shouldContain "commons-io"
    }

    @Test
    @DisplayName("registers no task while the feature is disabled")
    fun registersNothingWhenDisabled() {
        writeBuild("enabled = false")

        val result = fixture.build("tasks", "--all")

        result.output shouldNotContain "kreateResolveAndLockAll"
    }
}
