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

package com.davils.kreate.module

import org.gradle.api.Project

/**
 * The version used when neither the environment variable nor the project property
 * provides one.
 *
 * @since 2.0.0
 */
internal const val FALLBACK_VERSION: String = "1.0.0"

/**
 * Determines the version for a project.
 *
 * The CI tag environment variable wins, then the project property; if neither yields a
 * usable value the build falls back to [FALLBACK_VERSION]. That fallback is logged rather
 * than applied silently, because a release accidentally published as `1.0.0` is both easy
 * to cause and impossible to take back from a public repository.
 *
 * @param env The environment variable name to check for the version.
 * @param prop The project property name to check for the version.
 * @return The resolved version string.
 * @since 1.0.0
 */
internal fun Project.getProjectVersion(env: String, prop: String): String {
    val fromEnvironment = System.getenv(env)?.takeIf { it.isNotBlank() }
    val fromProperty = findProperty(prop)?.toString()
        ?.takeIf { it.isNotBlank() && it != "unspecified" }

    val resolved = fromEnvironment ?: fromProperty
    if (resolved != null) return resolved

    logger.warn(
        "Kreate could not resolve a version for project '$path': environment variable " +
            "'$env' is unset and project property '$prop' is missing or 'unspecified'. " +
            "Falling back to $FALLBACK_VERSION."
    )
    return FALLBACK_VERSION
}
