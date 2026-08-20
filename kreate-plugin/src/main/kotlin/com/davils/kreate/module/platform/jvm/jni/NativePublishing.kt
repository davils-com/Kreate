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
import com.davils.kreate.KreateTasks
import com.davils.kreate.module.platform.jvm.jni.tasks.VerifyNativePlatforms
import com.davils.kreate.system.currentPlatformId
import com.davils.kreate.system.requireKnownPlatform
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * The project property that overrides the platform selection from the command line.
 *
 * A pipeline that gains a runner should not have to edit the build script to publish another
 * platform, and a pipeline that loses one should be able to drop it for a single release.
 *
 * @since 2.2.0
 */
internal const val PLATFORM_SELECTION_PROPERTY: String = "kreate.jni.publishPlatforms"

/**
 * Where a platform's native library may come from, in the order the directories are searched.
 *
 * @since 2.2.0
 */
internal data class NativeSource(
    /**
     * The platform identifier the sources belong to.
     * @since 2.2.0
     */
    val platformId: String,
    /**
     * The staged directory, if a staging directory was configured.
     * @since 2.2.0
     */
    val staged: Provider<Directory>?,
    /**
     * The directory this build writes to, set only for the host platform.
     * @since 2.2.0
     */
    val hostBuilt: Provider<out Directory>?
)

/**
 * Registers the per-platform JAR, verification and publication wiring.
 *
 * @param extension The Kreate configuration extension.
 * @param projectName The native library base name.
 * @param hostLibraryDir The directory the native build of this machine writes to.
 * @param hostPlatformId The platform this build runs on.
 * @param resourcePath The directory inside the JAR the libraries are filed under.
 * @param buildTaskName The name of the native build task the host JAR must wait for.
 * @since 2.2.0
 */
internal fun Project.configureNativePublishing(
    extension: KreateExtension,
    projectName: String,
    hostLibraryDir: Provider<out Directory>,
    hostPlatformId: String,
    resourcePath: String,
    buildTaskName: String
) {
    val publishing = extension.platform.jvm.jni.packaging.publishing
    val platforms = resolveSelectedPlatforms(publishing, hostPlatformId)
    val sources = platforms.map { platformId ->
        NativeSource(
            platformId = platformId,
            staged = if (publishing.stagingDirectory.isPresent) {
                publishing.stagingDirectory.map { it.dir(platformId) }
            } else {
                null
            },
            hostBuilt = if (platformId == hostPlatformId) hostLibraryDir else null
        )
    }

    val verify = registerVerifyTask(sources, buildTaskName, hostPlatformId)
    val jars = sources.map { source ->
        registerNativeJar(source, projectName, resourcePath, verify, buildTaskName, hostPlatformId)
    }

    tasks.register(KreateTasks.Jni.NATIVE_JARS) {
        group = KreateTasks.Jni.GROUP
        description = "Builds the native JAR of every platform selected for publishing."
        dependsOn(jars)
    }

    registerNativePublications(extension, projectName, sources.map { it.platformId }, jars)
}

/**
 * Resolves which platforms this build publishes.
 *
 * The command line property wins over the build script, the build script over the default of
 * "whatever this machine is".
 *
 * @param publishing The publishing configuration.
 * @param hostPlatformId The platform this build runs on.
 * @return The validated, de-duplicated selection in declaration order.
 * @since 2.2.0
 */
internal fun Project.resolveSelectedPlatforms(
    publishing: JniPublishingExtension,
    hostPlatformId: String
): List<String> {
    val fromProperty = providers.gradleProperty(PLATFORM_SELECTION_PROPERTY).orNull
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }

    val selected = fromProperty
        ?: publishing.platforms.orNull?.takeIf { it.isNotEmpty() }
        ?: listOf(hostPlatformId)

    val origin = if (fromProperty != null) {
        "-P$PLATFORM_SELECTION_PROPERTY"
    } else {
        "jni { packaging { publishing { platforms } } }"
    }

    return selected.distinct().map { requireKnownPlatform(it, origin) }
}

/**
 * Registers the task that checks the selection against what is actually on disk.
 *
 * @param sources The resolved sources per selected platform.
 * @param buildTaskName The native build task, needed when the host platform is selected.
 * @param hostPlatformId The platform this build runs on.
 * @return The registered task.
 * @since 2.2.0
 */
private fun Project.registerVerifyTask(
    sources: List<NativeSource>,
    buildTaskName: String,
    hostPlatformId: String
): TaskProvider<VerifyNativePlatforms> =
    tasks.register<VerifyNativePlatforms>(KreateTasks.Jni.VERIFY_PLATFORMS) {
        if (sources.any { it.platformId == hostPlatformId }) {
            dependsOn(buildTaskName)
        }

        platforms.set(sources.map { it.platformId })
        searchedDirectories.from(sources.flatMap { it.directories() })
        candidatePaths.set(
            sources.map { source ->
                val paths = source.directories()
                    .map { provider -> provider.get().asFile.absolutePath }
                    .joinToString(File.pathSeparator)
                "${source.platformId}=$paths"
            }
        )
    }

/**
 * Registers the JAR carrying one platform's native libraries.
 *
 * @param source The resolved sources for the platform.
 * @param projectName The native library base name.
 * @param resourcePath The directory inside the JAR the libraries are filed under.
 * @param verify The verification task that must run first.
 * @param buildTaskName The native build task, needed for the host platform.
 * @param hostPlatformId The platform this build runs on.
 * @return The registered JAR task.
 * @since 2.2.0
 */
private fun Project.registerNativeJar(
    source: NativeSource,
    projectName: String,
    resourcePath: String,
    verify: TaskProvider<VerifyNativePlatforms>,
    buildTaskName: String,
    hostPlatformId: String
): TaskProvider<Jar> =
    tasks.register<Jar>(KreateTasks.Jni.nativeJar(source.platformId)) {
        group = KreateTasks.Jni.GROUP
        description = "Packages the ${source.platformId} native library of $projectName."

        dependsOn(verify)
        if (source.platformId == hostPlatformId) {
            dependsOn(buildTaskName)
        }

        // A staged binary wins: a pipeline may deliberately publish one built under controlled
        // conditions rather than whatever the publishing runner compiled.
        source.directories().forEach { directory ->
            from(directory) { into("$resourcePath/${source.platformId}") }
        }
        duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE

        archiveBaseName.set(projectName)
        archiveAppendix.set(source.platformId)
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
    }

/**
 * Returns the directories this platform's library may live in, most specific first.
 *
 * @return The candidate directories.
 * @since 2.2.0
 */
internal fun NativeSource.directories(): List<Provider<out Directory>> =
    listOfNotNull(staged, hostBuilt)

/**
 * Returns the platform this build machine is.
 *
 * Extracted so that tests can reason about the selection logic without a real toolchain.
 *
 * @return The host platform identifier.
 * @since 2.2.0
 */
internal fun hostPlatform(): String = currentPlatformId()
