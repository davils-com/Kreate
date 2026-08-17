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

/**
 * Adds the default repositories to the project, when the consumer opted in.
 *
 * Repositories are deliberately not added by default. A build that resolves through an
 * internal mirror declares its repositories centrally in settings and often forbids
 * project-level ones outright; silently adding Maven Central there either warns, fails, or
 * — worst of all — bypasses the mirror without anyone noticing.
 *
 * @param projectExtension The project configuration extension.
 * @return Unit
 * @since 1.0.0
 */
internal fun Project.addRepositories(projectExtension: ProjectExtension) {
    if (!projectExtension.applyDefaultRepositories.get()) return

    repositories.apply {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
