# Coverage reports

<link-summary>HTML, XML, log and binary coverage reports and where they land.</link-summary>

<card-summary>Four formats, each answering a different question, and the CI contract behind the log one.</card-summary>

Each format exists for a different reader. HTML is for a person looking for the untested branch;
XML is for a machine — a CI system rendering merge request annotations, or a quality dashboard.
The log report is for the build output itself, which is where CI reads the headline percentage
from. The binary report is intermediate data for the command line tooling.

## There is no `enabled` flag

A coverage report task always exists and can always be invoked by name. The only thing a build can
actually decide is whether it runs *on its own* as part of `check`, which is what `onCheck`
expresses. A flag claiming to disable a task you can still run would be a lie in the DSL.

All four reports default to `onCheck = false`. Generating reports nobody reads on every local
`check` is pure latency; CI asks for the ones it needs by name. The verification gate is the
exception — see [Verification](Coverage-Verification.md).

## Tasks

| Task                 | Produces                                        |
|----------------------|-------------------------------------------------|
| `koverHtmlReport`    | Browsable HTML                                  |
| `koverXmlReport`     | JaCoCo-format XML                               |
| `koverLog`           | A summary line in the build output              |
| `koverBinaryReport`  | Intermediate binary coverage data               |
| `koverVerify`        | Runs the threshold gate                         |

These are Kover's tasks. %product% configures them; it does not register them, which is why they
carry no `kreate` prefix.

## `xml { }`

### `xml.onCheck`
- **Type**: `Property<Boolean>` — **Default**: `false`

### `xml.file`
- **Type**: `RegularFileProperty` — **Default**: `build/reports/kover/report.xml`

### `xml.title`
- **Type**: `Property<String>` — **Default**: unset

<note>
The XML is JaCoCo format. That is what makes it readable by external tooling without a conversion
step — GitLab consumes it directly as <code>coverage_format: jacoco</code>. See
<a href="CI-Integration.md">CI integration</a>.
</note>

## `html { }`

### `html.onCheck`
- **Type**: `Property<Boolean>` — **Default**: `false`

### `html.directory`
- **Type**: `DirectoryProperty` — **Default**: `build/reports/kover/html`

### `html.title` / `html.charset`
- **Type**: `Property<String>` — **Default**: unset

## `log { }`

### `log.onCheck`
- **Type**: `Property<Boolean>` — **Default**: `false`

### `log.format`
- **Type**: `Property<String>`
- **Default**: `<entity> line coverage: <value>%`
- **Description**: `<entity>` and `<value>` are substituted.

### `log.header`
- **Type**: `Property<String>` — **Default**: unset

### `log.groupBy`
- **Type**: `Property<Grouping>` — **Default**: `Grouping.APPLICATION`
- **Description**: One line is printed per entity. `APPLICATION` prints exactly one.

### `log.coverageUnit`
- **Type**: `Property<CoverageUnit>` — **Default**: `CoverageUnit.LINE`

### `log.aggregation`
- **Type**: `Property<Aggregation>` — **Default**: `Aggregation.COVERED_PERCENTAGE`

<warning>
<b>The log format is a CI contract, not a cosmetic choice.</b> GitLab extracts the coverage
percentage for its badge and merge request widget by matching a regular expression against the
<i>job log</i>. The default format is paired with the expression documented under
<a href="CI-Integration.md">CI integration</a> — change one and you have to change the other.
Leaving <code>groupBy</code> at <code>APPLICATION</code> matters too: any other value prints many
lines, and the CI system stores whichever one it matched first.
</warning>

## `binary { }`

### `binary.onCheck`
- **Type**: `Property<Boolean>` — **Default**: `false`

### `binary.file`
- **Type**: `RegularFileProperty` — **Default**: unset, which uses the engine's own location

## Example

```kotlin
kreate {
    project {
        coverage {
            enabled = true

            reports {
                xml {
                    onCheck = false
                    file = layout.buildDirectory.file("reports/coverage/coverage.xml")
                }

                html {
                    title = "My Project coverage"
                }

                log {
                    onCheck = true
                }
            }
        }
    }
}
```

<seealso>
    <category ref="project">
        <a href="Coverage-Overview.md">Overview</a>
        <a href="Coverage-Configuration.md">Configuration</a>
        <a href="Coverage-Verification.md">Verification</a>
    </category>
    <category ref="reference">
        <a href="CI-Integration.md">CI integration</a>
    </category>
</seealso>
