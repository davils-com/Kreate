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
 * Renders the difference between a checked-in dump and a freshly extracted one.
 *
 * A dump is sorted, so a real API change shows up as one or two localised regions rather
 * than as a scattered set of lines. Trimming the common prefix and suffix is therefore
 * enough to produce a report a reviewer can act on, and it stays linear in the size of the
 * dump — which a full longest-common-subsequence diff would not, on the dumps large
 * projects produce.
 *
 * @since 2.1.0
 */
internal object AbiDiff {
    private const val MAX_REPORTED_LINES = 60

    /**
     * Builds a human readable report of how two dumps differ.
     *
     * @param expected The contents of the checked-in dump.
     * @param actual The contents of the dump extracted from the current classes.
     * @return The report, or `null` when the two are identical.
     * @since 2.1.0
     */
    fun render(expected: String, actual: String): String? {
        if (expected == actual) return null

        val expectedLines = expected.lines()
        val actualLines = actual.lines()

        var start = 0
        val maxStart = minOf(expectedLines.size, actualLines.size)
        while (start < maxStart && expectedLines[start] == actualLines[start]) start++

        var fromEnd = 0
        val maxFromEnd = minOf(expectedLines.size, actualLines.size) - start
        while (
            fromEnd < maxFromEnd &&
            expectedLines[expectedLines.size - 1 - fromEnd] == actualLines[actualLines.size - 1 - fromEnd]
        ) {
            fromEnd++
        }

        val removed = expectedLines.subList(start, expectedLines.size - fromEnd)
        val added = actualLines.subList(start, actualLines.size - fromEnd)

        return buildString {
            appendLine("--- ${start + 1} line(s) of context skipped")
            appendSection("-", removed)
            appendSection("+", added)
        }.trimEnd()
    }

    private fun StringBuilder.appendSection(prefix: String, lines: List<String>) {
        lines.take(MAX_REPORTED_LINES).forEach { line -> appendLine("$prefix$line") }
        if (lines.size > MAX_REPORTED_LINES) {
            appendLine("$prefix... and ${lines.size - MAX_REPORTED_LINES} more line(s)")
        }
    }
}
