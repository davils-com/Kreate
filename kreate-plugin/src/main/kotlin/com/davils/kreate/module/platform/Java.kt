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

package com.davils.kreate.module.platform

import com.davils.kreate.KreateExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/**
 * Configures the Java plugin extension for the project.
 *
 * This function sets the source and target compatibility based on the
 * [PlatformExtension] configuration.
 *
 * Does nothing when no Java plugin is applied. A Kotlin Multiplatform project without a JVM or
 * Android target has no `JavaPluginExtension` at all, and there is nothing to set on it - the JVM
 * toolchain is pinned on the Kotlin extension instead, which every target arrangement has.
 * Configuring it unconditionally would fail such a build during evaluation.
 *
 * @param extension The Kreate configuration extension.
 * @since 1.0.0
 */
internal fun Project.configureJava(extension: KreateExtension) {
    val platformExtension = extension.platform
    val java = extensions.findByType(JavaPluginExtension::class.java) ?: return

    val javaVersion = platformExtension.javaVersion.get()
    java.sourceCompatibility = javaVersion
    java.targetCompatibility = javaVersion
}
