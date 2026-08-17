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
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Scaffolds a native C++ JNI project.
 *
 * The task creates `<projectRoot>/src` and writes a minimal `CMakeLists.txt` plus a
 * placeholder translation unit, so that the very first build has something to compile.
 * Existing files are never overwritten: once scaffolded, the CMake project belongs to the
 * user.
 *
 * @since 1.1.0
 */
@DisableCachingByDefault(because = "Scaffolding runs once and deliberately preserves existing files")
public abstract class InitializeCppProject : Task(
    "Generates a new native C++ JNI project.",
    "kreate jni"
) {
    /**
     * The name of the native project.
     *
     * Used as the CMake `project(...)` name, as the `add_library` target name, and
     * therefore as the name passed to `System.loadLibrary`.
     *
     * @since 1.1.0
     */
    @get:Input
    public abstract val projectName: Property<String>

    /**
     * The native project root that is created and populated.
     *
     * Declared as the only output. The previous version additionally declared the parent
     * directory as an input, which nested the output inside the input and made Gradle's
     * up-to-date check meaningless.
     *
     * @since 1.1.0
     */
    @get:OutputDirectory
    public abstract val projectRoot: DirectoryProperty

    /**
     * Creates the project structure and the initial CMake and source files when missing.
     *
     * @return Unit
     * @throws GradleException If the source directory cannot be created.
     * @since 1.1.0
     */
    @TaskAction
    public fun execute() {
        val name = projectName.get()
        val root = projectRoot.get().asFile
        val sourceDirectory = root.resolve(SRC_DIR_NAME)

        if (!sourceDirectory.isDirectory && !sourceDirectory.mkdirs()) {
            throw GradleException("Failed to create the JNI source directory: ${sourceDirectory.absolutePath}")
        }

        val cMakeFile = root.resolve(CMAKE_FILE_NAME)
        if (!cMakeFile.exists()) {
            cMakeFile.writeText(defaultCMakeContent(name))
            logger.lifecycle("Created ${cMakeFile.absolutePath}.")
        } else if (!cMakeFile.readText().contains(INCLUDE_DIRS_VARIABLE)) {
            logger.warn(
                "${cMakeFile.absolutePath} does not reference \${$INCLUDE_DIRS_VARIABLE}. " +
                    "Add it to target_include_directories(...) so that generated JNI headers and " +
                    "the configured libraryIncludePaths are visible to the compiler."
            )
        }

        val placeholder = sourceDirectory.resolve("$name.cpp")
        if (!placeholder.exists()) {
            placeholder.writeText(defaultSourceContent(name))
            logger.lifecycle("Created ${placeholder.absolutePath}.")
        }
    }

    /**
     * Renders the default `CMakeLists.txt` for a freshly scaffolded project.
     *
     * Include directories are consumed from the [INCLUDE_DIRS_VARIABLE] cache variable
     * rather than being baked in. That way a change to `libraryIncludePaths`, and the
     * directory holding the generated JNI headers, take effect without the user having to
     * edit a generated file that Kreate deliberately never rewrites.
     *
     * @param projectName The CMake project and library target name.
     * @return The rendered CMake script.
     * @since 1.1.0
     */
    private fun defaultCMakeContent(projectName: String): String {
        val sourcesVariable = "${projectName.uppercase()}_SOURCES"
        return """
            cmake_minimum_required(VERSION 3.20)
            project($projectName CXX)
            set(CMAKE_CXX_STANDARD 17)
            set(CMAKE_CXX_STANDARD_REQUIRED ON)
            set(CMAKE_POSITION_INDEPENDENT_CODE ON)

            find_package(JNI REQUIRED)

            # CONFIGURE_DEPENDS re-evaluates the glob on every build system invocation, so a
            # newly added source file is picked up without a manual reconfigure.
            file(GLOB $sourcesVariable CONFIGURE_DEPENDS "src/*.cpp" "src/*.cc")

            add_library($projectName SHARED ${'$'}{$sourcesVariable})
            target_include_directories($projectName PRIVATE
                ${'$'}{JNI_INCLUDE_DIRS}
                include
                ${'$'}{$INCLUDE_DIRS_VARIABLE}
            )
            target_link_libraries($projectName PRIVATE ${'$'}{JNI_LIBRARIES})
        """.trimIndent()
    }

    /**
     * Renders the placeholder translation unit.
     *
     * @param projectName The native project name, used in the comment.
     * @return The rendered C++ source.
     * @since 1.1.0
     */
    private fun defaultSourceContent(projectName: String): String = """
        #include <jni.h>

        // Placeholder source for JNI project "$projectName".
        //
        // Run `gradle kreateJniHeaders` to generate declarations for every `external`
        // function in this module, then include the generated header here and implement
        // the declared functions.
    """.trimIndent()

    /**
     * Companion object for [InitializeCppProject].
     *
     * @since 1.1.0
     */
    public companion object {
        /**
         * The CMake file name.
         * @since 1.1.0
         */
        public const val CMAKE_FILE_NAME: String = "CMakeLists.txt"

        /**
         * The source directory name inside the native project.
         * @since 1.1.0
         */
        public const val SRC_DIR_NAME: String = "src"

        /**
         * The CMake cache variable Kreate passes its include directories through.
         * @since 2.0.0
         */
        public const val INCLUDE_DIRS_VARIABLE: String = "KREATE_JNI_INCLUDE_DIRS"
    }
}
