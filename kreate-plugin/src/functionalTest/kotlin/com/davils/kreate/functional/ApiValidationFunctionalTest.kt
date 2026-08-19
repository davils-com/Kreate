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
 * Tests for the binary compatibility validation feature.
 */
@DisplayName("API validation")
class ApiValidationFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var fixture: KreateBuildFixture

    @BeforeEach
    fun setUp() {
        fixture = KreateBuildFixture(projectDir)
        fixture.writeSettings()
        writeSource("class Sample {\n    fun greet(): String = \"hello\"\n}")
    }

    private fun writeSource(body: String) {
        fixture.writeKotlin(
            "com/example/Sample.kt",
            """
            package com.example

            $body
            """.trimIndent()
        )
    }

    private fun writeBuild(apiBlock: String = "enabled = true") {
        fixture.writeBuild(
            """
            ${KreateBuildFixture.platformBlock}

            project {
                name = "Sample"
                description = "Fixture"

                apiValidation {
                    $apiBlock
                }
            }
            """.trimIndent()
        )
    }

    private val dump: File get() = fixture.file("api/sample.api")

    @Test
    @DisplayName("records the public interface in the dump")
    fun writesDump() {
        writeBuild()

        val result = fixture.build("kreateApiDump")

        result.task(":kreateApiDump")?.outcome shouldBe TaskOutcome.SUCCESS
        dump.readText() shouldContain "public final class com/example/Sample {"
        dump.readText() shouldContain "public final fun greet ()Ljava/lang/String;"
    }

    @Test
    @DisplayName("passes the check against a freshly written dump")
    fun checkPassesAgainstFreshDump() {
        writeBuild()
        fixture.build("kreateApiDump")

        val result = fixture.build("kreateApiCheck")

        result.task(":kreateApiCheck")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    @DisplayName("fails the check and names the dump task when the interface changed")
    fun checkFailsOnChange() {
        writeBuild()
        fixture.build("kreateApiDump")

        writeSource("class Sample {\n    fun greet(): String = \"hello\"\n    fun added(): Int = 1\n}")
        val result = fixture.buildAndFail("kreateApiCheck")

        result.output shouldContain "The public binary interface of project ':' changed."
        result.output shouldContain "public final fun added ()I"
        result.output shouldContain "./gradlew :kreateApiDump"
    }

    @Test
    @DisplayName("explains that no dump has been recorded yet")
    fun checkFailsWithoutDump() {
        writeBuild()

        val result = fixture.buildAndFail("kreateApiCheck")

        result.output shouldContain "No binary interface dump has been recorded"
        result.output shouldContain "./gradlew :kreateApiDump"
    }

    @Test
    @DisplayName("runs as part of the check lifecycle task")
    fun runsAsPartOfCheck() {
        writeBuild()
        fixture.build("kreateApiDump")

        val result = fixture.build("check")

        result.task(":kreateApiCheck")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    @DisplayName("hides a declaration marked with a configured annotation")
    fun honoursNonPublicMarkers() {
        fixture.writeKotlin(
            "com/example/Marker.kt",
            """
            package com.example

            @Retention(AnnotationRetention.BINARY)
            annotation class Hidden
            """.trimIndent()
        )
        writeSource("class Sample {\n    fun greet(): String = \"hello\"\n    @Hidden fun secret(): Int = 1\n}")
        writeBuild(
            """
            enabled = true
            nonPublicMarkers = setOf("com.example.Hidden")
            """.trimIndent()
        )

        fixture.build("kreateApiDump")

        dump.readText() shouldContain "public final fun greet ()Ljava/lang/String;"
        dump.readText() shouldNotContain "secret"
    }

    @Test
    @DisplayName("is up to date on a second run and reuses the configuration cache")
    fun isUpToDateAndCacheable() {
        writeBuild()
        fixture.build("kreateApiDump")
        fixture.build("kreateApiCheck")

        val result = fixture.build("kreateApiCheck")

        result.task(":kreateApiCheck")?.outcome shouldBe TaskOutcome.UP_TO_DATE
        result.output shouldContain "Configuration cache entry reused"
    }

    @Test
    @DisplayName("registers no tasks while the feature is disabled")
    fun registersNothingWhenDisabled() {
        writeBuild("enabled = false")

        val result = fixture.build("tasks", "--all")

        result.output shouldNotContain "kreateApiDump"
        result.output shouldNotContain "kreateApiCheck"
    }
}
