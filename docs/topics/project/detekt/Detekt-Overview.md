# Detekt overview

<link-summary>Wiring Detekt into your build through Kreate.</link-summary>

<card-summary>Static analysis configured from one place, with reports in every format.</card-summary>

<tldr>
<p><b>Enable</b>: <code>project { detekt { enabled = true } }</code></p>
<p><b>Requires</b>: the <code>dev.detekt</code> plugin applied by you</p>
</tldr>

Detekt is a static code analysis tool for the Kotlin programming language. It operates on the abstract syntax tree provided by the Kotlin compiler and focuses on finding code smells, complexity issues, and potential bugs.

The `detekt { }` block inside `kreate { project { } }` provides a deeply integrated configuration for Detekt. Kreate automates the setup of the Detekt Gradle plugin, manages rule-set application, and configures reporting with professional defaults tailored for Kotlin development.

## Quick Start

Detekt is **disabled by default**. To activate it, set the `enabled` property to `true`:

```kotlin
kreate {
    project {
        detekt {
            enabled = true
        }
    }
}
```

Once enabled, you must manually apply the `dev.detekt` plugin to your project. Kreate will then automatically configure the standard tasks and reports.

```kotlin
plugins {
    id("dev.detekt") version "1.23.6" // Use the latest version
}

kreate {
    project {
        detekt {
            enabled = true
        }
    }
}
```

## Why use Detekt?

Static analysis helps maintain a high-quality codebase by:
- **Enforcing Consistency**: Ensures all developers follow the same coding standards.
- **Reducing Technical Debt**: Catches complex or hard-to-maintain code early.
- **Finding Bugs**: Identifies potential issues that might not be caught by the compiler.
- **Education**: Helps developers learn Kotlin best practices through rule descriptions.

## Key Features in Kreate

- **Zero-Configuration**: Sensible defaults are provided out of the box.
- **Nested DSL**: Familiar configuration structure matching the original Detekt plugin.
- **Automated Reporting**: Generates HTML, SARIF, XML, and Markdown reports by default.
- **Baseline Support**: Easily use custom `detekt.yaml` configurations.
- **Extensible**: Add third-party rule sets like `detekt-formatting` with ease.




<seealso>
    <category ref="project">
        <a href="Detekt-Configuration.md">Configuration</a>
        <a href="Detekt-Reports.md">Reports</a>
    </category>
</seealso>
