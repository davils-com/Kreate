# Project Metadata

<link-summary>Naming and describing your project.</link-summary>

<card-summary>The identity that flows into the POM, the docs, and the generated constants.</card-summary>

The `project { }` block inside `kreate { }` is the central place to declare the identity of your
Gradle module. Kreate reads these values and applies them to the Gradle project at evaluation time,
making them available to other subsystems like publishing, documentation, and build constants.

```kotlin
kreate {
    project {
        name = "Example"
        description = "Example project"
    }
}
```

> The `project { }` block is evaluated inside `afterEvaluate`, so all three properties must be set
> before the configuration phase ends.
>
{style="note"}

## Properties

### `name`

**Type:** `Property<String>`  
**Required:** yes

Sets the display name of the project. Other Kreate subsystems — such as publishing and build
constants generation — use this value to identify the artifact and generate derived names.

```kotlin
project {
    name = "MyLibrary"
}
```

### `description`

**Type:** `Property<String>`  
**Default:** `"A Kreate project."`

A human-readable description of the project. Kreate writes this value directly to
`project.description` during the `afterEvaluate` phase, so it appears in generated POM files and
documentation outputs.

```kotlin
project {
    description = "A high-performance Kotlin/Native library."
}
```

<seealso>
    <category ref="project">
        <a href="Project-Version-Resolution.md">Version resolution</a>
        <a href="Project-Repositories.md">Repositories and applied plugins</a>
        <a href="Publishing-POM-Configuration.md">POM configuration</a>
    </category>
</seealso>
