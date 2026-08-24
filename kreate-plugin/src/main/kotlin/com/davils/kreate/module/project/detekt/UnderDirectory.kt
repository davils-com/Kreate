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

package com.davils.kreate.module.project.detekt

import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import java.io.Serializable

/**
 * Matches every file underneath a directory, compared by absolute path.
 *
 * A named class rather than a lambda because it is stored on a task and therefore has to survive
 * the configuration cache. A lambda declared in a plugin would capture its enclosing scope, and
 * Gradle refuses to serialize that.
 *
 * @since 2.3.0
 */
internal class UnderDirectory(private val root: String) : Spec<FileTreeElement>, Serializable {

    /**
     * Whether the given element lies underneath the configured directory.
     *
     * @param element The file tree element being tested.
     * @return `true` when the element's absolute path starts with the directory.
     * @since 2.3.0
     */
    override fun isSatisfiedBy(element: FileTreeElement): Boolean =
        element.file.absolutePath.startsWith(root)

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
