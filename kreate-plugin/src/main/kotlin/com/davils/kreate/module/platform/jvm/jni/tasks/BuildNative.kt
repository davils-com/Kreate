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

package com.davils.kreate.module.platform.jvm.jni.tasks

import com.davils.kreate.jobs.Task
import com.davils.kreate.module.platform.jvm.jni.resolveCmakeCommand
import com.davils.kreate.tooling.toCmakePath
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Builds the native JNI library with CMake.
 *
 * Every file the native build reads is declared as an input. That is not a formality: a
 * task that declares outputs but no relevant inputs is considered up to date by Gradle as
 * long as its outputs are untouched, which means edits to C++ sources produce no rebuild
 * and the JVM keeps loading a stale shared library. Declaring the sources, the generated
 * headers and the CMake cache is what makes the incremental behaviour correct.
 *
 * @param exec The executive operations used to run external commands.
 * @since 1.1.0
 */
@DisableCachingByDefault(because = "Native artifacts are tied to the local toolchain and are not relocatable")
public abstract class BuildNative @Inject constructor(
    /**
     * The executive operations instance.
     * @since 1.1.0
     */
    private val exec: ExecOperations
) : Task("Builds the native JNI library with CMake.", "kreate jni") {
    /**
     * The native sources and headers the build compiles.
     * @since 2.0.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val nativeSources: ConfigurableFileCollection

    /**
     * The CMake cache produced by [ConfigureNative].
     *
     * Depending on the cache file rather than on the whole scratch directory keeps the two
     * tasks free of overlapping outputs, since CMake writes into that directory during the
     * build as well.
     *
     * @since 2.0.0
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract val cmakeCache: RegularFileProperty

    /**
     * The CMake build type. Defaults to `Release`.
     * @since 1.1.0
     */
    @get:Input
    @get:Optional
    public abstract val buildType: Property<String>

    /**
     * An explicit CMake executable path, if one is configured.
     * @since 2.0.0
     */
    @get:Input
    @get:Optional
    public abstract val cmakeExecutable: Property<String>

    /**
     * The home directory of the JDK the native code is compiled against.
     * @since 2.0.0
     */
    @get:Input
    public abstract val javaHome: Property<String>

    /**
     * The scratch directory CMake generated its build system into.
     * @since 2.0.0
     */
    @get:Internal
    public abstract val cmakeBuildDirectory: DirectoryProperty

    /**
     * The directory containing the compiled shared library.
     * @since 1.1.0
     */
    @get:OutputDirectory
    public abstract val libraryOutputDirectory: DirectoryProperty

    /**
     * Builds the native library.
     *
     * @return Unit
     * @since 1.1.0
     */
    @TaskAction
    public fun execute() {
        val type = buildType.getOrElse("Release")

        runCmake(
            exec = exec,
            logger = logger,
            phase = "build",
            workingDirectory = cmakeBuildDirectory.get().asFile,
            javaHome = javaHome.get().toCmakePath(),
            arguments = listOf(
                resolveCmakeCommand(cmakeExecutable.orNull),
                "--build",
                cmakeBuildDirectory.get().asFile.toCmakePath(),
                "--config",
                type
            )
        )
    }
}
