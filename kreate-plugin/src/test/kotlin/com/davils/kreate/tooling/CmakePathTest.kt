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

package com.davils.kreate.tooling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [toCmakePath].
 *
 * These run on every platform, including the ones where the conversion is a no-op. That is the
 * point: the defect this guards against only reproduces on Windows, so the rule has to be
 * verifiable without a Windows machine.
 */
@DisplayName("CMake path conversion")
class CmakePathTest {

    @Test
    @DisplayName("converts a Windows JDK path into a form the CMake language can parse")
    fun convertsWindowsJdkPath() {
        // The exact path from the CI failure. Passed with backslashes, FindJNI expands it into
        // a macro argument and CMake aborts with "Invalid character escape '\h'".
        val windowsJavaHome = """C:\hostedtoolcache\windows\Java_Temurin-Hotspot_jdk\17.0.20-8\x64"""

        windowsJavaHome.toCmakePath() shouldBe
            "C:/hostedtoolcache/windows/Java_Temurin-Hotspot_jdk/17.0.20-8/x64"
    }

    @Test
    @DisplayName("leaves no backslash for CMake to interpret as an escape")
    fun leavesNoBackslashes() {
        val paths = listOf(
            """D:\a\kreate\kreate\example\build\jni\windows-x64\lib""",
            """C:\Program Files\CMake\bin""",
            """\\server\share\includes"""
        )

        paths.forEach { it.toCmakePath() shouldNotContain "\\" }
    }

    @Test
    @DisplayName("is a no-op on paths that already use forward slashes")
    fun leavesUnixPathsAlone() {
        val unixPath = "/usr/lib/jvm/temurin-17"

        unixPath.toCmakePath() shouldBe unixPath
    }

    @Test
    @DisplayName("is idempotent")
    fun isIdempotent() {
        val path = """C:\Users\build\project"""

        path.toCmakePath().toCmakePath() shouldBe path.toCmakePath()
    }

    @Test
    @DisplayName("preserves the drive letter and the segment separator semantics")
    fun preservesDriveLetter() {
        // CMake accepts C:/... on Windows; only the separator changes, never the drive.
        """C:\build""".toCmakePath() shouldBe "C:/build"
    }
}
