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

package com.davils.kreate.jobs

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Configures the project to execute a specific task before every Kotlin compilation task.
 *
 * The task is passed as a [TaskProvider] and never realized here, so that registering a
 * Kreate feature does not force the task to be created in builds that do not run it.
 *
 * @param task The provider of the task that must run before compilation.
 * @return Unit
 * @since 1.0.0
 */
internal fun Project.executeTaskBeforeCompile(task: TaskProvider<out Task>) {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        dependsOn(task)
    }
}
