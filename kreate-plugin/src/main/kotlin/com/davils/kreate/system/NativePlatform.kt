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

import org.gradle.api.GradleException

/**
 * Returns the identifier of the platform the build is currently running on.
 *
 * The value has the form `<os>-<arch>`, for example `linux-x64` or `macos-arm64`. It is
 * used to keep native build outputs of different platforms apart, both inside the Gradle
 * build directory and inside a packaged JAR. Without that separation, a shared build cache
 * or a checkout mounted into containers of differing architectures would silently mix
 * incompatible binaries.
 *
 * @return The platform identifier.
 * @throws GradleException If the operating system cannot be determined.
 * @since 2.0.0
 */
internal fun currentPlatformId(): String {
    val os by getOs()
    val arch by getArchitecture()

    val osId = when (os) {
        OsTarget.WINDOWS -> "windows"
        OsTarget.LINUX -> "linux"
        OsTarget.MACOS -> "macos"
        OsTarget.UNKNOWN -> throw GradleException(
            "Cannot determine the native platform: unsupported operating system " +
                "'${System.getProperty("os.name")}'."
        )
    }

    val archId = when (arch) {
        Architecture.ARM64 -> "arm64"
        Architecture.X64 -> "x64"
    }

    return "$osId-$archId"
}

/**
 * Returns the file name a shared library with the given base name has on this platform.
 *
 * CMake follows the platform conventions (`libfoo.so`, `libfoo.dylib`, `foo.dll`), so the
 * packaging and loading code has to reproduce them to find the produced artifact.
 *
 * @param baseName The library name as passed to CMake's `add_library` and to
 *   `System.loadLibrary`, without prefix or extension.
 * @return The platform specific shared library file name.
 * @throws GradleException If the operating system cannot be determined.
 * @since 2.0.0
 */
internal fun sharedLibraryFileName(baseName: String): String {
    val os by getOs()

    return when (os) {
        OsTarget.WINDOWS -> "$baseName.dll"
        OsTarget.MACOS -> "lib$baseName.dylib"
        OsTarget.LINUX -> "lib$baseName.so"
        OsTarget.UNKNOWN -> throw GradleException(
            "Cannot determine the shared library naming convention: unsupported operating " +
                "system '${System.getProperty("os.name")}'."
        )
    }
}
