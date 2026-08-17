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

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File

/**
 * Skips a test class when CMake is not installed.
 *
 * Developers must be able to run the suite without a native toolchain, but CI must not be
 * allowed to pass by silently skipping the native tests. Setting `KREATE_REQUIRE_CMAKE`
 * turns a missing toolchain into a failure instead of a skip, and the CI workflow sets it.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(CmakeAvailableCondition::class)
annotation class EnabledIfCmakeAvailable

/**
 * The [ExecutionCondition] backing [EnabledIfCmakeAvailable].
 */
class CmakeAvailableCondition : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (cmakeAvailable) {
            return ConditionEvaluationResult.enabled("CMake is available")
        }

        check(System.getenv("KREATE_REQUIRE_CMAKE") == null) {
            "CMake is required but was not found on PATH. The native tests must not be " +
                "skipped when KREATE_REQUIRE_CMAKE is set."
        }

        return ConditionEvaluationResult.disabled("CMake is not installed")
    }

    private companion object {
        val cmakeAvailable: Boolean by lazy {
            val names = if (System.getProperty("os.name").lowercase().contains("win")) {
                listOf("cmake.exe", "cmake")
            } else {
                listOf("cmake")
            }

            System.getenv("PATH").orEmpty()
                .split(File.pathSeparatorChar)
                .filter { it.isNotBlank() }
                .any { directory -> names.any { File(directory, it).canExecute() } }
        }
    }
}
