# Coverage verification

<link-summary>Coverage thresholds, why they have no default, and how to raise them.</link-summary>

<card-summary>The gate — and the reason it ships without a number in it.</card-summary>

<tldr>
<p><b>Runs on</b>: <code>check</code>, by default</p>
<p><b>Default bounds</b>: none — you measure first</p>
</tldr>

Verification is what turns coverage from a number somebody looks at occasionally into something
the build enforces. `koverVerify` runs as part of `check` and fails when coverage is below the
bounds you set.

## Why there is no default threshold

None of the three bounds has a default value, and that is the single most deliberate decision in
this feature.

A number picked before the first measurement lands in one of two places. Too high, and the build
breaks on the day coverage is switched on, so the first thing anyone does is lower it — which
teaches everyone that the gate is negotiable. Too low, and it can never fail, which is worse: a
gate that cannot fail is indistinguishable from a working one right up until the day it should
have caught something.

So: measure, then set.

```bash
./gradlew koverLog
```

```kotlin
coverage {
    verify {
        minLineCoverage = 71   // what koverLog just reported, rounded down
    }
}
```

<tip>
Treat the number as a <b>ratchet</b>. Raise it when a change raises the measurement; never lower
it to turn a red build green. A threshold that only goes up needs no policing — the build does it.
</tip>

An unset bound registers **no rule at all**, not a rule demanding zero coverage. The distinction
matters: the second would appear in the report as a passing check.

## Properties

### `runOnCheck`
- **Type**: `Property<Boolean>`
- **Default**: `true`
- **Description**: Whether verification runs as part of `check`. This is the one part of the
  coverage feature that runs unasked, because a threshold you have to invoke by name is one nobody
  hears about until someone remembers to look.

### `warningInsteadOfFailure`
- **Type**: `Property<Boolean>`
- **Default**: `false`
- **Description**: Logs a warning rather than failing. Useful while a codebase is being brought up
  to a threshold, dangerous as a permanent setting for the reason above.

### `groupBy`
- **Type**: `Property<Grouping>`
- **Default**: `Grouping.APPLICATION`
- **Description**: The entity each bound is evaluated against.

### `minLineCoverage` / `minBranchCoverage` / `minInstructionCoverage`
- **Type**: `Property<Int>`, a percentage between 0 and 100
- **Default**: unset
- **Description**: One verification rule is registered per bound that is set.

## Choosing a grouping

<deflist type="wide">
    <def title="Grouping.APPLICATION">
        Each bound is checked once against the whole codebase. A well-tested module can hide an
        untested one, but the number is stable and the gate is realistic to adopt on an existing
        project. The default.
    </def>
    <def title="Grouping.CLASS">
        Each bound is checked separately for every class, and one neglected class fails the build.
        Far stricter, and generally only realistic on a codebase that started out with the rule in
        place — turning it on later tends to produce a list of violations long enough that nobody
        works through it.
    </def>
    <def title="Grouping.PACKAGE">
        A middle ground: each package answers for itself.
    </def>
</deflist>

## Set a branch bound too

Line coverage alone will tell you a suite is thorough when it is not. Every line of

```kotlin
fun greet(name: String): String = if (name.isBlank()) "Hello!" else "Hello, $name!"
```

is executed by a single test passing `"Kreate"` — 100% line coverage with the blank-name path
never taken. A branch bound is what catches that:

```kotlin
coverage {
    verify {
        minLineCoverage = 80
        minBranchCoverage = 70
    }
}
```

Branch coverage runs lower than line coverage on essentially every codebase, so set it from its
own measurement rather than by copying the line number.

## Full example

```kotlin
kreate {
    project {
        coverage {
            enabled = true

            filters {
                excludes {
                    annotatedBy = listOf("com.example.Generated")
                }
            }

            verify {
                runOnCheck = true
                warningInsteadOfFailure = false
                groupBy = Grouping.APPLICATION
                minLineCoverage = 80
                minBranchCoverage = 70
            }
        }
    }
}
```

A violated bound fails with the rule name, the measured value and the expected minimum:

```
> Rule 'Minimum line coverage' violated: lines covered percentage is 64.200000, but expected minimum is 80
```

<seealso>
    <category ref="project">
        <a href="Coverage-Overview.md">Overview</a>
        <a href="Coverage-Configuration.md">Configuration</a>
        <a href="Coverage-Reports.md">Reports</a>
    </category>
    <category ref="reference">
        <a href="CI-Integration.md">CI integration</a>
    </category>
</seealso>
