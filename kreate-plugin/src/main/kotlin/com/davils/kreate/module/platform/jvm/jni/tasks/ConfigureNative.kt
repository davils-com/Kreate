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
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Runs the CMake configure step for the native JNI project.
 *
 * The step is a task of its own so that it is skipped when only C++ sources changed;
 * re-running CMake's generator is by far the more expensive half of a native build.
 *
 * Two properties of this task matter for correctness:
 *
 * - The CMake build directory lives under Gradle's build directory rather than inside the
 *   source tree. `CMakeCache.txt` records the absolute paths it was generated for, so a
 *   build directory that travels with the sources breaks irrecoverably as soon as the
 *   project is renamed, moved, or checked out at a different path on a CI agent. Placing it
 *   under the build directory also makes `clean` able to reset it.
 * - The JDK is taken from the Gradle toolchain and handed to CMake explicitly. Left to
 *   itself, `find_package(JNI)` picks up whatever JDK the machine happens to default to,
 *   which silently compiles against different headers than the ones the Kotlin code is
 *   compiled for.
 *
 * @param exec The executive operations used to run external commands.
 * @since 2.0.0
 */
@DisableCachingByDefault(because = "CMake configuration is tied to absolute paths and the local toolchain")
public abstract class ConfigureNative @Inject constructor(
    /**
     * The executive operations instance.
     * @since 2.0.0
     */
    private val exec: ExecOperations
) : Task("Runs the CMake configure step for the native JNI project.", "kreate jni") {
    /**
     * The native project root that contains `CMakeLists.txt`.
     *
     * Deliberately not an input: tracking the whole directory would make every C++ edit
     * trigger a reconfiguration, which is the expensive half of a native build. What the
     * generated build system actually depends on is [cmakeListsFile]; newly added source
     * files are picked up by the `CONFIGURE_DEPENDS` glob during the build step.
     *
     * @since 2.0.0
     */
    @get:Internal
    public abstract val sourceDirectory: DirectoryProperty

    /**
     * The `CMakeLists.txt` that describes the native project.
     *
     * @since 2.0.0
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val cmakeListsFile: RegularFileProperty

    /**
     * The absolute source and build directory paths the generated build system is bound to.
     *
     * `CMakeCache.txt` records the paths it was generated for and refuses to be reused
     * from anywhere else — the failure is a hard `CMake Error: The current CMakeCache.txt
     * directory ... is different ...`. Declaring the paths as an explicit input is what
     * makes Gradle reconfigure after the project is renamed, moved, or checked out at a
     * different path on a build agent, instead of handing CMake a cache it will reject.
     *
     * @since 2.0.0
     */
    @get:Input
    public abstract val cacheBoundPaths: ListProperty<String>

    /**
     * The directory holding the generated JNI headers, added to the include path.
     * @since 2.0.0
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val generatedHeaderDirectory: DirectoryProperty

    /**
     * The CMake build type, for example `Release` or `Debug`.
     * @since 2.0.0
     */
    @get:Input
    public abstract val buildType: Property<String>

    /**
     * The CMake generator passed to `cmake -G`, if one is configured.
     * @since 2.0.0
     */
    @get:Input
    @get:Optional
    public abstract val generator: Property<String>

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
     * Additional include directories forwarded to the compiler.
     * @since 2.0.0
     */
    @get:Input
    public abstract val libraryIncludePaths: ListProperty<String>

    /**
     * The scratch directory CMake generates its build system into.
     * @since 2.0.0
     */
    @get:Internal
    public abstract val cmakeBuildDirectory: DirectoryProperty

    /**
     * The directory the produced shared library is written to.
     * @since 2.0.0
     */
    @get:Internal
    public abstract val libraryOutputDirectory: DirectoryProperty

    /**
     * The generated CMake cache.
     *
     * Declared as the task's output so that Gradle can tell whether the project still
     * needs to be configured, and so that [BuildNative] can depend on it as a file rather
     * than on the whole scratch directory, which CMake also writes into while building.
     *
     * @since 2.0.0
     */
    @get:OutputFile
    public abstract val cmakeCache: RegularFileProperty

    /**
     * Configures the native project with CMake.
     *
     * @return Unit
     * @since 2.0.0
     */
    @TaskAction
    public fun execute() {
        val buildDirectory = cmakeBuildDirectory.get().asFile
        val libraryDirectory = libraryOutputDirectory.get().asFile
        buildDirectory.mkdirs()
        libraryDirectory.mkdirs()

        val type = buildType.get()

        // Every path handed to CMake uses forward slashes. A backslash is an escape character
        // in the CMake language, so a native Windows path is re-parsed as escape sequences the
        // moment a module expands the variable — `FindJNI` fails with "Invalid character escape
        // '\h'" on a JDK under C:\hostedtoolcache.
        val libraryPath = libraryDirectory.toCmakePath()
        val javaHomePath = javaHome.get().toCmakePath()

        val arguments = buildList {
            add(resolveCmakeCommand(cmakeExecutable.orNull))
            add("-S")
            add(sourceDirectory.get().asFile.toCmakePath())
            add("-B")
            add(buildDirectory.toCmakePath())
            generator.orNull?.takeIf { it.isNotBlank() }?.let {
                add("-G")
                add(it)
            }
            add("-DCMAKE_BUILD_TYPE=$type")
            add("-DJAVA_HOME=$javaHomePath")

            // Multi-configuration generators (Visual Studio, Xcode) ignore the plain
            // variable and append the configuration name to the output path. Pinning the
            // per-configuration variables as well is what keeps the artifact in one known
            // place on every platform, which is what java.library.path and the packaging
            // step rely on.
            add("-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$libraryPath")
            add("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$libraryPath")
            for (configuration in CMAKE_CONFIGURATIONS) {
                add("-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_$configuration=$libraryPath")
                add("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_$configuration=$libraryPath")
            }

            val includePaths = buildList {
                generatedHeaderDirectory.orNull?.asFile?.let { add(it.toCmakePath()) }
                addAll(
                    libraryIncludePaths.getOrElse(emptyList())
                        .filter { it.isNotBlank() }
                        .map { it.toCmakePath() }
                )
            }
            if (includePaths.isNotEmpty()) {
                add("-DKREATE_JNI_INCLUDE_DIRS=${includePaths.joinToString(";")}")
            }
        }

        runCmake(
            exec = exec,
            logger = logger,
            phase = "configure",
            workingDirectory = sourceDirectory.get().asFile,
            javaHome = javaHomePath,
            arguments = arguments
        )

        val cache = cmakeCache.get().asFile
        if (!cache.isFile) {
            throw GradleException(
                "CMake reported success but did not write ${cache.absolutePath}. " +
                    "This usually means the configured generator wrote to a different directory."
            )
        }
    }

    /**
     * Companion object for [ConfigureNative].
     *
     * @since 2.0.0
     */
    public companion object {
        /**
         * The configuration names multi-configuration CMake generators support.
         *
         * @since 2.0.0
         */
        public val CMAKE_CONFIGURATIONS: List<String> =
            listOf("DEBUG", "RELEASE", "RELWITHDEBINFO", "MINSIZEREL")
    }
}
