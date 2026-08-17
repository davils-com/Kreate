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

package com.davils.kreate.module.platform.multiplatform.cinterop.tasks

import com.davils.kreate.jobs.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task to configure the Cargo.toml file for static library output.
 *
 * This task appends the necessary `lib` configuration to `Cargo.toml` if it's
 * not already present, ensuring that the Rust project can be built as a
 * static library for C-interop.
 *
 * @since 1.0.0
 */
@DisableCachingByDefault(because = "Cargo configuration has side effects on Cargo.toml")
public abstract class ConfigureCargo : Task(
    "Configure Cargo Toml file for static library output.",
    "kreate c-interoperation"
) {
    /**
     * The native project directory the task writes into.
     *
     * Deliberately not an input: this directory <i>contains</i> the task's own output, so
     * declaring it as an input would nest the output inside the input and make Gradle's
     * up-to-date check meaningless. What the task actually reads is declared separately.
     *
     * @since 1.0.0
     */
    @get:Internal
    public abstract val workDir: DirectoryProperty

    /**
     * The manifest section this task appends to `Cargo.toml`.
     *
     * Exposed as a task input rather than kept private so that Gradle regenerates the manifest
     * when the template changes — for example after a Kreate upgrade. Without an input, a task
     * that has outputs is considered up to date for as long as those outputs are untouched, and
     * the improved template would never reach an existing project.
     *
     * @since 2.0.0
     */
    @get:Input
    public val extendedCargoContent: String
        get() = """
            [lib]
            crate-type = ["staticlib"]
        """.trimIndent()

    /**
     * The output file representing the configured `Cargo.toml`.
     * @since 1.0.0
     */
    @get:OutputFile
    public val outputFile: File
        get() = workDir.get().asFile.resolve(CARGO_TOML_FILE_NAME)

    /**
     * Executes the task to configure `Cargo.toml`.
     *
     * @since 1.0.0
     */
    @TaskAction
    public fun execute() {
        val cargoToml = workDir.get().asFile.resolve(CARGO_TOML_FILE_NAME)
        if (!isValidCargoToml(cargoToml)) {
            return
        }

        if (isContentAlreadyExtended(cargoToml)) {
            return
        }
        writeContentToFile(cargoToml)
    }

    private fun isValidCargoToml(cargoToml: File): Boolean = cargoToml.exists()

    private fun isContentAlreadyExtended(cargoToml: File): Boolean {
        val content = cargoToml.readText()
        return content.contains(extendedCargoContent)
    }

    private fun writeContentToFile(cargoToml: File) {
        val originalContent = cargoToml.readText()
        val newContent = originalContent + extendedCargoContent
        cargoToml.writeText(newContent)
    }

    /**
     * Companion object for [ConfigureCargo].
     * @since 1.0.0
     */
    public companion object {
        /**
         * The name of the Cargo configuration file.
         * @since 1.0.0
         */
        public const val CARGO_TOML_FILE_NAME: String = "Cargo.toml"
    }
}
