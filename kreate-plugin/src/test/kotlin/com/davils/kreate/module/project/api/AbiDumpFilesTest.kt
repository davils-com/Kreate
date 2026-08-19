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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@DisplayName("ABI dump reading")
class AbiDumpFilesTest {

    @TempDir
    lateinit var directory: File

    private val lfDump = "public final class com/example/Sample {\n\tpublic fun <init> ()V\n}\n\n"

    @Test
    @DisplayName("leaves a dump written with line feeds alone")
    fun keepsLineFeeds() {
        readAbiDump(dumpFile(lfDump)) shouldBe lfDump
    }

    @Test
    @DisplayName("accepts a dump Git checked out with carriage returns")
    fun normalizesCarriageReturns() {
        readAbiDump(dumpFile(lfDump.replace("\n", "\r\n"))) shouldBe lfDump
    }

    @Test
    @DisplayName("reports no difference against a dump that only differs in its line endings")
    fun comparesEqualToTheRenderedForm() {
        val expected = readAbiDump(dumpFile(lfDump.replace("\n", "\r\n")))

        AbiDiff.render(expected = expected, actual = lfDump) shouldBe null
    }

    private fun dumpFile(content: String): File =
        File(directory, "sample.api").apply { writeText(content) }
}
