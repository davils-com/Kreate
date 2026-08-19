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

plugins {
    id("com.vanniktech.maven.publish")
    signing
}

version = System.getenv("CI_COMMIT_TAG")?.removePrefix("v") ?: "2.2.0-SNAPSHOT"
group = Project.Identity.GROUP

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(Project.Identity.GROUP, Project.Identity.NAME.lowercase(), version.toString())

    pom {
        name = Project.Identity.NAME
        description = Project.Identity.DESCRIPTION
        inceptionYear = Project.Identity.INCEPTION_YEAR.toString()
        url = Project.Organization.WEBSITE_URL

        issueManagement {
            system = Project.IssueManagement.SYSTEM
            url = Project.IssueManagement.URL
        }

        ciManagement {
            system = Project.VersionControl.CI_SYSTEM
            url = Project.VersionControl.CI_URL
        }

        licenses {
            license {
                name = Project.Legal.LICENSE_NAME
                url = Project.Legal.LICENSE_URL
                distribution = Project.Legal.LICENSE_DISTRIBUTION
            }
        }

        developers {
            developer {
                id = Project.Organization.NAME.lowercase()
                name = Project.Organization.NAME
                email = Project.Organization.EMAIL
                organization = Project.Organization.NAME
                timezone = Project.Organization.TIMEZONE
            }
        }

        scm {
            url = Project.VersionControl.SCM_URL
            connection = Project.VersionControl.SCM_CONNECTION
            developerConnection = Project.VersionControl.SCM_DEVELOPER_CONNECTION
        }
    }
}
