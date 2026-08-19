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

package com.davils.kreate.module.project.locking

import com.davils.kreate.KreateExtension
import com.davils.kreate.KreateTasks
import org.gradle.api.Project

/**
 * Initializes Gradle dependency locking for the project.
 *
 * Activates locking on the configured classpaths and registers
 * [KreateTasks.DependencyLocking.RESOLVE_AND_LOCK_ALL], which is the task that makes
 * `--write-locks` record every locked classpath in one run.
 *
 * @param extension The main Kreate extension.
 * @since 2.1.0
 */
internal fun Project.initializeDependencyLocking(extension: KreateExtension) {
    val lockingExtension = extension.project.dependencyLocking
    if (!lockingExtension.enabled.get()) return

    // Read once, at configuration time: the task action below must not reach back into the
    // extension, or the task would carry a reference to the whole project model.
    val lockedClasspaths = lockingExtension.lockedClasspaths.get()

    val lockEverything = lockingExtension.lockAllConfigurations.get()
    if (lockEverything) {
        dependencyLocking.lockAllConfigurations()
    } else {
        configurations.matching { it.name in lockedClasspaths }.configureEach {
            resolutionStrategy.activateDependencyLocking()
        }
    }

    registerResolveAndLockAll(if (lockEverything) null else lockedClasspaths)
}

/**
 * Registers the task that resolves every locked classpath.
 *
 * `--write-locks` only records the configurations a build actually resolves, so running it
 * against an arbitrary task writes a partial lock file — one that looks complete and is
 * not. This task exists to resolve all of them in a single invocation.
 *
 * Unlike every other Kreate task this one is a plain `DefaultTask` registered by name
 * rather than a subclass of [com.davils.kreate.jobs.Task]. Resolving a configuration is
 * inherently an execution-time interaction with the project model, which no typed task
 * with declared inputs and outputs can express; `notCompatibleWithConfigurationCache` is
 * the sanctioned way to say so, and it must not be removed in a later cleanup.
 *
 * @param lockedClasspaths The names of the configurations to resolve, or `null` to resolve
 *   every resolvable configuration because all of them are locked.
 * @since 2.1.0
 */
private fun Project.registerResolveAndLockAll(lockedClasspaths: Set<String>?) {
    val isWriteDependencyLocks = gradle.startParameter.isWriteDependencyLocks

    tasks.register(KreateTasks.DependencyLocking.RESOLVE_AND_LOCK_ALL) {
        description = "Resolves the locked classpaths so that --write-locks records every one of them."
        group = KreateTasks.DependencyLocking.GROUP

        notCompatibleWithConfigurationCache("Resolves configurations at execution time.")

        doFirst {
            check(isWriteDependencyLocks) {
                "${KreateTasks.DependencyLocking.RESOLVE_AND_LOCK_ALL} only makes sense with " +
                    "--write-locks: ./gradlew ${KreateTasks.DependencyLocking.RESOLVE_AND_LOCK_ALL} " +
                    "--write-locks"
            }
        }

        doLast {
            // Locking records only what a build actually resolves, so locking every
            // configuration and resolving two of them writes a lock file that looks
            // complete and is not.
            val locked = project.configurations.filter { configuration ->
                configuration.isCanBeResolved &&
                    (lockedClasspaths == null || configuration.name in lockedClasspaths)
            }
            locked.forEach { configuration -> configuration.resolve() }
            logger.lifecycle(
                "Resolved ${locked.size} locked configuration(s): ${locked.joinToString { it.name }}"
            )
        }
    }
}
