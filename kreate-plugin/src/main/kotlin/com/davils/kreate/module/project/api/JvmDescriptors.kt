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

package com.davils.kreate.module.project.api

/**
 * Splits JVM method descriptors into their parts.
 *
 * Needed to recognise the bridge Kotlin generates for a function with default arguments:
 * the bridge's descriptor is the original one with an `int` mask and an `Object` marker
 * appended, and reconstructing the original is the only way to tell whether the function
 * behind the bridge is one that belongs in the dump.
 *
 * @since 2.1.0
 */
internal object JvmDescriptors {
    /**
     * Extracts the parameter types of a method descriptor.
     *
     * @param descriptor A method descriptor such as `(ILjava/lang/String;)V`.
     * @return The parameter type descriptors in declaration order, empty for a descriptor
     *   that does not start with a parameter list or that is malformed.
     * @since 2.1.0
     */
    fun parameterTypes(descriptor: String): List<String> {
        val close = descriptor.indexOf(')')
        if (!descriptor.startsWith("(") || close < 0) return emptyList()

        val types = mutableListOf<String>()
        var index = 1
        var malformed = false
        while (index < close && !malformed) {
            val end = typeEnd(descriptor, index, close)
            if (end <= index) {
                malformed = true
            } else {
                types += descriptor.substring(index, end)
                index = end
            }
        }
        return if (malformed) emptyList() else types
    }

    /**
     * Extracts the return type of a method descriptor.
     *
     * @param descriptor A method descriptor.
     * @return The return type descriptor, or the empty string when there is no parameter list.
     * @since 2.1.0
     */
    fun returnType(descriptor: String): String {
        val close = descriptor.indexOf(')')
        return if (close < 0) "" else descriptor.substring(close + 1)
    }

    /**
     * Finds the end index of the type starting at [start].
     *
     * @param descriptor The descriptor being scanned.
     * @param start The index the type starts at.
     * @param limit The index the parameter list ends at.
     * @return The index just past the type, or [start] when the descriptor is malformed.
     * @since 2.1.0
     */
    private fun typeEnd(descriptor: String, start: Int, limit: Int): Int {
        var index = start
        while (index < limit && descriptor[index] == '[') index++

        return when {
            index >= limit -> start
            descriptor[index] != 'L' -> index + 1
            else -> objectTypeEnd(descriptor, index, limit)
        }
    }

    /**
     * Finds the end index of an object type starting at [start].
     *
     * @param descriptor The descriptor being scanned.
     * @param start The index of the leading `L`.
     * @param limit The index the parameter list ends at.
     * @return The index just past the closing semicolon, or [start] when there is none.
     * @since 2.1.0
     */
    private fun objectTypeEnd(descriptor: String, start: Int, limit: Int): Int {
        val semicolon = descriptor.indexOf(';', start)
        return if (semicolon < 0 || semicolon >= limit) start else semicolon + 1
    }
}
