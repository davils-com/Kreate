# Testing examples

<link-summary>Worked testing configuration examples.</link-summary>

<card-summary>Complete test setups you can copy.</card-summary>

## Minimal Setup — Kotlin JVM

Enable testing with all defaults. Tests run on the JUnit Platform for JVM targets with half the available
CPU cores, a 10-minute timeout, and passed/skipped events logged to the console.

```kotlin
kreate {
    project {
        tests {
            enabled = true
        }
    }
}
```

## Minimal Setup — Kotlin Multiplatform

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kreate)
}

kreate {
    project {
        tests {
            enabled = true
        }
    }
}
```

## CI-Optimized Configuration

Maximizes parallelism, enforces a strict timeout, ensures tests always run, fails on empty
test suites, and produces XML reports for the CI test parser.

```kotlin
tests {
    enabled = true
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    timeoutMinutes = 15
    alwaysRunTests = true
    failOnNoDiscoveredTests = true
    ignoreFailures = false

    report {
        enabled = true
        xml = true
        html = false
    }
}
```

## Silent Failures for Reporting Pipelines

Continue the build even when tests fail, so downstream tasks like artifact collection
or report publishing still run.

```kotlin
tests {
    enabled = true
    ignoreFailures = true

    report {
        enabled = true
        xml = true
    }
}
```

## Verbose Console Output

Log every test lifecycle event — useful during local development to see exactly which
tests are running and in what order.

```kotlin
tests {
    enabled = true
    logging {
        logPassedTests = true
        logSkippedTests = true
        logTestStarted = true
    }
}
```

## HTML Reports for Local Review

Generate a browsable HTML test report alongside the standard XML output.

```kotlin
tests {
    enabled = true
    report {
        enabled = true
        xml = true
        html = true
    }
}
```

Open the report after the build at:
```build/reports/tests/<taskName>/index.html```


## Full Configuration

All available options combined:

```kotlin
kreate {
    project {
        name = "MyLibrary"
        projectGroup = group.toString()

        tests {
            enabled = true
            maxParallelForks = 4
            timeoutMinutes = 10
            ignoreFailures = false
            alwaysRunTests = false
            failOnNoDiscoveredTests = true

            logging {
                logPassedTests = true
                logSkippedTests = true
                logTestStarted = false
            }

            report {
                enabled = true
                xml = true
                html = true
            }
        }
    }
}
```

<seealso>
    <category ref="project">
        <a href="Testing-Overview.md">Overview</a>
        <a href="Testing-Configuration-Reference.md">Configuration reference</a>
    </category>
</seealso>
