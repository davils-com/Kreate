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

package com.davils.kreate.module.platform.jvm.jni

import com.davils.kreate.KreateExtension
import com.davils.kreate.module.platform.resolveFeatureProjectName
import com.davils.kreate.tooling.ExecutableResolver
import com.davils.kreate.tooling.ExternalTool
import org.gradle.api.Project
import java.io.File

/**
 * Resolves the name for the JNI native project.
 *
 * Delegates to the shared feature-name resolver, using [JniExtension.nameOverride].
 *
 * @param extension The Kreate configuration extension.
 * @return The resolved and sanitized project name as a string.
 * @since 1.1.0
 */
internal fun Project.resolveProjectName(extension: KreateExtension): String {
    val jniConfig = extension.platform.jvm.jni
    return resolveFeatureProjectName(extension, jniConfig.nameOverride)
}

/**
 * Resolves the root directory for JNI native sources.
 *
 * Defaults to `<projectDir>/jni` when [JniExtension.projectDirectory] is absent.
 *
 * @param jniConfig The JNI configuration extension.
 * @return The resolved root [File] directory.
 * @since 1.1.0
 */
internal fun Project.resolveRootDir(jniConfig: JniExtension): File {
    if (jniConfig.projectDirectory.isPresent) {
        return projectDir.resolve(jniConfig.projectDirectory.get().asFile)
    }
    return projectDir.resolve("jni")
}

/**
 * Resolves the path to the CMake executable.
 *
 * Delegates to the shared [ExecutableResolver], which searches the `PATH` first and falls
 * back to the conventional installation directories on every platform.
 *
 * @param override An explicit executable path configured through the JNI DSL, if any.
 * @return The resolved CMake command path or name as a string.
 * @since 1.1.0
 */
internal fun resolveCmakeCommand(override: String? = null): String =
    ExecutableResolver.resolve(ExternalTool.CMAKE, override)
