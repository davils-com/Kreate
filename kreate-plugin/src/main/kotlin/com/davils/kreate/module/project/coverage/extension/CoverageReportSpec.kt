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

package com.davils.kreate.module.project.coverage.extension

import com.davils.kreate.module.project.coverage.Aggregation
import com.davils.kreate.module.project.coverage.CoverageUnit
import com.davils.kreate.module.project.coverage.Grouping
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * The settings every coverage report format shares.
 *
 * There is deliberately no `enabled` flag. A coverage report task always exists and can always be
 * invoked by name; the only thing a build can actually decide is whether it runs on its own as
 * part of `check`, which is what [onCheck] expresses. A flag that claimed to disable a task you
 * can still run would be a lie in the DSL.
 *
 * @param factory The object factory used for creating properties.
 * @since 2.2.0
 */
public abstract class CoverageReportSpec @Inject constructor(
    /**
     * The object factory instance.
     * @since 2.2.0
     */
    factory: ObjectFactory
) {
    /**
     * Whether this report is generated as part of the `check` task.
     *
     * Defaults to `false`. Generating every report on every `check` slows down the inner
     * development loop for output nobody reads locally; CI asks for the reports it needs by name.
     * The verification gate is the exception — see [CoverageVerifyExtension.runOnCheck].
     *
     * @since 2.2.0
     */
    public val onCheck: Property<Boolean> = factory.property(Boolean::class.java).convention(false)
}

/**
 * The XML coverage report.
 *
 * The XML is emitted in JaCoCo's format, which is what makes it readable by external tooling —
 * GitLab consumes it directly as `coverage_format: jacoco`, and no conversion step is needed.
 *
 * @param factory The object factory used for creating properties.
 * @param project The project instance used to resolve paths.
 * @since 2.2.0
 */
public abstract class CoverageXmlReportSpec @Inject constructor(
    factory: ObjectFactory,
    project: Project
) : CoverageReportSpec(factory) {
    /**
     * The file the XML report is written to.
     *
     * Defaults to `build/reports/kover/report.xml`.
     *
     * @since 2.2.0
     */
    public val file: RegularFileProperty = factory.fileProperty().convention(
        project.layout.buildDirectory.file("reports/kover/report.xml")
    )

    /**
     * The title recorded in the report.
     *
     * Unset by default, which leaves the coverage engine's own title in place.
     *
     * @since 2.2.0
     */
    public val title: Property<String> = factory.property(String::class.java)
}

/**
 * The HTML coverage report.
 *
 * @param factory The object factory used for creating properties.
 * @param project The project instance used to resolve paths.
 * @since 2.2.0
 */
public abstract class CoverageHtmlReportSpec @Inject constructor(
    factory: ObjectFactory,
    project: Project
) : CoverageReportSpec(factory) {
    /**
     * The directory the HTML report is written to.
     *
     * Defaults to `build/reports/kover/html`.
     *
     * @since 2.2.0
     */
    public val directory: DirectoryProperty = factory.directoryProperty().convention(
        project.layout.buildDirectory.dir("reports/kover/html")
    )

    /**
     * The title shown in the generated pages.
     *
     * Unset by default, which leaves the coverage engine's own title in place.
     *
     * @since 2.2.0
     */
    public val title: Property<String> = factory.property(String::class.java)

    /**
     * The character set the pages are written in.
     *
     * Unset by default.
     *
     * @since 2.2.0
     */
    public val charset: Property<String> = factory.property(String::class.java)
}

/**
 * The coverage summary printed to the build log.
 *
 * This is the report a CI system reads. GitLab extracts the coverage percentage for its badge and
 * merge request widget by matching a regular expression against the job log, so the shape of
 * [format] is part of the pipeline's contract rather than a cosmetic choice.
 *
 * @param factory The object factory used for creating properties.
 * @since 2.2.0
 */
public abstract class CoverageLogReportSpec @Inject constructor(
    factory: ObjectFactory
) : CoverageReportSpec(factory) {
    /**
     * The line printed for every measured entity.
     *
     * `<entity>` and `<value>` are substituted. Defaults to
     * `<entity> line coverage: <value>%`, which the GitLab regular expression documented under
     * CI integration matches — changing it means changing that regular expression too.
     *
     * @since 2.2.0
     */
    public val format: Property<String> =
        factory.property(String::class.java).convention("<entity> line coverage: <value>%")

    /**
     * A line printed once before the measurements.
     *
     * Unset by default.
     *
     * @since 2.2.0
     */
    public val header: Property<String> = factory.property(String::class.java)

    /**
     * The entity one line is printed for.
     *
     * Defaults to [Grouping.APPLICATION], which prints a single line — the one a CI regular
     * expression is meant to match.
     *
     * @since 2.2.0
     */
    public val groupBy: Property<Grouping> =
        factory.property(Grouping::class.java).convention(Grouping.APPLICATION)

    /**
     * The unit the printed value is measured in.
     *
     * Defaults to [CoverageUnit.LINE].
     *
     * @since 2.2.0
     */
    public val coverageUnit: Property<CoverageUnit> =
        factory.property(CoverageUnit::class.java).convention(CoverageUnit.LINE)

    /**
     * How the measurements of a group are aggregated into the printed value.
     *
     * Defaults to [Aggregation.COVERED_PERCENTAGE].
     *
     * @since 2.2.0
     */
    public val aggregation: Property<Aggregation> =
        factory.property(Aggregation::class.java).convention(Aggregation.COVERED_PERCENTAGE)
}

/**
 * The binary coverage report.
 *
 * Binary reports are intermediate coverage data rather than something to read. They exist to be
 * fed to the command line tooling or merged into a report produced elsewhere.
 *
 * @param factory The object factory used for creating properties.
 * @since 2.2.0
 */
public abstract class CoverageBinaryReportSpec @Inject constructor(
    factory: ObjectFactory
) : CoverageReportSpec(factory) {
    /**
     * The file the binary report is written to.
     *
     * Unset by default, which leaves the coverage engine's own location in place.
     *
     * @since 2.2.0
     */
    public val file: RegularFileProperty = factory.fileProperty()
}
