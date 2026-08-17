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

package com.davils.kreate.module.platform.jvm.jni

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/**
 * Contributes `-Djava.library.path` to a JVM task without eagerly resolving it.
 *
 * Using an argument provider instead of mutating `jvmArgs` at configuration time has two
 * benefits. The value is computed when the task runs, so it survives configuration cache
 * reuse; and the argument is contributed additively, so the provider never has to inspect
 * and rewrite arguments the user set themselves — the previous implementation filtered the
 * existing `jvmArgs` list, which silently discarded any library path a build had
 * configured for its own reasons.
 *
 * @since 2.0.0
 */
internal class JavaLibraryPathArgumentProvider(
    /**
     * The provider of the resolved library path value.
     * @since 2.0.0
     */
    @get:Input
    val libraryPath: Provider<String>
) : CommandLineArgumentProvider {
    /**
     * Returns the JVM argument contributed to the task.
     *
     * @return A single element list holding the `-Djava.library.path` argument.
     * @since 2.0.0
     */
    override fun asArguments(): Iterable<String> = listOf("-Djava.library.path=${libraryPath.get()}")
}
