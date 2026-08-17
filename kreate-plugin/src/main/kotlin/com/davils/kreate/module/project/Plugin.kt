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

package com.davils.kreate.module.project

import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin

/**
 * Applies the optional default Gradle plugins, when the consumer opted in.
 *
 * Only the Kotlin serialization compiler plugin falls into this category. It is opt-in
 * because a compiler plugin is not free, and until 2.0.0 every Kreate project paid for it
 * whether or not it serialized anything.
 *
 * @param projectExtension The project configuration extension.
 * @return Unit
 * @since 1.0.0
 */
internal fun Project.applyDefaultGradlePlugins(projectExtension: ProjectExtension) {
    if (!projectExtension.applySerializationPlugin.get()) return

    pluginManager.apply(SerializationGradleSubplugin::class)
}
