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

package com.davils.kreate.system

import com.davils.kreate.KreateTasks
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.GradleException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for the closed platform vocabulary.
 *
 * A platform identifier is simultaneously a directory name inside the JAR, the suffix of a
 * published artifact id, and a string the generated loader recomputes at runtime. All three have
 * to agree, which is why the set is closed rather than free text.
 */
@DisplayName("Known platforms")
class KnownPlatformsTest {

    @Test
    @DisplayName("covers every operating system and architecture combination")
    fun vocabularyIsComplete() {
        KNOWN_PLATFORM_IDS shouldBe setOf(
            "windows-x64", "windows-arm64",
            "linux-x64", "linux-arm64",
            "macos-x64", "macos-arm64"
        )
    }

    @Test
    @DisplayName("contains whatever the current machine reports")
    fun hostPlatformIsKnown() {
        // The loader derives its lookup path the same way. If these two ever disagreed, every
        // published artifact would be filed under a name no consumer looks for.
        (currentPlatformId() in KNOWN_PLATFORM_IDS) shouldBe true
    }

    @Test
    @DisplayName("accepts a known identifier unchanged")
    fun acceptsKnownIdentifier() {
        requireKnownPlatform("linux-x64", "test") shouldBe "linux-x64"
    }

    @Test
    @DisplayName("rejects a plausible typo and lists what is supported")
    fun rejectsTypo() {
        // `amd64` is what `os.arch` reports on a Linux JVM, so this is the mistake somebody
        // actually makes — and it would otherwise publish an artifact nobody resolves.
        val failure = assertThrows<GradleException> {
            requireKnownPlatform("linux-amd64", "jni { packaging { publishing { platforms } } }")
        }

        failure.message shouldContain "linux-amd64"
        failure.message shouldContain "linux-x64"
        failure.message shouldContain "jni { packaging { publishing { platforms } } }"
    }

    @Test
    @DisplayName("derives task names in upper camel case")
    fun taskSuffix() {
        platformTaskSuffix("linux-x64") shouldBe "LinuxX64"
        platformTaskSuffix("macos-arm64") shouldBe "MacosArm64"
    }

    @Test
    @DisplayName("the native JAR task name follows the kreate scheme")
    fun nativeJarTaskName() {
        KreateTasks.Jni.nativeJar("linux-x64") shouldBe "kreateJniNativeJarLinuxX64"
    }
}
