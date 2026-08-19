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

package com.davils.kreate.module.project.api.tasks

import com.davils.kreate.KreateTasks
import com.davils.kreate.jobs.Task
import com.davils.kreate.module.project.api.AbiExtractor
import com.davils.kreate.module.project.api.AbiFilterOptions
import com.davils.kreate.module.project.api.AbiRenderer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes the project's public binary interface to a checked-in `.api` file.
 *
 * The dump is the record a reviewer reads: a diff against it is the only place where the
 * removal of a public method, a widened return type or a newly exposed class is visible
 * as such, rather than buried in the source diff that caused it.
 *
 * @since 2.1.0
 */
@CacheableTask
public abstract class ApiDump : Task(
    "Writes the public binary interface of this project to its .api dump.",
    KreateTasks.ApiValidation.GROUP
) {
    /**
     * The compiled class directories the dump is extracted from.
     *
     * @since 2.1.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val classDirectories: ConfigurableFileCollection

    /**
     * Fully qualified names of annotations that exclude the declarations they mark.
     *
     * @since 2.1.0
     */
    @get:Input
    public abstract val nonPublicMarkers: SetProperty<String>

    /**
     * Package names excluded from the dump, including their subpackages.
     *
     * @since 2.1.0
     */
    @get:Input
    public abstract val ignoredPackages: SetProperty<String>

    /**
     * Fully qualified names of classes excluded from the dump.
     *
     * @since 2.1.0
     */
    @get:Input
    public abstract val ignoredClasses: SetProperty<String>

    /**
     * The file the dump is written to.
     *
     * @since 2.1.0
     */
    @get:OutputFile
    public abstract val dumpFile: RegularFileProperty

    /**
     * Extracts the binary interface and writes it to [dumpFile].
     *
     * @return Unit
     * @since 2.1.0
     */
    @TaskAction
    public fun execute() {
        val rendered = AbiRenderer.render(
            AbiExtractor.extract(
                classFiles = readClassFiles(classDirectories),
                options = AbiFilterOptions(
                    nonPublicMarkers = nonPublicMarkers.get(),
                    ignoredPackages = ignoredPackages.get(),
                    ignoredClasses = ignoredClasses.get()
                )
            )
        )

        val target = dumpFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(rendered)

        logger.lifecycle("Wrote the public binary interface to ${target.path}.")
    }
}
