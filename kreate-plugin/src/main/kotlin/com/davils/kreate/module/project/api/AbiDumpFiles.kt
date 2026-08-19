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

package com.davils.kreate.module.project.api

import java.io.File

/**
 * Reads a checked-in `.api` dump with its line endings normalised to `\n`.
 *
 * [AbiRenderer] always emits `\n`, but the dump on disk does not have to: Git rewrites
 * text files to CRLF when it checks them out on Windows, which is the default on the
 * Windows CI images. Comparing the raw bytes would then report an interface change on
 * Windows for a project whose interface did not change, and the diff would be empty
 * because splitting into lines hides the very difference that failed the comparison.
 *
 * @param file The dump to read.
 * @return The dump text with `\r\n` and lone `\r` replaced by `\n`.
 * @since 2.1.1
 */
internal fun readAbiDump(file: File): String =
    file.readText().replace("\r\n", "\n").replace('\r', '\n')
