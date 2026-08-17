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

package com.davils.kreate

/**
 * The names of every task Kreate registers.
 *
 * Task names are part of the plugin's public contract — users type them, and CI pipelines
 * hard-code them. Collecting them in one place is what keeps that contract reviewable, and
 * what made it possible to notice that the 1.x names followed three different conventions
 * at once (`kreate-jni-build`, `kreate-c-interop-compile`, `trivyScan`).
 *
 * All names follow the same scheme in 2.0.0: the `kreate` prefix, then the feature, then
 * the action, in camel case.
 *
 * @since 2.0.0
 */
public object KreateTasks {
    /**
     * The task group shared by every Kreate task, used as a prefix for feature groups.
     *
     * @since 2.0.0
     */
    public const val GROUP: String = "kreate"

    /**
     * Names of the JNI pipeline tasks.
     *
     * @since 2.0.0
     */
    public object Jni {
        /**
         * Scaffolds the native C++ project. Was `kreate-jni-initialize` in 1.x.
         * @since 2.0.0
         */
        public const val INITIALIZE: String = "kreateJniInitialize"

        /**
         * Generates JNI headers from the compiled classes. New in 2.0.0.
         * @since 2.0.0
         */
        public const val HEADERS: String = "kreateJniHeaders"

        /**
         * Runs the CMake configure step. New in 2.0.0; was part of `kreate-jni-build`.
         * @since 2.0.0
         */
        public const val CONFIGURE: String = "kreateJniConfigure"

        /**
         * Builds the native library. Was `kreate-jni-build` in 1.x.
         * @since 2.0.0
         */
        public const val BUILD: String = "kreateJniBuild"

        /**
         * Generates the runtime loader for packaged natives. New in 2.0.0.
         * @since 2.0.0
         */
        public const val LOADER: String = "kreateJniLoader"

        /**
         * The task group for JNI tasks.
         * @since 2.0.0
         */
        public const val GROUP: String = "kreate jni"
    }

    /**
     * Names of the C-interop pipeline tasks.
     *
     * @since 2.0.0
     */
    public object CInterop {
        /**
         * Scaffolds the native project. Was `kreate-c-interop-initialize` in 1.x.
         * @since 2.0.0
         */
        public const val INITIALIZE: String = "kreateCInteropInitialize"

        /**
         * Adds the Rust dependencies. Was `kreate-c-interop-dependencies` in 1.x.
         * @since 2.0.0
         */
        public const val DEPENDENCIES: String = "kreateCInteropDependencies"

        /**
         * Configures Cargo. Was `kreate-c-interop-configure` in 1.x.
         * @since 2.0.0
         */
        public const val CONFIGURE: String = "kreateCInteropConfigure"

        /**
         * Generates the Rust build script. Was `kreate-c-interop-script` in 1.x.
         * @since 2.0.0
         */
        public const val SCRIPT: String = "kreateCInteropScript"

        /**
         * Compiles the native sources. Was `kreate-c-interop-compile` in 1.x.
         * @since 2.0.0
         */
        public const val COMPILE: String = "kreateCInteropCompile"

        /**
         * Generates the `.def` files. Was `kreate-c-interop-definitions` in 1.x.
         * @since 2.0.0
         */
        public const val DEFINITIONS: String = "kreateCInteropDefinitions"

        /**
         * The task group for C-interop tasks.
         * @since 2.0.0
         */
        public const val GROUP: String = "kreate c-interop"
    }

    /**
     * Names of the Trivy scan tasks.
     *
     * @since 2.0.0
     */
    public object Trivy {
        /**
         * Runs every enabled scan. Was `trivyScan` in 1.x.
         * @since 2.0.0
         */
        public const val SCAN: String = "kreateTrivyScan"

        /**
         * Scans for hard-coded secrets. Was `trivySecretScan` in 1.x.
         * @since 2.0.0
         */
        public const val SECRETS: String = "kreateTrivySecretScan"

        /**
         * Scans dependency licences. Was `trivyLicenseScan` in 1.x.
         * @since 2.0.0
         */
        public const val LICENSES: String = "kreateTrivyLicenseScan"

        /**
         * Scans dependencies for known vulnerabilities. Was `trivyVulnerabilityScan` in 1.x.
         * @since 2.0.0
         */
        public const val VULNERABILITIES: String = "kreateTrivyVulnerabilityScan"

        /**
         * The task group for Trivy tasks.
         * @since 2.0.0
         */
        public const val GROUP: String = "kreate trivy"
    }

    /**
     * Name of the build constants generation task.
     *
     * Was `kreate-build-constants` in 1.x.
     *
     * @since 2.0.0
     */
    public const val BUILD_CONSTANTS: String = "kreateBuildConstants"

    /**
     * The task group for build constant tasks.
     *
     * @since 2.0.0
     */
    public const val BUILD_CONSTANTS_GROUP: String = "kreate build-constants"
}
