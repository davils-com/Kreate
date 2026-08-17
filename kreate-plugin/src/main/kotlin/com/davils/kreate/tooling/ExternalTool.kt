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

private const val HOMEBREW_BIN = "/opt/homebrew/bin"
private const val LOCAL_BIN = "/usr/local/bin"
private const val SYSTEM_BIN = "/usr/bin"

/**
 * The directories a command line tool is conventionally installed in on a Unix system.
 *
 * @since 2.0.0
 */
private val UNIX_BIN_DIRECTORIES = listOf(HOMEBREW_BIN, LOCAL_BIN, SYSTEM_BIN)

/**
 * An external command line tool that Kreate shells out to during a build.
 *
 * Each entry carries the locations the tool is conventionally installed in, so that it can
 * still be found when the Gradle daemon was started without the user's interactive shell
 * `PATH` — the usual situation for IDE launched builds on macOS.
 *
 * @since 2.0.0
 */
internal enum class ExternalTool(
    /**
     * The bare command name, used both for the `PATH` lookup and as the last-resort value.
     *
     * @since 2.0.0
     */
    val commandName: String,
    /**
     * Absolute directories that are searched when the command is not on the `PATH`.
     *
     * @since 2.0.0
     */
    val wellKnownDirectories: List<String>,
    /**
     * Directories relative to the user's home directory that are searched after
     * [wellKnownDirectories].
     *
     * @since 2.0.0
     */
    val homeRelativeDirectories: List<String> = emptyList()
) {
    /**
     * The CMake build system generator, used by the JNI and C/C++ C-interop pipelines.
     *
     * @since 2.0.0
     */
    CMAKE(
        commandName = "cmake",
        wellKnownDirectories = UNIX_BIN_DIRECTORIES +
            listOf(
                "/Applications/CMake.app/Contents/bin",
                "C:\\Program Files\\CMake\\bin"
            )
    ),

    /**
     * The Cargo build tool, used by the Rust C-interop pipeline.
     *
     * @since 2.0.0
     */
    CARGO(
        commandName = "cargo",
        wellKnownDirectories = UNIX_BIN_DIRECTORIES,
        homeRelativeDirectories = listOf(".cargo/bin")
    ),

    /**
     * The Trivy scanner, used by the security and compliance tasks.
     *
     * @since 2.0.0
     */
    TRIVY(
        commandName = "trivy",
        wellKnownDirectories = UNIX_BIN_DIRECTORIES
    )
}
