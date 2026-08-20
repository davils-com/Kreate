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

package com.davils.kreate.module.project.coverage

import com.davils.kreate.Kreate
import com.davils.kreate.KreateExtension
import com.davils.kreate.module.project.coverage.extension.CoverageExtension
import io.kotest.matchers.shouldBe
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit as KoverCoverageUnit

/**
 * Tests for the coverage extension defaults and the mapping onto Kover's own model.
 *
 * The defaults are the published contract, and the mapping is the place where a Kover upgrade
 * that renamed an enum constant would otherwise go unnoticed until a consumer's build broke.
 */
@DisplayName("CoverageExtension")
class CoverageExtensionTest {

    private fun coverage(): CoverageExtension {
        val project: Project = ProjectBuilder.builder().withName("sample").build()
        project.pluginManager.apply(Kreate::class.java)
        return project.extensions.getByType(KreateExtension::class.java).project.coverage
    }

    @Nested
    @DisplayName("defaults")
    inner class Defaults {

        @Test
        @DisplayName("the integration is off until it is asked for")
        fun disabledByDefault() {
            coverage().enabled.get() shouldBe false
        }

        @Test
        @DisplayName("measurement uses Kover's own engine rather than JaCoCo")
        fun koverEngineByDefault() {
            val extension = coverage()

            extension.useJacoco.get() shouldBe false
            extension.jacocoVersion.isPresent shouldBe false
        }

        @Test
        @DisplayName("no report is generated during check")
        fun reportsAreNotOnCheck() {
            // Generating reports nobody reads on every local `check` is pure latency; CI asks
            // for the ones it needs by name.
            val reports = coverage().reports

            reports.xml.onCheck.get() shouldBe false
            reports.html.onCheck.get() shouldBe false
            reports.log.onCheck.get() shouldBe false
            reports.binary.onCheck.get() shouldBe false
        }

        @Test
        @DisplayName("verification does run during check, and fails rather than warns")
        fun verificationIsOnCheckAndFatal() {
            // The gate is the one part that has to run unasked — a threshold nobody invokes is
            // a threshold nobody finds out about.
            val verify = coverage().verify

            verify.runOnCheck.get() shouldBe true
            verify.warningInsteadOfFailure.get() shouldBe false
        }

        @Test
        @DisplayName("no coverage bound is set")
        fun boundsAreUnset() {
            // A bound picked before the first measurement either breaks the build on day one or
            // sits below the real figure forever. Unset means no rule at all, not a rule of zero.
            val verify = coverage().verify

            verify.minLineCoverage.isPresent shouldBe false
            verify.minBranchCoverage.isPresent shouldBe false
            verify.minInstructionCoverage.isPresent shouldBe false
        }

        @Test
        @DisplayName("no named rules are registered")
        fun noNamedRules() {
            coverage().verify.rules.isEmpty() shouldBe true
        }

        @Test
        @DisplayName("aggregation is off, and names no projects")
        fun aggregationIsOff() {
            val aggregate = coverage().aggregate

            aggregate.enabled.get() shouldBe false
            aggregate.projects.get() shouldBe emptyList()
        }

        @Test
        @DisplayName("the log format matches the documented GitLab coverage expression")
        fun logFormatIsCiParsable() {
            // GitLab reads the headline percentage by matching a regular expression against the
            // job log. The format and that expression are one contract; see CI-Integration.md.
            coverage().reports.log.format.get() shouldBe "<entity> line coverage: <value>%"
        }

        @Test
        @DisplayName("reports land where the documentation says they do")
        fun reportLocations() {
            val reports = coverage().reports

            reports.xml.file.get().asFile.path.endsWith("reports/kover/report.xml") shouldBe true
            reports.html.directory.get().asFile.path.endsWith("reports/kover/html") shouldBe true
        }

        @Test
        @DisplayName("nothing is filtered, excluded or left uninstrumented")
        fun noFiltersByDefault() {
            val extension = coverage()

            extension.filters.excludes.classes.get() shouldBe emptyList()
            extension.filters.includes.classes.get() shouldBe emptyList()
            extension.sources.excludedSourceSets.get() shouldBe emptyList()
            extension.instrumentation.excludedClasses.get() shouldBe emptyList()
            extension.instrumentation.disabledForAll.get() shouldBe false
        }
    }

    @Nested
    @DisplayName("named rules")
    inner class NamedRules {

        @Test
        @DisplayName("a rule keeps the name it was registered under")
        fun ruleKeepsItsName() {
            // The name is what the failure message shows, which is the whole reason rules
            // are named rather than indexed.
            val verify = coverage().verify
            verify.rules.create("No untested class")

            verify.rules.getByName("No untested class").name shouldBe "No untested class"
        }

        @Test
        @DisplayName("a rule inherits the grouping unless it sets one")
        fun ruleGroupingIsUnsetByDefault() {
            val rule = coverage().verify.rules.create("Rule")

            rule.groupBy.isPresent shouldBe false
            rule.disabled.get() shouldBe false
            rule.bounds.get() shouldBe emptyList()
        }

        @Test
        @DisplayName("minBound records the value, unit and aggregation it was given")
        fun minBoundRecordsItsArguments() {
            val rule = coverage().verify.rules.create("Rule")
            rule.minBound(70, CoverageUnit.BRANCH, Aggregation.COVERED_PERCENTAGE)

            val bound = rule.bounds.get().single()
            bound.min.get() shouldBe 70
            bound.max.isPresent shouldBe false
            bound.unit.get() shouldBe CoverageUnit.BRANCH
            bound.aggregation.get() shouldBe Aggregation.COVERED_PERCENTAGE
        }

        @Test
        @DisplayName("maxBound caps rather than floors")
        fun maxBoundRecordsItsArguments() {
            // The case a percentage cannot express: an absolute ceiling on untested code that
            // does not drift upwards as the codebase grows.
            val rule = coverage().verify.rules.create("Rule")
            rule.maxBound(100, CoverageUnit.LINE, Aggregation.MISSED_COUNT)

            val bound = rule.bounds.get().single()
            bound.max.get() shouldBe 100
            bound.min.isPresent shouldBe false
            bound.aggregation.get() shouldBe Aggregation.MISSED_COUNT
        }

        @Test
        @DisplayName("a rule accumulates every bound added to it")
        fun boundsAccumulate() {
            val rule = coverage().verify.rules.create("Rule")
            rule.minBound(80, CoverageUnit.LINE)
            rule.minBound(70, CoverageUnit.BRANCH)

            rule.bounds.get().size shouldBe 2
        }
    }

    @Nested
    @DisplayName("mapping onto Kover")
    inner class Mapping {

        @Test
        @DisplayName("every coverage unit maps to a Kover unit of the same name")
        fun coverageUnitsMap() {
            CoverageUnit.entries.forEach { unit ->
                KoverCoverageUnit.valueOf(unit.name).name shouldBe unit.name
            }
        }

        @Test
        @DisplayName("every aggregation maps to a Kover aggregation type of the same name")
        fun aggregationsMap() {
            Aggregation.entries.forEach { aggregation ->
                AggregationType.valueOf(aggregation.name).name shouldBe aggregation.name
            }
        }

        @Test
        @DisplayName("every grouping maps to a Kover grouping entity of the same name")
        fun groupingsMap() {
            Grouping.entries.forEach { grouping ->
                GroupingEntityType.valueOf(grouping.name).name shouldBe grouping.name
            }
        }
    }
}
