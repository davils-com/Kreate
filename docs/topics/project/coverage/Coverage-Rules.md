# Verification rules

<link-summary>Named coverage rules with several bounds, for thresholds the shorthands cannot express.</link-summary>

<card-summary>When one minimum percentage is not the rule you actually want.</card-summary>

`minLineCoverage`, `minBranchCoverage` and `minInstructionCoverage` cover the common case: one
percentage, checked once, across the whole codebase. See
[Verification](Coverage-Verification.md) for those.

Named rules exist for everything else — several bounds under one heading, absolute counts instead
of percentages, or one rule checked per class while another is checked per application.

```kotlin
kreate {
    project {
        coverage {
            enabled = true

            verify {
                rules {
                    create("Every class carries its own weight") {
                        groupBy = Grouping.CLASS
                        minBound(60, CoverageUnit.LINE)
                    }

                    create("Untested code has a ceiling") {
                        maxBound(500, CoverageUnit.LINE, Aggregation.MISSED_COUNT)
                    }
                }
            }
        }
    }
}
```

Rules registered here are checked **in addition to** the shorthand bounds, not instead of them.

## Why rules are named

The name is what the failure message shows:

```
> Rule 'Every class carries its own weight' violated: lines covered percentage is 41.000000, but expected minimum is 60
```

That sends someone to the right place. An unnamed rule reports an index, and an index tells you
nothing about which of your thresholds you just broke.

## Rule properties

### `groupBy`
- **Type**: `Property<Grouping>`
- **Default**: unset, which inherits `verify.groupBy`
- **Description**: The entity this rule's bounds are evaluated against. Setting it per rule is the
  main reason to reach for a named rule: a strict per-class floor alongside a lenient
  application-wide one.

### `disabled`
- **Type**: `Property<Boolean>`
- **Default**: `false`
- **Description**: Skips the rule. Preferable to deleting one while a codebase is being brought
  back up to it — a disabled rule still documents the intent, a deleted one does not.

### `bounds`
- **Type**: `ListProperty<CoverageBoundSpec>`
- **Description**: Populated through `bound { }`, `minBound(...)` and `maxBound(...)`. A rule with
  no bounds is rejected rather than accepted, because it checks nothing and always passes — a
  green tick next to a threshold that measures nothing is worse than no threshold.

## Bound properties

### `min` / `max`
- **Type**: `Property<Int>`
- **Default**: unset
- **Description**: A bound setting neither is rejected. It measures something and demands nothing.

### `unit`
- **Type**: `Property<CoverageUnit>` — `LINE`, `INSTRUCTION` or `BRANCH`
- **Default**: `CoverageUnit.LINE`

### `aggregation`
- **Type**: `Property<Aggregation>`
- **Default**: `Aggregation.COVERED_PERCENTAGE`
- **Description**: `COVERED_COUNT`, `MISSED_COUNT`, `COVERED_PERCENTAGE` or `MISSED_PERCENTAGE`.

## Percentages and counts answer different questions

A percentage keeps the ratio from slipping as the codebase grows. It cannot cap how much untested
code exists: at a fixed 80%, a codebase that doubles in size doubles its untested code while the
number on the badge never moves.

`MISSED_COUNT` with a `maxBound` is the bound that says so:

```kotlin
create("Untested code has a ceiling") {
    maxBound(500, CoverageUnit.LINE, Aggregation.MISSED_COUNT)
}
```

Now the untested surface has an absolute limit, and growth has to be tested rather than merely
outpaced.

## Several bounds in one rule

Bounds accumulate, and all of them have to hold:

```kotlin
create("Core is held to a higher standard") {
    groupBy = Grouping.PACKAGE
    minBound(90, CoverageUnit.LINE)
    minBound(80, CoverageUnit.BRANCH)
}
```

## Adopting a stricter rule gradually

A per-class rule turned on late usually produces a violation list nobody works through. Two ways
to land it:

<deflist type="medium">
    <def title="Warn first">
        Set <code>verify { warningInsteadOfFailure = true }</code> while the list is being worked
        down. This affects every rule, so it is a transition state and not a destination.
    </def>
    <def title="Exclude, then narrow">
        Filter the classes that cannot meet it yet out of the report, and shrink the filter over
        time. Unlike a lowered threshold, the exclusion list is visible in the build file and
        shows what is still outstanding.
    </def>
</deflist>

<seealso>
    <category ref="project">
        <a href="Coverage-Verification.md">Verification</a>
        <a href="Coverage-Configuration.md">Configuration</a>
        <a href="Coverage-Aggregation.md">Aggregation</a>
    </category>
</seealso>
