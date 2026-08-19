# Benchmarks

<link-summary>Measuring performance with kotlinx-benchmark, and holding the numbers against a committed baseline.</link-summary>

<card-summary>JMH benchmarks with a source set Kreate builds for you and a regression gate that fails the build.</card-summary>

<tldr>
<p><b>Enable</b>: <code>project { benchmark { enabled = true } }</code></p>
<p><b>Requires</b>: the <code>org.jetbrains.kotlinx.benchmark</code> plugin applied by you</p>
<p><b>Tasks</b>: <code>kreateBenchmarkBaseline</code>, <code>kreateBenchmarkCheck</code>, <code>kreateBenchmarkReport</code></p>
</tldr>

[kotlinx-benchmark](https://github.com/Kotlin/kotlinx-benchmark) measures. What it does not
do is tell you whether today's number is worse than last month's — and a benchmark nobody
compares against an earlier run is a number in a build log.

Kreate adds the parts around the measurement: the source set, the compiler plugin JMH
needs, a report at a path other tasks can depend on, and a baseline you commit and review.

## Quick start

Apply the plugin yourself, then enable the integration:

```kotlin
plugins {
    id("org.jetbrains.kotlinx.benchmark") version "0.4.17"
}

kreate {
    project {
        benchmark {
            enabled = true
        }
    }
}
```

Write a benchmark in `src/benchmarks/kotlin`:

```kotlin
package com.example

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
class ParserBenchmark {
    private val input = "…"

    @Benchmark
    fun parse(): Document = Parser.parse(input)
}
```

Record the baseline and commit it:

```bash
./gradlew kreateBenchmarkBaseline
```

From then on `kreateBenchmarkCheck` runs the benchmarks and fails when one got measurably
slower. It is **not** wired into `check`: a benchmark run takes minutes, and attaching it to
the ordinary build is the surest way to get it switched off.

## What Kreate sets up

Enabling the feature replaces the setup the kotlinx-benchmark documentation asks you to
write by hand:

* **A `benchmarks` source set**, kept apart from `main` so that JMH's generated code and the
  `allopen` transformation never reach the artifact you publish.
* **An association with `main`**, so the benchmarks see its `internal` declarations and
  inherit its dependencies. Without it you would be benchmarking a facade.
* **The `kotlinx-benchmark-runtime` dependency** on that source set.
* **The `allopen` compiler plugin**, configured for `org.openjdk.jmh.annotations.State`.
  JMH subclasses every `@State` class, so a benchmark class that is final fails during
  generation with an error that never mentions `allopen`. This is the one plugin Kreate
  applies for you, because there is nothing here to decide.
* **The measurement profiles**, with defaults chosen for reproducibility rather than speed —
  including a fixed fork count, which is the difference between a measurement and a
  snapshot of whatever state the JIT happened to be in.

## Why the plugin is not applied for you

Kreate configures kotlinx-benchmark, it does not apply it. It is a 0.4.x release with an API
that is not marked stable, so pinning it to Kreate's release cycle would make Kreate the
reason you cannot upgrade. Kreate does not put it on your buildscript classpath either — it
compiles against the plugin and lets your `plugins { }` block supply it at runtime.

If you enable the feature without applying the plugin, the build fails with an explanation
rather than a `NoClassDefFoundError`.

## Scope

The source set scaffolding covers Kotlin/JVM and the JVM target of a Kotlin Multiplatform
project. kotlinx-benchmark also supports Kotlin/Native, JS and Wasm; register those targets
yourself in the `benchmark { }` block it installs. The regression gate reads the JSON report
and does not care which platform produced it.
