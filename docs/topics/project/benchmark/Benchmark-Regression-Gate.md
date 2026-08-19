# Benchmark regression gate

<link-summary>Comparing a run against a committed baseline, and when to trust the result.</link-summary>

<card-summary>A committed baseline, a mode-aware comparison, and a noise test that keeps CI honest.</card-summary>

<tldr>
<p><b>Record</b>: <code>./gradlew kreateBenchmarkBaseline</code>, then commit the file</p>
<p><b>Verify</b>: <code>./gradlew kreateBenchmarkCheck</code></p>
<p><b>Baseline</b>: <code>benchmarks/baseline.json</code></p>
</tldr>

The gate works the way [API validation](API-Validation-Overview.md) does: a file in the
repository records what is expected, and the build fails when reality and that file
disagree. A change to the file is then something a reviewer approves rather than something
that happens quietly.

## The baseline

`kreateBenchmarkBaseline` writes only the fields the comparison reads:

```json
[
  {
    "benchmark" : "com.example.ParserBenchmark.parse",
    "mode" : "thrpt",
    "params" : {  },
    "primaryMetric" : {
      "score" : 2.5638331814905707E7,
      "scoreError" : "NaN",
      "scoreUnit" : "ops/s"
    }
  }
]
```

The raw report JMH writes carries the JVM path, the full argument list and a percentile
table besides. Committing that would put one developer's absolute paths into version
control and bury the scores a reviewer came to read, so the baseline is rewritten rather
than copied.

## How a run is compared

1. **Matched** on the benchmark name together with its `@Param` values, so the same
   benchmark run with different parameters is compared per parameter set.
2. **Directional.** For `thrpt` a lower score is worse; for `avgt` and the other time based
   modes a higher score is worse.
3. **Unit-checked.** A benchmark whose `mode` or `scoreUnit` changed is reported as
   incomparable and fails, because comparing `ops/s` against `ns/op` produces a number and
   not an answer.
4. **Noise-tested.** A regression counts only when the movement also exceeds the two
   measurement errors added together. When a run reports no error margin — a single
   iteration does not produce one — the threshold decides on its own.
5. **Missing benchmarks fail.** Deleting a benchmark is the simplest way to make a
   regression disappear, so its absence has to be recorded deliberately.
6. **New benchmarks pass.** They are listed in the report, never a failure.

Every run writes `build/reports/kreate/benchmark/comparison.md`, passing or failing.

## Configuration

```kotlin
kreate {
    project {
        benchmark {
            enabled = true

            regression {
                enabled = true
                profile = "main"
                maxRegressionPercent = 10.0
                thresholdOverrides = mapOf("com.example.ParserBenchmark.parse" to 25.0)
                failOnMissingBenchmark = true
                requireSignificance = true
            }
        }
    }
}
```

| Property                 | Type                         | Default                    |
|--------------------------|------------------------------|----------------------------|
| `enabled`                | `Property<Boolean>`          | `true`                     |
| `profile`                | `Property<String>`           | `main`                     |
| `baselineFile`           | `RegularFileProperty`        | `benchmarks/baseline.json` |
| `maxRegressionPercent`   | `Property<Double>`           | `10.0`                     |
| `thresholdOverrides`     | `MapProperty<String, Double>`| empty                      |
| `failOnMissingBenchmark` | `Property<Boolean>`          | `true`                     |
| `requireSignificance`    | `Property<Boolean>`          | `true`                     |

`thresholdOverrides` is how a known-noisy benchmark stays in the suite without loosening the
gate for everything else.

The gate reads JSON. Pointing `profile` at one configured for `csv`, `scsv` or `text` fails
at configuration time — a gate that waves through a run it cannot parse is worse than an
error message.

## The timestamped report

kotlinx-benchmark writes to `build/reports/benchmarks/<profile>/<timestamp>/<target>.json`.
The timestamp comes from the plugin and cannot be configured away, which means the report is
not something a task can declare as an output: nothing downstream can depend on it, nothing
can be up to date against it, and the directory gains an entry on every run.

`kreateBenchmarkReport` resolves the newest run once and republishes it at
`build/reports/kreate/benchmark/<profile>/`. That is what makes the gate an ordinary
cacheable task with declared inputs. You rarely run it yourself; the other two depend on it.

## When to trust the numbers

Read this before wiring the gate into a pull request check.

* **Shared CI runners are noisy.** Scores on a shared GitHub runner routinely move by tens of
  per cent between identical runs. The significance test stops that from failing builds, but
  the same test makes the gate insensitive to real regressions below roughly 15% there.
* **The gate is trustworthy on a dedicated runner.** Fixed hardware, no co-tenants, no
  virtualisation surprises — that is where a 10% threshold means something.
* **Run it on a schedule, not on every push.** A benchmark takes minutes and its result is
  only comparable against runs from the same machine. Kreate's own repository runs
  benchmarks from a manually triggered and scheduled workflow for exactly this reason.
* **Re-record after a deliberate change.** A rewritten hot path, a new Kotlin version, a
  changed `mode` — record a new baseline and let the diff show the size of the change.

## Task reference

| Task                      | Does                                                         |
|---------------------------|--------------------------------------------------------------|
| `kreateBenchmarkReport`   | Copies the newest run to a stable path                        |
| `kreateBenchmarkBaseline` | Records the current results as the baseline. Commit the file  |
| `kreateBenchmarkCheck`    | Runs the benchmarks and fails on a regression                 |

Asking for the baseline and the check in one invocation runs them in that order.
