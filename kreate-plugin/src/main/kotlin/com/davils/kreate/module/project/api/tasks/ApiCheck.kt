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
import com.davils.kreate.module.project.api.AbiDiff
import com.davils.kreate.module.project.api.AbiExtractor
import com.davils.kreate.module.project.api.AbiFilterOptions
import com.davils.kreate.module.project.api.AbiRenderer
import com.davils.kreate.module.project.api.readAbiDump
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when the project's public binary interface no longer matches its
 * checked-in `.api` dump.
 *
 * @since 2.1.0
 */
@CacheableTask
public abstract class ApiCheck : Task(
    "Verifies that the public binary interface matches the checked-in .api dump.",
    KreateTasks.ApiValidation.GROUP
) {
    /**
     * The compiled class directories the current interface is extracted from.
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
     * The checked-in dump to compare against, as a collection holding at most one file.
     *
     * A collection rather than a `RegularFileProperty` because the dump legitimately does
     * not exist before the first [ApiDump] run, and `@InputFile` fails the build on a
     * missing file before the task action ever runs. Reporting the absence here instead
     * lets the message say what to do about it.
     *
     * @since 2.1.0
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val expectedDumpFile: ConfigurableFileCollection

    /**
     * The path of the task that regenerates the dump, used in the failure message.
     *
     * @since 2.1.0
     */
    @get:Input
    public abstract val dumpTaskPath: Property<String>

    /**
     * The path of the project being validated, used in the failure message.
     *
     * Carried as an input rather than read from `project` at execution time, because
     * reaching for the project model inside a task action is what makes a task
     * incompatible with the configuration cache.
     *
     * @since 2.1.0
     */
    @get:Input
    public abstract val projectPath: Property<String>

    /**
     * The file the extracted interface is written to for inspection after a failure.
     *
     * @since 2.1.0
     */
    @get:OutputFile
    public abstract val actualDumpFile: RegularFileProperty

    /**
     * Compares the extracted interface against the checked-in dump.
     *
     * @return Unit
     * @throws GradleException When the dump is missing or out of date.
     * @since 2.1.0
     */
    @TaskAction
    public fun execute() {
        val actual = AbiRenderer.render(
            AbiExtractor.extract(
                classFiles = readClassFiles(classDirectories),
                options = AbiFilterOptions(
                    nonPublicMarkers = nonPublicMarkers.get(),
                    ignoredPackages = ignoredPackages.get(),
                    ignoredClasses = ignoredClasses.get()
                )
            )
        )

        val actualFile = actualDumpFile.get().asFile
        actualFile.parentFile.mkdirs()
        actualFile.writeText(actual)

        val expectedFile = expectedDumpFile.files.singleOrNull()
        if (expectedFile == null || !expectedFile.isFile) {
            throw GradleException(
                listOf(
                    "No binary interface dump has been recorded for project " +
                        "'${projectPath.get()}' yet.",
                    "",
                    "Create it and commit the result:",
                    "",
                    "    ./gradlew ${dumpTaskPath.get()}"
                ).joinToString("\n")
            )
        }

        val diff = AbiDiff.render(expected = readAbiDump(expectedFile), actual = actual)
            ?: return

        // Assembled line by line rather than as a raw string: `trimIndent` measures the
        // indentation of the interpolated result, so the diff's own unindented lines would
        // stop every surrounding line from being trimmed.
        throw GradleException(
            listOf(
                "The public binary interface of project '${projectPath.get()}' changed.",
                "",
                diff,
                "",
                "If the change is intended, record it and commit the result:",
                "",
                "    ./gradlew ${dumpTaskPath.get()}",
                "",
                "Expected: ${expectedFile.path}",
                "Actual:   ${actualFile.path}"
            ).joinToString("\n")
        )
    }
}
