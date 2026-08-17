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

import java.io.File

/**
 * Converts a filesystem path into the form CMake expects.
 *
 * A backslash is an escape character in the CMake language, so a native Windows path handed to
 * CMake through `-D` is re-parsed as escape sequences the moment a module expands the variable.
 * With a JDK under `C:\hostedtoolcache\...`, `FindJNI` fails outright:
 *
 * ```
 * CMake Error at FindJNI.cmake:291 (foreach):
 *   Syntax error ... Invalid character escape '\h'.
 * ```
 *
 * Forward slashes are accepted on every platform CMake supports — which is why CMake's own
 * documentation and generated files use them throughout, including on Windows.
 *
 * @return The path with backslashes replaced by forward slashes.
 * @since 2.0.0
 */
internal fun String.toCmakePath(): String = replace('\\', '/')

/**
 * Returns the absolute path of this file in the form CMake expects.
 *
 * @return The absolute path with forward slashes.
 * @since 2.0.0
 */
internal fun File.toCmakePath(): String = absolutePath.toCmakePath()
