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

package com.davils.kreate.module.platform.multiplatform.cinterop

import com.davils.kreate.KreateExtension
import com.davils.kreate.KreateTasks
import com.davils.kreate.jobs.executeTaskBeforeCompile
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.AddRustDependencies
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.CompileNative
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.CompileRust
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.ConfigureCargo
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.GenerateDefinitionFiles
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.GenerateRustBuildScript
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.InitializeNativeProject
import com.davils.kreate.module.platform.multiplatform.cinterop.tasks.InitializeRustProject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

/**
 * Initializes the C-interop configuration for the project.
 *
 * This function sets up the necessary tasks and applies native targets
 * if C-interop is enabled in the extension.
 *
 * @param extension The Kreate configuration extension.
 * @since 1.0.0
 */
internal fun Project.initializeCInterop(extension: KreateExtension) {
    val cInteropConfig = extension.platform.multiplatform.cInterop
    if (!cInteropConfig.enabled.get()) return

    addCInteropTasks(extension)
    applyNativeTargets(cInteropConfig)
}

private fun Project.addCInteropTasks(extension: KreateExtension) {
    val cInteropConfig = extension.platform.multiplatform.cInterop
    when (cInteropConfig.language.get()) {
        NativeLanguage.RUST -> addRustCInteropTasks(extension)
        NativeLanguage.C, NativeLanguage.CPP -> addNativeCInteropTasks(extension)
    }
}

private fun Project.addRustCInteropTasks(extension: KreateExtension) {
    val projectName = resolveProjectName(extension)

    val cInteropConfig = extension.platform.multiplatform.cInterop
    val projectRootDir = resolveRootDir(cInteropConfig)

    val rustProject = projectRootDir.resolve(projectName)
    val initializeRustProject = tasks.register<InitializeRustProject>(KreateTasks.CInterop.INITIALIZE) {
        this.workDir.set(projectRootDir)
        this.projectName.set(projectName)
    }

    val addRustDependencies = tasks.register<AddRustDependencies>(KreateTasks.CInterop.DEPENDENCIES) {
        workDir.set(rustProject)
        dependsOn(initializeRustProject)
    }

    val configureCargo = tasks.register<ConfigureCargo>(KreateTasks.CInterop.CONFIGURE) {
        workDir.set(rustProject)
        dependsOn(addRustDependencies)
    }

    val generateRustBuildScript = tasks.register<GenerateRustBuildScript>(KreateTasks.CInterop.SCRIPT) {
        workDir.set(rustProject)
        this.projectName.set(projectName)
        dependsOn(configureCargo)
    }

    val compileRust = tasks.register<CompileRust>(KreateTasks.CInterop.COMPILE) {
        workDir.set(rustProject)
        nativeSources.from(
            fileTree(rustProject) {
                include("Cargo.toml", "build.rs", "src/**")
            }
        )
        if (cInteropConfig.rustTargets.isPresent) {
            rustTargets.set(cInteropConfig.rustTargets)
        }
        dependsOn(generateRustBuildScript)
    }

    val generateDefinitionFiles = tasks.register<GenerateDefinitionFiles>(KreateTasks.CInterop.DEFINITIONS) {
        workDir.set(rustProject)
        this.rootDir.set(projectRootDir)
        this.projectName.set(projectName)
        this.defFileName.set(cInteropConfig.defFiles.fileName)
        this.defDirName.set(cInteropConfig.defFiles.dirName)
        this.language.set(cInteropConfig.language)
        if (cInteropConfig.rustTargets.isPresent) {
            rustTargets.set(cInteropConfig.rustTargets)
        }
        dependsOn(compileRust)
    }

    tasks.withType<CInteropProcess>().configureEach {
        dependsOn(generateDefinitionFiles)
    }
    executeTaskBeforeCompile(generateDefinitionFiles)
}

private fun Project.addNativeCInteropTasks(extension: KreateExtension) {
    val projectName = resolveProjectName(extension)

    val cInteropConfig = extension.platform.multiplatform.cInterop
    val projectRootDir = resolveRootDir(cInteropConfig)

    val nativeProject = projectRootDir.resolve(projectName)
    val initializeNativeProject = tasks.register<InitializeNativeProject>(KreateTasks.CInterop.INITIALIZE) {
        this.workDir.set(projectRootDir)
        this.projectName.set(projectName)
        this.language.set(cInteropConfig.language)
    }

    val compileNative = tasks.register<CompileNative>(KreateTasks.CInterop.COMPILE) {
        this.workDir.set(nativeProject)
        nativeSources.from(
            fileTree(nativeProject) {
                include("CMakeLists.txt", "src/**", "include/**")
            }
        )
        dependsOn(initializeNativeProject)
    }

    val generateDefinitionFiles = tasks.register<GenerateDefinitionFiles>(KreateTasks.CInterop.DEFINITIONS) {
        this.workDir.set(nativeProject)
        this.rootDir.set(projectRootDir)
        this.projectName.set(projectName)
        this.defFileName.set(cInteropConfig.defFiles.fileName)
        this.defDirName.set(cInteropConfig.defFiles.dirName)
        this.language.set(cInteropConfig.language)
        dependsOn(compileNative)
    }

    tasks.withType<CInteropProcess>().configureEach {
        dependsOn(generateDefinitionFiles)
    }
    executeTaskBeforeCompile(generateDefinitionFiles)
}

private fun Project.applyNativeTargets(cInteropConfig: CInteropExtension) {
    val targets = resolveRustTargets(cInteropConfig.rustTargets)
    configure<KotlinMultiplatformExtension> {
        for (rustTarget in targets) {
            when {
                rustTarget.contains("x86_64-pc-windows") || rustTarget.contains("mingw") -> {
                    mingwX64 {
                        configureCInterop(this@applyNativeTargets)
                        cInteropConfig.mingwConfiguration.invoke(this)
                    }
                }

                rustTarget.contains("aarch64-apple-darwin") -> {
                    macosArm64 {
                        configureCInterop(this@applyNativeTargets)
                        cInteropConfig.macosConfiguration.invoke(this)
                    }
                }

                rustTarget.contains("x86_64-unknown-linux") -> {
                    linuxX64 {
                        configureCInterop(this@applyNativeTargets)
                        cInteropConfig.linuxConfiguration.invoke(this)
                    }
                }

                rustTarget.contains("aarch64-unknown-linux") -> {
                    linuxArm64 {
                        configureCInterop(this@applyNativeTargets)
                        cInteropConfig.linuxConfiguration.invoke(this)
                    }
                }

                else -> {
                    throw GradleException("Unsupported Rust target for Kotlin/Native mapping: $rustTarget")
                }
            }
        }
    }
}
