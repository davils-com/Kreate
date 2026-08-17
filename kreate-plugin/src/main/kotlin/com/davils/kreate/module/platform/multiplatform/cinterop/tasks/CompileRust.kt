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
import com.davils.kreate.module.platform.multiplatform.cinterop.resolveCargoCommand
import com.davils.kreate.module.platform.multiplatform.cinterop.resolveRustTargets
import com.davils.kreate.tooling.runExternalTool
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Task to compile Rust code for C-interop.
 *
 * This task executes the `cargo build` command for each specified Rust target,
 * producing static libraries that can be used by Kotlin/Native.
 *
 * @param exec The executive operations used to run external commands.
 * @since 1.0.0
 */
@DisableCachingByDefault(because = "Rust compilation depends on external environment and tools")
public abstract class CompileRust @Inject constructor(
    /**
     * The executive operations instance.
     * @since 1.0.0
     */
    private val exec: ExecOperations
) : Task("Compile Rust code for C interop", "kreate c-interoperation") {
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
     * The native sources the build compiles.
     *
     * Declared so that Gradle re-runs the compilation when they change. A task that declares
     * outputs but no relevant inputs is considered up to date as long as its outputs are
     * untouched, which would leave an edited source file silently uncompiled and the previous
     * binary in place while the build reported success.
     *
     * @since 2.0.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val nativeSources: ConfigurableFileCollection

    /**
     * The list of Rust targets to compile for.
     * @since 1.0.0
     */
    @get:Input
    @get:Optional
    public abstract val rustTargets: ListProperty<String>

    /**
     * The output directory where compiled artifacts are stored.
     * @since 1.0.0
     */
    @get:OutputDirectory
    public val outputDir: File
        get() = workDir.get().asFile.resolve("target")

    /**
     * Executes the task to compile Rust code for the configured targets.
     *
     * @throws GradleException If compilation fails for any target.
     * @since 1.0.0
     */
    @TaskAction
    public fun execute() {
        val targets = resolveRustTargets(rustTargets)
        val cargoCmd = resolveCargoCommand()

        for (target in targets) {
            runExternalTool(
                exec = exec,
                logger = logger,
                description = "Cargo build for target '$target'",
                workingDirectory = workDir.get().asFile,
                arguments = listOf(cargoCmd, "build", "--target", target, "--release")
            )
        }
    }
}
