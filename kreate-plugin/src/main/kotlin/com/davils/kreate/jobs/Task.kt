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

package com.davils.kreate.jobs

import org.gradle.api.DefaultTask
import org.gradle.work.DisableCachingByDefault

/**
 * A base class for Kreate's Gradle tasks.
 *
 * It only assigns the task group and description, so that every Kreate task shows up in
 * `gradle tasks` under a recognisable heading.
 *
 * Up to 2.0.0 this class also implemented a `Process` interface declaring `execute()`,
 * which forced every task to write `@TaskAction override fun execute()`. That bought
 * nothing — Gradle discovers task actions through the annotation, not through a type — and
 * it actively misled: the sibling `Executable : Exec()` base class inherited a second,
 * unrelated action method of its own. The interface and that unused `Executable` class were
 * removed; task actions are now plain annotated methods.
 *
 * @param desc A description of what this task does.
 * @param group The Gradle task group this task belongs to. Defaults to "kreate".
 * @since 1.0.0
 */
@DisableCachingByDefault(because = "Base task class for Kreate tasks")
public abstract class Task(desc: String, group: String = "kreate") : DefaultTask() {
    init {
        this.group = group
        description = desc
    }
}
