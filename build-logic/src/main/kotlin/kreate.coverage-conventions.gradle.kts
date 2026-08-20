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

import com.davils.buildlogic.Project
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    id("org.jetbrains.kotlinx.kover")
}

// Kover is applied here directly rather than through Kreate's own `coverage { }` DSL: this is
// the build that produces the plugin, so it cannot apply the plugin to itself. The consumer
// path is exercised by :example instead.

kover {
    reports {
        total {
            filters {
                excludes {
                    // Task name and project identity constants. They are data, and a coverage
                    // number that counts them measures how many constants exist rather than
                    // how much behaviour is tested.
                    classes("com.davils.kreate.KreateTasks*")
                }
            }

            xml {
                onCheck = false
            }

            html {
                onCheck = false
            }

            log {
                onCheck = false
                // Matched by the GitLab `coverage:` expression documented in
                // docs/topics/CI-Integration.md. Changing it means changing that too.
                format = "<entity> line coverage: <value>%"
            }

            verify {
                onCheck = true

                rule("Minimum line coverage") {
                    // The threshold, its rationale and its known blind spot live on the constant.
                    minBound(Project.Quality.MINIMUM_LINE_COVERAGE, CoverageUnit.LINE)
                }
            }
        }
    }
}
