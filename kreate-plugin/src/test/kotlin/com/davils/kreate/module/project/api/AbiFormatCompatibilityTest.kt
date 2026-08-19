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

package com.davils.kreate.module.project.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Checks Kreate's ABI extractor against a dump produced by the Kotlin
 * `binary-compatibility-validator` plugin.
 *
 * The claim this feature makes to consumers is that a checked-in `.api` file survives the
 * move from that plugin to Kreate untouched. This project's own dump is still written by
 * the plugin, which makes it the one piece of evidence that can substantiate the claim —
 * and it stops being evidence the moment the two implementations are allowed to drift.
 */
@DisplayName("BCV format compatibility")
class AbiFormatCompatibilityTest {

    @Test
    @DisplayName("reproduces the dump the binary-compatibility-validator plugin wrote")
    fun reproducesPluginDump() {
        val classesDirectory = File("build/classes/kotlin/main")
        val expectedDump = File("api/kreate-plugin.api")

        // The unit tests must stay runnable before the main classes exist, for instance on
        // a clean checkout driven straight at `:test`.
        assumeTrue(classesDirectory.isDirectory, "Main classes have not been compiled yet.")
        assumeTrue(expectedDump.isFile, "The checked-in dump is missing.")

        val classFiles = classesDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .sortedBy { it.invariantSeparatorsPath }
            .map { it.readBytes() }
            .toList()

        val actual = AbiRenderer.render(
            AbiExtractor.extract(
                classFiles,
                AbiFilterOptions(nonPublicMarkers = setOf("com.davils.kreate.InternalKreateApi"))
            )
        )

        actual shouldBe expectedDump.readText()
    }
}
