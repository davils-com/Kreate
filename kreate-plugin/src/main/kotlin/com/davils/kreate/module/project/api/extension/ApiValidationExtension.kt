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

package com.davils.kreate.module.project.api.extension

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * Extension for configuring binary compatibility validation.
 *
 * When enabled, Kreate records the project's public binary interface in a checked-in
 * `.api` file and fails the build when a change is not reflected there. The dump is read
 * out of the compiled classes with ASM, so no additional Gradle plugin has to be applied,
 * and it is written in the same format the Kotlin `binary-compatibility-validator` plugin
 * uses — an existing dump can be carried over unchanged.
 *
 * Validation covers JVM bytecode. Kotlin/Native and JavaScript targets produce no class
 * files and are therefore not validated.
 *
 * @since 2.1.0
 */
public abstract class ApiValidationExtension @Inject constructor(factory: ObjectFactory, project: Project) {

    /**
     * Whether binary compatibility validation is enabled for this project.
     *
     * Defaults to `false`, in line with every other Kreate feature: a project that never
     * asked for validation should not suddenly fail on a missing dump file.
     *
     * @since 2.1.0
     */
    public val enabled: Property<Boolean> = factory.property(Boolean::class.java).convention(false)

    /**
     * The directory holding the checked-in dump.
     *
     * Defaults to `api` below the project directory, which is where the
     * `binary-compatibility-validator` plugin puts it.
     *
     * @since 2.1.0
     */
    public val apiDirectory: DirectoryProperty = factory.directoryProperty().convention(
        project.layout.projectDirectory.dir("api")
    )

    /**
     * The file name of the dump inside [apiDirectory].
     *
     * Defaults to the project name with an `.api` suffix.
     *
     * @since 2.1.0
     */
    public val dumpFileName: Property<String> = factory.property(String::class.java)
        .convention("${project.name}.api")

    /**
     * Fully qualified names of annotations that exclude whatever they are applied to.
     *
     * This is how an opt-in or internal API is kept out of the dump without making it
     * `internal` in Kotlin — for example `com.davils.kreate.InternalKreateApi`.
     *
     * @since 2.1.0
     */
    public val nonPublicMarkers: SetProperty<String> = factory.setProperty(String::class.java)

    /**
     * Package names excluded from the dump, including their subpackages.
     *
     * @since 2.1.0
     */
    public val ignoredPackages: SetProperty<String> = factory.setProperty(String::class.java)

    /**
     * Fully qualified names of classes excluded from the dump.
     *
     * @since 2.1.0
     */
    public val ignoredClasses: SetProperty<String> = factory.setProperty(String::class.java)
}
