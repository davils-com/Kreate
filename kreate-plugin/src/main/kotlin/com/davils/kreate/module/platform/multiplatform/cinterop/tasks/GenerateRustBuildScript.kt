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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task to generate the `build.rs` script for the Rust project.
 *
 * This task creates a build script that uses `cbindgen` to generate C headers
 * from Rust source code during the Cargo build process.
 *
 * @since 1.0.0
 */
@DisableCachingByDefault(because = "Build script generation is conditional and depends on external state")
public abstract class GenerateRustBuildScript : Task(
    "Generates the build script for the Rust project.",
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
     * The name of the Rust project.
     * @since 1.0.0
     */
    @get:Input
    public abstract val projectName: Property<String>

    private val script: String
        get() = """
            extern crate cbindgen;

            use std::env;
            use cbindgen::Language::C;

            fn main() {
                let crate_dir = env::var("CARGO_MANIFEST_DIR").unwrap();

                cbindgen::Builder::new()
                    .with_crate(crate_dir)
                    .with_language(C)
                    .generate()
                    .expect("Unable to generate bindings")
                    .write_to_file("include/${projectName.get()}.h");
            }
        """.trimIndent()

    /**
     * The output file representing the generated `build.rs`.
     * @since 1.0.0
     */
    @get:OutputFile
    public val outputFile: File
        get() = workDir.get().asFile.resolve(BUILD_RUST_FILE_NAME)

    /**
     * Executes the task to generate the build script.
     *
     * @since 1.0.0
     */
    @TaskAction
    public fun execute() {
        val buildRsFile = workDir.get().asFile.resolve(BUILD_RUST_FILE_NAME)
        if (!buildRsFile.exists()) {
            buildRsFile.createNewFile()
            buildRsFile.writeText(script)
            return
        }

        if (!isFileEmpty(buildRsFile)) {
            return
        }
        buildRsFile.writeText(script)
    }

    private fun isFileEmpty(buildRsFile: File): Boolean = buildRsFile.length() == 0L

    /**
     * Companion object for [GenerateRustBuildScript].
     * @since 1.0.0
     */
    public companion object {
        /**
         * The name of the Rust build script file.
         * @since 1.0.0
         */
        public const val BUILD_RUST_FILE_NAME: String = "build.rs"
    }
}
