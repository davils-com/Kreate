# Coverage overview

<link-summary>Measuring which code your tests actually execute, through Kover.</link-summary>

<card-summary>The measurement that tells you whether a green build proved anything.</card-summary>

<tldr>
<p><b>Enable</b>: <code>project { coverage { enabled = true } }</code></p>
<p><b>Requires</b>: the <code>org.jetbrains.kotlinx.kover</code> plugin applied by you</p>
</tldr>

Detekt says whether the code looks right. Trivy says whether it is safe to ship. Neither says
whether the code is ever executed. A test suite can be entirely green while half the codebase is
never entered, and nothing in an ordinary build reports that.

[Kover](https://github.com/Kotlin/kotlinx-kover) is JetBrains' coverage toolset for Kotlin. The
`coverage { }` block inside `kreate { project { } }` configures it: which source sets are measured,
which classes are instrumented, what the reports contain, and what threshold the build is held to.

## Quick start

Coverage is **disabled by default**. Apply Kover yourself and switch the integration on:

```kotlin
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    id("com.davils.kreate")
}

kreate {
    project {
        coverage {
            enabled = true
        }
    }
}
```

Then measure before you gate:

```bash
./gradlew koverLog
```

## %product% configures Kover, it does not apply it

Enabling the integration without the Kover plugin fails the build with a message telling you what
to add. That is deliberate: applying Kover on your behalf would pin its version to %product%'s
release cycle, and a coverage engine is exactly the kind of dependency a build wants to upgrade on
its own schedule. The same contract applies to [Detekt](Detekt-Overview.md).

Silently doing nothing would be the worse failure mode — a build that reports no coverage problem
because it measured no coverage looks identical to one that passed.

## What you get

<deflist type="wide">
    <def title="Reports">
        HTML for a person hunting the untested branch, XML for a machine, a log line for CI to
        parse, and a binary report for the command line tooling. See
        <a href="Coverage-Reports.md">Reports</a>.
    </def>
    <def title="A threshold gate">
        <code>koverVerify</code> runs as part of <code>check</code> and fails the build when
        coverage drops below the bounds you set. See
        <a href="Coverage-Verification.md">Verification</a>.
    </def>
    <def title="Filters">
        Generated code, DSL marker types and anything else that would measure your code generators
        rather than your tests. See <a href="Coverage-Configuration.md">Configuration</a>.
    </def>
</deflist>

## Reading the number honestly

<warning>
<b>Coverage measured through Gradle TestKit is not counted.</b> A suite that drives real Gradle
builds through <code>GradleRunner</code> runs them in a <i>separate daemon process</i>. Kover
instruments the test JVM, not the process that JVM starts, so code exercised only by TestKit
contributes nothing to the figure. This is not a %product% limitation and there is no
configuration that fixes it; the practical consequence is that a Gradle plugin project's real
test coverage is higher than its measured coverage, sometimes by a lot. %product%'s own build
measures roughly 29% while its plugin behaviour is covered by both a unit and a functional suite.
</warning>

Two further things a coverage percentage does not tell you:

- **Full line coverage is not full branch coverage.** A suite can execute every line while only
  ever taking one side of each condition. Set a branch bound alongside the line bound — see
  [Verification](Coverage-Verification.md).
- **Coverage measures execution, not assertion.** A test that calls a method and asserts nothing
  covers it completely. Coverage tells you where you have *no* test; it cannot tell you where you
  have a *good* one.

## Kover or JaCoCo

Kover's own engine is the default. JaCoCo is available for builds that have to interoperate with
tooling expecting it:

```kotlin
coverage {
    enabled = true
    useJacoco = true
    jacocoVersion = "0.8.14"
}
```

<note>
The two are not feature-equivalent. Filtering by annotation — the criterion that handles generated
code properly — does not work under JaCoCo. In a multi-project build every participating project
has to make the same choice.
</note>

<seealso>
    <category ref="project">
        <a href="Coverage-Configuration.md">Configuration</a>
        <a href="Coverage-Reports.md">Reports</a>
        <a href="Coverage-Verification.md">Verification</a>
        <a href="Testing-Overview.md">Testing</a>
    </category>
    <category ref="reference">
        <a href="CI-Integration.md">CI integration</a>
    </category>
</seealso>
