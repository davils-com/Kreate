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

import com.davils.kreate.tooling.runExternalTool
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.File

/**
 * Runs a single CMake invocation for the JNI pipeline.
 *
 * `JAVA_HOME` is exported into the process environment in addition to being passed as a CMake
 * variable, because `find_package(JNI)` consults the environment variable directly. Without it,
 * CMake resolves against whichever JDK the machine defaults to and silently compiles the native
 * code against different headers than the Kotlin code targets.
 *
 * @param exec The executive operations used to start the process.
 * @param logger The logger that receives the output of a successful invocation.
 * @param phase A short label for the CMake phase, used in the failure message.
 * @param workingDirectory The directory the process is started in.
 * @param javaHome The JDK home directory exported to the process.
 * @param arguments The full command line, starting with the CMake executable.
 * @return Unit
 * @throws GradleException If CMake terminates with a non-zero exit code.
 * @since 2.0.0
 */
internal fun runCmake(
    exec: ExecOperations,
    logger: Logger,
    phase: String,
    workingDirectory: File,
    javaHome: String,
    arguments: List<String>
) {
    runExternalTool(
        exec = exec,
        logger = logger,
        description = "CMake $phase",
        workingDirectory = workingDirectory,
        arguments = arguments,
        environment = mapOf("JAVA_HOME" to javaHome)
    )
}
