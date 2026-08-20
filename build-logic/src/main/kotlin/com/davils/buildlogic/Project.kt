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

package com.davils.buildlogic

/**
 * Contains project-wide constants and configuration values.
 *
 * This object provides centralized access to project identity, organization details,
 * version control information, legal information, and issue management details.
 *
 * @since 1.0.0
 */
public object Project {
    /**
     * Contains constants related to project identity.
     *
     * @since 1.0.0
     */
    public object Identity {
        /**
         * The name of the project.
         *
         * @since 1.0.0
         */
        public const val NAME: String = "Kreate"

        /**
         * The description of the project.
         *
         * @since 1.0.0
         */
        public const val DESCRIPTION: String = "A helper plugin for setting up enterprise-grade Gradle Kotlin projects."

        /**
         * The group ID for the project.
         *
         * @since 1.0.0
         */
        public const val GROUP: String = "com.davils"

        /**
         * The year the project was started.
         *
         * @since 1.0.0
         */
        public const val INCEPTION_YEAR: Int = 2025
    }

    /**
     * Contains the toolchain and runtime compatibility guarantees of the published plugin.
     *
     * These values are the single source of truth for the build conventions, the functional
     * test matrix, and the compatibility section of the documentation. They must only be
     * raised in a major release, because raising them locks out consumers.
     *
     * @since 2.0.0
     */
    public object Compatibility {
        /**
         * The bytecode target of the published plugin.
         *
         * Deliberately pinned to the minimum JVM supported by Gradle 9 so that consumers
         * running on JDK 17 are not locked out. The build itself may use a newer toolchain
         * to compile against this target.
         *
         * @since 2.0.0
         */
        public const val TARGET_JAVA_VERSION: Int = 17

        /**
         * The lowest Gradle version the plugin is tested against.
         *
         * Functional tests run the full feature matrix against this version to catch
         * accidental use of newer Gradle APIs.
         *
         * @since 2.0.0
         */
        public const val MIN_GRADLE_VERSION: String = "9.0"
    }

    /**
     * Contains the quality thresholds the build is verified against.
     *
     * @since 2.1.0
     */
    public object Quality {
        /**
         * The minimum percentage of lines the plugin's own test suite has to cover.
         *
         * Derived from a measurement rather than chosen as a target: a threshold set before the
         * first `koverLog` either breaks the build the day coverage is switched on, or sits so
         * far below the real figure that it can never fail — and a gate that cannot fail reads
         * like protection without being any.
         *
         * This value is a ratchet. Raise it when a change raises the measurement; never lower it
         * to turn a red build green. It sits a point or so below the current measurement on
         * purpose: `check` runs on all three platforms of the CI matrix and the native functional
         * tests do not all take the same paths on each, so a bound pinned to the exact figure
         * would turn ordinary cross-platform variance into a red build.
         *
         * It also understates the real figure. The `functionalTest` suite drives Gradle builds
         * through TestKit, which run in a separate daemon process, and Kover instruments the
         * test JVM rather than the process it starts. Plugin code exercised only by TestKit
         * therefore does not count towards this number.
         *
         * @since 2.2.0
         */
        public const val MINIMUM_LINE_COVERAGE: Int = 40
    }

    /**
     * Contains constants related to the organization.
     *
     * @since 1.0.0
     */
    public object Organization {
        /**
         * The name of the organization.
         *
         * @since 1.0.0
         */
        public const val NAME: String = "Davils"

        /**
         * The email address for the organization.
         *
         * @since 1.0.0
         */
        public const val EMAIL: String = "development@davils.com"

        /**
         * The website URL for the organization.
         *
         * @since 1.0.0
         */
        public const val WEBSITE_URL: String = "https://www.davils.com"

        /**
         * The timezone for the organization.
         *
         * @since 1.0.0
         */
        public const val TIMEZONE: String = "Europe/Berlin"
    }

    /**
     * Contains constants related to version control and continuous integration.
     *
     * @since 1.0.0
     */
    public object VersionControl {
        /**
         * The name of the CI system used for the project.
         *
         * @since 1.0.0
         */
        public const val CI_SYSTEM: String = "Github Actions"

        /**
         * The URL of the CI system.
         *
         * @since 1.0.0
         */
        public const val CI_URL: String = "https://github.com/davils-com/kreate/actions"

        /**
         * The connection URL for the source control management system.
         *
         * @since 1.0.0
         */
        public const val SCM_CONNECTION: String = "scm:git:https://github.com/davils-com/kreate.git"

        /**
         * The developer connection URL for the source control management system.
         *
         * @since 1.0.0
         */
        public const val SCM_DEVELOPER_CONNECTION: String = "scm:git:ssh://git@github.com:davils-com/kreate.git"

        /**
         * The public URL of the source control management system.
         *
         * @since 1.0.0
         */
        public const val SCM_URL: String = "https://github.com/davils-com/kreate.git"
    }

    /**
     * Contains constants related to legal information and licensing.
     *
     * @since 1.0.0
     * @author Nils Jaekel
     */
    public object Legal {
        /**
         * The name of the project's license.
         *
         * @since 1.0.0
         */
        public const val LICENSE_NAME: String = "Apache 2.0"

        /**
         * The URL to the license text.
         *
         * @since 1.0.0
         */
        public const val LICENSE_URL: String = "https://github.com/davils-com/kreate/blob/main/LICENSE"

        /**
         * The distribution mode of the license.
         *
         * @since 1.0.0
         */
        public const val LICENSE_DISTRIBUTION: String = "repo"
    }

    /**
     * Contains constants related to issue management and tracking.
     *
     * @since 1.0.0
     */
    public object IssueManagement {
        /**
         * The name of the issue management system.
         *
         * @since 1.0.0
         */
        public const val SYSTEM: String = "Github Issues"

        /**
         * The URL to the issue tracker.
         *
         * @since 1.0.0
         */
        public const val URL: String = "https://github.com/davils-com/kreate/issues"
    }
}
