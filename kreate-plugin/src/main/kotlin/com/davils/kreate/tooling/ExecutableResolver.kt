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

import com.davils.kreate.system.OsTarget
import com.davils.kreate.system.getOs
import java.io.File

/**
 * Locates the executables of the external tools Kreate invokes.
 *
 * Resolution happens in a deliberate order so that a build is both predictable and
 * overridable:
 *
 * 1. An explicit path configured through the DSL, which always wins.
 * 2. The directories on the `PATH` environment variable.
 * 3. The tool's conventional installation directories ([ExternalTool.wellKnownDirectories]
 *    and [ExternalTool.homeRelativeDirectories]).
 * 4. The bare command name, so that the operating system still gets a chance to resolve it
 *    and the resulting error message names the tool rather than a made-up path.
 *
 * Steps 3 and 4 exist because the Gradle daemon does not necessarily inherit the `PATH` of
 * the user's interactive shell. This is most visible for IDE launched builds on macOS, but
 * the same applies to minimal CI containers.
 *
 * @since 2.0.0
 */
internal object ExecutableResolver {
    /**
     * Resolves the executable to invoke for the given tool.
     *
     * @param tool The tool to locate.
     * @param override An explicit executable path configured by the user, if any. When it
     *   is set it is returned unchanged, even if the file does not exist, so that the user
     *   sees a failure naming their own configuration instead of a silent fallback.
     * @return The command or absolute path to pass to the process builder.
     * @since 2.0.0
     */
    fun resolve(tool: ExternalTool, override: String? = null): String =
        override?.takeIf { it.isNotBlank() }
            ?: findOnPath(tool)?.absolutePath
            ?: findInWellKnownDirectories(tool)?.absolutePath
            ?: tool.commandName

    /**
     * Searches the `PATH` environment variable for the tool.
     *
     * On Windows the `PATHEXT` extensions are appended to the command name, because the
     * executable is `cmake.exe` rather than `cmake`.
     *
     * @param tool The tool to locate.
     * @return The executable file, or `null` when it is not on the `PATH`.
     * @since 2.0.0
     */
    private fun findOnPath(tool: ExternalTool): File? {
        val pathValue = System.getenv("PATH") ?: return null
        val candidateNames = candidateNames(tool)

        return pathValue.split(File.pathSeparatorChar)
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { directory -> candidateNames.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    /**
     * Searches the tool's conventional installation directories.
     *
     * @param tool The tool to locate.
     * @return The executable file, or `null` when none of the directories contain it.
     * @since 2.0.0
     */
    private fun findInWellKnownDirectories(tool: ExternalTool): File? {
        val userHome = System.getProperty("user.home")
        val directories = tool.wellKnownDirectories.map(::File) +
            tool.homeRelativeDirectories.mapNotNull { relative ->
                userHome?.let { File(it, relative) }
            }
        val candidateNames = candidateNames(tool)

        return directories.asSequence()
            .flatMap { directory -> candidateNames.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    /**
     * Builds the list of file names the tool may have on the current operating system.
     *
     * @param tool The tool to build the names for.
     * @return The command name, plus its Windows executable variants where applicable.
     * @since 2.0.0
     */
    private fun candidateNames(tool: ExternalTool): List<String> {
        val os by getOs()
        if (os != OsTarget.WINDOWS) return listOf(tool.commandName)

        val extensions = System.getenv("PATHEXT")
            ?.split(File.pathSeparatorChar)
            ?.filter { it.isNotBlank() }
            ?: listOf(".EXE", ".CMD", ".BAT")

        return extensions.map { tool.commandName + it.lowercase() } + tool.commandName
    }
}
