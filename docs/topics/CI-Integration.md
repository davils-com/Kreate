# CI integration

<link-summary>
Wiring %product%'s checks into GitHub Actions, GitLab CI, or any other runner.
</link-summary>

<card-summary>
Ready-to-adapt pipelines, plus the pitfalls that make a green build meaningless.
</card-summary>

%product% adds ordinary Gradle tasks, so any runner that can execute `./gradlew` can use them.
What follows is what a pipeline should actually run, and the mistakes that make one pass without
verifying anything.

## What to run

<deflist type="wide">
    <def title="./gradlew build">
        Compiles, tests, and assembles. With <code>project.tests.enabled</code> this includes your
        test suite with the configured reporting.
    </def>
    <def title="./gradlew kreateTrivyScan">
        The security and compliance scans. Needs Trivy installed and dependency lock files
        present.
    </def>
    <def title="./gradlew detekt">
        Static analysis, when the <a href="Detekt-Overview.md">integration</a> is enabled.
    </def>
    <def title="./gradlew kreateJniBuild">
        Included in <code>build</code> transitively, but useful as an explicit step when you want
        the native failure reported separately from the JVM one.
    </def>
</deflist>

## Two pitfalls that produce a meaningless green build

<warning>
<b>Composite builds are not reached by a root-level task name.</b>
If your plugin or build logic lives in an <code>includeBuild</code>, running
<code>./gradlew build</code> from the root does <i>not</i> run that build's tests or checks — it
only builds it far enough to satisfy the consumer. Name the tasks explicitly:
<code-block lang="bash">./gradlew :my-included-build:build build</code-block>
</warning>

<warning>
<b>A skipped native test is not a passing native test.</b>
Test suites that skip when a toolchain is missing are right to do so on a developer machine and
wrong to do so in CI, where a missing toolchain is an infrastructure failure. Install the
toolchain and assert it is present:
<code-block lang="bash">cmake --version || exit 1</code-block>
</warning>

## GitHub Actions

<code-block lang="yaml" collapsible="true" collapsed-title=".github/workflows/ci.yml">
name: CI

on:
  push:
    branches: ['**']
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

permissions: {}

jobs:
  build:
    runs-on: ${{ matrix.os }}
    permissions:
      contents: read
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
        java: ['17', '21', '25']
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${{ matrix.java }}

      - uses: gradle/actions@v6
        with:
          validate-wrappers: true

      - name: Install CMake
        if: runner.os == 'Linux'
        run: sudo apt-get update &amp;&amp; sudo apt-get install -y cmake

      - name: Install CMake
        if: runner.os == 'macOS'
        run: brew install cmake

      - run: ./gradlew build --configuration-cache --stacktrace
</code-block>

<tip>
A native feature is exactly the kind of thing that works on Linux and fails on Windows. A
three-platform matrix is the only way to find that out before a user does — multi-configuration
generator behaviour, shared library naming, and path handling all differ.
</tip>

### Publishing findings to code scanning

Detekt and Trivy both emit SARIF, which GitHub renders as inline pull request annotations:

```yaml
      - name: Run Detekt
        run: ./gradlew detekt --continue

      - name: Upload SARIF
        if: always()
        uses: github/codeql-action/upload-sarif@v4
        with:
          sarif_file: build/reports/detekt/detekt.sarif
          category: detekt
```

The `if: always()` matters — the upload is most valuable precisely on the runs where the analysis
failed.

## GitLab CI

<code-block lang="yaml" collapsible="true" collapsed-title=".gitlab-ci.yml">
stages: [build, verify, publish]

variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"

default:
  image: eclipse-temurin:21-jdk
  before_script:
    - apt-get update &amp;&amp; apt-get install -y cmake
    - cmake --version

build:
  stage: build
  script:
    - ./gradlew build --configuration-cache
  artifacts:
    when: always
    reports:
      junit: "**/build/test-results/test/TEST-*.xml"
    paths:
      - "**/build/reports/"
    expire_in: 1 week

security:
  stage: verify
  before_script:
    - apt-get update &amp;&amp; apt-get install -y wget gnupg
    - wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor > /usr/share/keyrings/trivy.gpg
    - echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" > /etc/apt/sources.list.d/trivy.list
    - apt-get update &amp;&amp; apt-get install -y trivy
  script:
    - ./gradlew dependencies --write-locks
    - ./gradlew kreateTrivyScan

publish:
  stage: publish
  rules:
    - if: $CI_COMMIT_TAG
  script:
    - ./gradlew publish
</code-block>

<note>
The GitLab publishing integration reads <code>CI_JOB_TOKEN</code>, <code>CI_PROJECT_ID</code>, and
<code>CI_API_V4_URL</code>, all of which GitLab provides automatically. See
<a href="Publishing-Gitlab-Registry.md">GitLab Package Registry</a>.
</note>

## Versioning from CI

Configure the version to come from your CI's tag variable, with a local fallback:

```kotlin
kreate {
    project {
        version {
            environment = "CI_COMMIT_TAG"   // GitHub: use a step that exports this
            property = "version"            // falls back to gradle.properties
        }
    }
}
```

<warning>
%product% logs a warning when neither source yields a version and it falls back to
<code>1.0.0</code>. Treat that warning as an error in a release pipeline — a release accidentally
published as <code>1.0.0</code> cannot be taken back from a public repository.
</warning>

See [Version resolution](Project-Version-Resolution.md).

## Caching

<deflist type="medium">
    <def title="Gradle caches">
        Use your runner's Gradle integration (<code>gradle/actions</code> on GitHub, the Gradle
        cache on GitLab) rather than caching directories by hand.
    </def>
    <def title="The native build directory">
        Cache it only if the workspace path is stable across runs. CMake bakes absolute paths into
        its cache, so a cache restored under a different path forces a reconfiguration — correct,
        but no faster than not caching it.
    </def>
    <def title="The configuration cache">
        Enable it with <code>--configuration-cache</code>. All %product% tasks support it.
    </def>
</deflist>

## Reproducibility

If you build artifacts you intend to be independently verifiable, assert it:

```bash
./gradlew jar
sha256sum build/libs/*.jar > first.sha256
./gradlew clean jar
sha256sum build/libs/*.jar > second.sha256
diff first.sha256 second.sha256
```

This requires pinned archive timestamps and file order in your build configuration.

<seealso>
    <category ref="reference">
        <a href="Task-Reference.md">Task reference</a>
        <a href="Compatibility.md">Compatibility</a>
    </category>
    <category ref="security">
        <a href="Trivy-Overview.md">Security and compliance</a>
    </category>
    <category ref="project">
        <a href="Publishing-Overview.md">Publishing</a>
        <a href="Testing-Overview.md">Testing</a>
    </category>
</seealso>
