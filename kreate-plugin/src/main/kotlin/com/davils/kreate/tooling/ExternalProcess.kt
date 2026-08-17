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

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Runs an external build tool and surfaces its full output when it fails.
 *
 * The diagnostics that explain *why* a native build failed — a missing header, an unresolved
 * symbol, an unusable compiler — are written to the process's standard output and error streams.
 * They are captured here and attached to the thrown exception, because an exit code on its own
 * gives the user nothing to act on.
 *
 * @param exec The executive operations used to start the process.
 * @param logger The logger that receives the output of a successful invocation.
 * @param description A short label for the step, used in the failure message.
 * @param workingDirectory The directory the process is started in.
 * @param arguments The full command line, starting with the executable.
 * @param environment Extra environment variables exported to the process.
 * @return The captured output of the successful invocation.
 * @throws GradleException If the process terminates with a non-zero exit code.
 * @since 2.0.0
 */
internal fun runExternalTool(
    exec: ExecOperations,
    logger: Logger,
    description: String,
    workingDirectory: File,
    arguments: List<String>,
    environment: Map<String, String> = emptyMap()
): String {
    val output = ByteArrayOutputStream()
    val result = exec.exec {
        workingDir = workingDirectory
        commandLine(arguments)
        environment.forEach { (key, value) -> environment(key, value) }
        standardOutput = output
        errorOutput = output
        isIgnoreExitValue = true
    }

    val text = output.toString(Charsets.UTF_8)

    if (result.exitValue != 0) {
        throw GradleException(
            buildString {
                appendLine("$description failed with exit code ${result.exitValue}.")
                appendLine()
                appendLine("Command:           ${arguments.joinToString(" ")}")
                appendLine("Working directory: ${workingDirectory.absolutePath}")
                environment.forEach { (key, value) -> appendLine("$key: $value") }
                appendLine()
                appendLine("Output:")
                append(text.trimEnd())
            }
        )
    }

    logger.info(text)
    return text
}
