# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 2.2.0

### Added

- **Per-platform publishing for JNI libraries**. `jni { packaging { publishing { } } }` publishes
  the native libraries as one artifact per platform — `com.example:mylib-linux-x64` next to
  `com.example:mylib` — instead of bundling them into the main JAR. A JNI library built on one
  machine can only contain that machine's binary, and the usual answer, a fat JAR assembled from a
  matrix over every operating system, needs a runner for each of them. Where those do not exist,
  this publishes what the infrastructure can build and adds platforms later without any consumer
  having to change anything.

  `platforms` is the selection for a release, not a promise about every version to come, so
  publishing a subset is an ordinary state rather than a warning. The one thing that fails is
  selecting a platform with no binary anywhere: `kreateJniVerifyPlatforms` reports it by name,
  because that gap is always an accident and would otherwise upload cleanly and surface as an
  `UnsatisfiedLinkError` in a consumer's process. `stagingDirectory` takes binaries built
  elsewhere, and a staged binary wins over a locally built one for the same platform.

  Enabling it moves the natives out of the main JAR entirely, so no consumer silently receives
  whichever platform the library happened to be built on. Existing builds are untouched: without
  the new block, `packaging` behaves exactly as before.
- **The generated loader names the missing coordinate.** With per-platform artifacts the usual
  cause of a failed load is a dependency the consumer did not declare, so the message now prints
  the exact `runtimeOnly(...)` line and lists the platforms that version shipped — which separates
  "you forgot the dependency" from "this version does not have that platform".
- **Code coverage**. `kreate { project { coverage { enabled = true } } }` configures
  [Kover](https://github.com/Kotlin/kotlinx-kover) — source set selection, instrumentation control,
  report filters, all four report formats, and a verification gate wired into `check`. It closes the
  last gap in the quality chain: Detekt says the code looks right and Trivy says it is safe to ship,
  but nothing said whether the code is ever executed. Kover is configured, not applied, so its
  version stays with the consumer; enabling the integration without the plugin fails with a message
  saying what to add rather than silently measuring nothing.

  The verification bounds deliberately have **no defaults**. A threshold picked before the first
  measurement is either too high, so the first thing anyone does is lower it, or too low, so it can
  never fail — and a gate that cannot fail is indistinguishable from a working one until the day it
  should have caught something. An unset bound registers no rule at all rather than a rule demanding
  zero coverage, and a named rule that declares no bounds is rejected for the same reason.
- **Named coverage rules**. `coverage { verify { rules { create("...") { ... } } } }` covers what a
  single minimum percentage cannot: several bounds under one heading, a per-class floor alongside a
  per-application one, and absolute counts. `maxBound(500, CoverageUnit.LINE,
  Aggregation.MISSED_COUNT)` caps how much untested code may exist at all — a limit a percentage
  cannot express, because at a fixed 80% a codebase that doubles in size doubles its untested code
  while the number on the badge never moves. Rules are named because the name is what the failure
  message shows.
- **Coverage aggregation**. `coverage { aggregate { enabled = true } }` merges the coverage of
  subprojects into one report, so the gate answers for the product rather than for whichever module
  happens to be best tested. Kover's own `merge { }` applies its plugin to the projects it
  aggregates; Kreate wires them through the `kover` configuration instead and reports the ones
  missing the plugin by path, because injecting a plugin into a project whose build script never
  mentions it is the behaviour this plugin exists to avoid.
- **Coverage of Kreate's own build**. The new `kreate.coverage-conventions` build-logic plugin
  measures the plugin itself, with a ratcheted line coverage bound set from a real measurement. That
  figure understates reality and the documentation says so: the functional suite drives Gradle
  through TestKit, which runs builds in a separate daemon process, and Kover instruments the test
  JVM rather than the process it starts.

## 2.1.1

### Fixed

- **`kreateApiCheck` failed on Windows for an interface that had not changed**. Git rewrites text
  files to CRLF when it checks them out on Windows, which is the default on the Windows CI
  images, while the dump Kreate renders always ends its lines with a line feed. The task compared
  the two as raw strings, so every Windows run of a project with a checked-in `.api` file failed.
  The report made the cause hard to see rather than obvious: the diff splits on line boundaries,
  which treats `\r\n` and `\n` alike, so it found no differing line and printed a bare context
  count with nothing underneath it. The checked-in file is now read with its line endings
  normalised, and a real interface change is still reported line by line. The test that pins the
  dump format to the `binary-compatibility-validator` plugin's output compared the same two
  values and failed for the same reason; it reads the dump the same way now.

## 2.1.0

### Added

- **Binary compatibility validation**. `kreate { project { apiValidation { enabled = true } } }`
  registers `kreateApiDump` and `kreateApiCheck`. The dump records every public and protected
  declaration of the compiled classes; the check runs as part of `check` and fails with the
  differing lines and the command to regenerate the file. Kreate reads the bytecode itself with
  ASM, so nothing else has to be applied to the consumer's build, and the file format is the one
  the `binary-compatibility-validator` plugin writes — an existing `api/*.api` file carries over
  unchanged, its line endings normalised on read so a Windows checkout that Git handed out with
  CRLF does not read as an interface change. A test asserts that byte for byte against a dump
  the plugin produced.
  Kotlin `internal` declarations are excluded via the class metadata, along with the default
  argument bridges that would otherwise outlive them, and so is compiler plumbing such as
  `access$` accessors and marker-only constructors. Declarations can also be hidden with an
  annotation of your own through `nonPublicMarkers`, or excluded by package or class name.
- **Dependency locking**. `kreate { project { dependencyLocking { enabled = true } } }` activates
  locking and registers `kreateResolveAndLockAll`, which resolves every locked classpath in one
  invocation. `--write-locks` records only the configurations a build actually resolves, so
  running it against an arbitrary task writes a lock file that looks complete and is not; the
  Trivy scans read those files, which is where an incomplete one does real damage. The default
  locks `compileClasspath` and `runtimeClasspath` rather than everything, because locking the
  build tools too makes a vulnerability scan report CVEs in a documentation tool's XML parser as
  though they were vulnerabilities in the published artifact — 2 of 103 entries were shipped
  dependencies in the measurement behind that default.
- **kotlinx-benchmark integration**. `kreate { project { benchmark { enabled = true } } }` builds
    the `benchmarks` source set, associates it with `main` so the benchmarks reach its `internal`
    declarations and inherit its dependencies, adds `kotlinx-benchmark-runtime`, and applies the
    `allopen` compiler plugin for `org.openjdk.jmh.annotations.State` — a benchmark class that is
    final fails inside JMH with a message that never mentions `allopen`. Measurement profiles are
    configured through `profiles { }` with defaults chosen for reproducibility, including a fixed
    fork count. Kreate configures the kotlinx-benchmark plugin but does not apply it, and depends
    on it `compileOnly` so a 0.4.x artifact never reaches a consumer's buildscript classpath;
    enabling the feature without the plugin fails with an explanation rather than a
    `NoClassDefFoundError`.
- **Benchmark regression gate**. `kreateBenchmarkBaseline` records a committed baseline and
  `kreateBenchmarkCheck` fails the build when a benchmark got measurably slower. The comparison
  knows that `thrpt` is better when higher and `avgt` better when lower, matches on `@Param`
  values, refuses to compare scores recorded in a different mode or unit, and treats a benchmark
  that vanished from the run as a failure — deleting one is otherwise the simplest way to make a
  regression disappear. A regression counts only when it also exceeds the two measurement errors
  combined; without that test the gate fires on ordinary run-to-run variance and is switched off
  within a week. Every run writes a Markdown comparison, passing or failing.
- **`kreateBenchmarkReport`**. kotlinx-benchmark writes its report to a directory named after
  the time of the run, which no task can declare as an output: nothing downstream can depend on
  it, nothing can be up to date against it, and the directory gains an entry per run. This task
  republishes the newest run at a fixed path, which is what makes the gate an ordinary cacheable
  task with declared inputs.
- **A canonical baseline format**. The report JMH writes carries the JVM path, the full argument
  list and a percentile table. The baseline holds the six fields the comparison reads, so a
  committed file does not contain one developer's absolute paths and a diff shows the scores.

### Changed

- **Lock files are no longer ignored by Git**. `*.lockfile` was in `.gitignore`, which is why the
  repository had none. Gradle writes "This file is expected to be part of source control" into
  every lock file it generates, and a lock file that cannot be committed locks nothing.
- **The Trivy workflow writes its locks with `kreateResolveAndLockAll`** rather than
  `:example:dependencies --write-locks`, which did not resolve every locked classpath.
- **The example project uses both new features** instead of the hand-written locking block it
  carried before, and its API dump is checked in CI.
- **The example project benchmarks itself**, with a deliberately wide threshold: it proves the
  pipeline is wired up, not that the machine is fast.
- **A `Benchmark` workflow** runs the gate on a schedule and on demand rather than on every push.
  Shared runners are too noisy for a per-pull-request gate, and the workflow says so.

## 2.0.1

### Fixed

- **GitLab publishing produced no artifacts**. `configureGitlab` registered the repository and
  then configured POM metadata through `publications.withType<MavenPublication>()`, but nothing
  ever created a publication. `publish` is a lifecycle task, so with no
  `PublishToMavenRepository` to depend on it reported `UP-TO-DATE` and the build passed —
  green pipeline, empty registry. Kreate now registers a `maven` publication from the
  project's `java` or `javaPlatform` component. A project that already declares a publication
  of its own, including the one the Maven Central plugin creates, is left untouched.
- **GitLab credentials could not authenticate**. The repository paired `PasswordCredentials`
  with `HttpHeaderAuthentication`, which accepts `HttpHeaderCredentials` and nothing else.
  This never surfaced because the defect above meant the repository was never used; with
  publishing fixed it would have failed every upload. The job token is now passed as
  `HttpHeaderCredentials` under the `Job-Token` header, as GitLab documents.
- **Incomplete registry coordinates failed late and unreadably**. A missing project id or API
  URL was interpolated into the URL as the literal `null`, and the build failed somewhere in
  the transport layer. Both are now validated up front, naming the variables that are unset.
- **Publications were missing outside CI**. The whole GitLab block returned early when no job
  token was present, so a local `publishToMavenLocal` had nothing to publish either. Only the
  remote repository now depends on the token; the publication is always registered.

### Added

- **Sources JAR for published libraries**. Projects with the `java` plugin get `withSourcesJar()`
  when GitLab publishing is enabled. A `java-platform` is skipped — a BOM has no sources.
- **Functional tests for publishing**. Four TestKit tests assert on the task graph rather than
  the exit code, which is what the original defect required: the old build succeeded.

## 2.0.0

### Added

- **JNI header generation**: The new `kreateJniHeaders` task reads the compiled classes, finds every
  method with the `ACC_NATIVE` flag, and emits the exact C declarations the JVM will look up —
  correct mangling, correct types, and long-form disambiguation for overloads. Kotlin has no
  `javac -h` equivalent, so JNI signatures previously had to be transcribed by hand, where a single
  wrong character compiled cleanly on both sides and failed at runtime with `UnsatisfiedLinkError`.
  Enabled by default via `jni { headers { } }`.
- **Native packaging**: `jni { packaging { } }` places the built shared library into the JAR under
  `natives/<os>-<arch>/` and generates a `KreateNativeLoader` object into your sources. The loader
  tries `System.loadLibrary` first and falls back to extracting the packaged copy, so a published
  artifact works without the consumer configuring `java.library.path`.
- **JNI toolchain configuration**: `buildType`, `cmakeExecutable`, and `generator` are now
  configurable rather than fixed.
- **`kreateJniConfigure`**: The CMake configure step is a task of its own, so an ordinary C++ edit
  no longer re-runs the generator.
- **`KreateTasks`**: Every task name the plugin registers is declared in one place and documented.
- **Test suite**: 77 tests where there were none — unit tests for the mangling, naming, version and
  toolchain resolution logic, and TestKit functional tests that drive real Gradle builds, including
  the native pipeline end to end.
- **Gradle compatibility matrix**: The functional suite runs against the declared minimum Gradle
  version and the current one, so the supported range is verified rather than asserted.
- **Binary compatibility validation**: The public DSL is covered by a checked-in API dump; a change
  to any public declaration fails the build until it is recorded.
- **CI**: Workflows for a three-platform, three-JDK build matrix, Detekt with SARIF upload, Trivy
  scanning with SBOM generation, and a reproducible-build check. All actions are pinned to commit
  SHAs and every job declares minimal permissions.
- **Repository hygiene**: `SECURITY.md`, `CODEOWNERS`, and pull request and issue templates.

### Fixed

- **JNI: stale shared libraries**. `kreateJniBuild` declared outputs but no relevant inputs, so
  Gradle considered it up to date indefinitely — edits to C++ sources produced no rebuild and the
  JVM kept loading the previous binary while the build reported success. All native sources,
  generated headers, and the CMake cache are now declared inputs.
- **JNI: unrecoverable CMake cache errors**. The CMake build directory lived inside the source tree,
  so it travelled with the sources. Renaming, moving, or checking the project out at a different
  path produced `CMake Error: The current CMakeCache.txt directory ... is different ...`, which
  `gradle clean` could not repair because it never reached in there. Build output now lives under
  `build/jni/<os>-<arch>/`, and the absolute paths CMake binds to are a declared task input, so a
  relocated project reconfigures automatically.
- **JNI: wrong JDK**. `find_package(JNI)` resolved against whatever JDK the machine defaulted to
  rather than the Gradle toolchain, silently compiling native code against different headers than
  the Kotlin code targeted. The toolchain JDK is now passed to CMake explicitly.
- **JNI: CMake configure failed on Windows**. Paths were handed to CMake with native
  backslashes. A backslash is an escape character in the CMake language, so `FindJNI` re-parsed
  `-DJAVA_HOME=C:\hostedtoolcache\...` as escape sequences and aborted with
  `Invalid character escape '\h'`. Every path passed to CMake now uses forward slashes, which
  are valid on all platforms.
- **JNI: artifacts missing on Windows and macOS**. Multi-configuration generators append the
  configuration name to the output path, so the library landed in `lib/Release/` while
  `java.library.path` pointed at `lib/`. The per-configuration output directories are now pinned.
- **JNI: new source files ignored**. The generated `CMakeLists.txt` globbed without
  `CONFIGURE_DEPENDS`, so a source file added after the first configure was never compiled.
- **JNI: swallowed build failures**. Only the exception message survived a failed CMake invocation.
  The full command, working directory, `JAVA_HOME`, and complete compiler output are now included.
- **JNI in multiplatform projects**: the native build was hooked into every Kotlin compilation task,
  including Kotlin/Native and Kotlin/JS targets, contradicting the documented behaviour. It is now
  wired only where the library is actually needed.
- **Overlapping task inputs and outputs**: `InitializeCppProject` declared the parent directory as
  an input and a directory inside it as an output, making its up-to-date check meaningless.
- **Configuration-time side effects**: directories were created while the build was being
  configured, which did not happen at all on a configuration cache hit.
- **Eager task realization**: `executeTaskBeforeCompile` called `.get()` on a `TaskProvider` and
  `tasks.contains(...)`, defeating configuration avoidance.
- **C-interop: stale native libraries**. `kreateCInteropCompile` declared outputs but no source
  inputs, so an edited Rust or C++ file produced no rebuild — the same defect as the JNI build task.
  The native sources are now declared inputs.
- **C-interop: overlapping task inputs and outputs**. Every task in the pipeline declared the
  native project directory as an input while writing its output into that same directory, which
  made their up-to-date checks meaningless. The directories are now internal and each task
  declares what it actually reads.
- **C-interop: swallowed build failures**. `CompileNative` and `CompileRust` caught every
  exception and replaced it with a generic message, discarding the Cargo or compiler diagnostic
  entirely. Both now use the shared process runner, which attaches the full command, working
  directory, and output to the failure.
- **C-interop: `ConfigureCargo` could never regenerate**. After the input/output overlap was
  removed it had no inputs at all, which would have left an existing project pinned to the
  manifest template of the Kreate version that first generated it. The template is now a declared
  input.
- **Executable resolution**: CMake, Cargo, and Trivy were each resolved by separate logic that
  searched conventional install directories on macOS only. A single resolver now searches the
  `PATH` first — including Windows executable extensions — then the conventional locations, on
  every platform.

- **Example: the vulnerability scan covered the wrong dependencies**. The example locked every
  resolvable configuration, so its lock file described the Kotlin compiler classpath, Dokka's
  HTML generator and Detekt's rule set plugins alongside the two dependencies it actually ships —
  2 shipped entries out of 103. The scan consequently reported CVEs in a documentation tool's XML
  parser as findings against the project. Locking is now restricted to the compile and runtime
  classpaths, and the Trivy documentation explains why.

### Changed

- **Task names are consistent.** The 1.x names followed three conventions at once. All tasks now use
  the `kreate` prefix, the feature, and the action, in camel case:

  | 1.x                             | 2.0.0                          |
  |---------------------------------|--------------------------------|
  | `kreate-jni-initialize`         | `kreateJniInitialize`          |
  | `kreate-jni-build`              | `kreateJniBuild`               |
  | `kreate-c-interop-initialize`   | `kreateCInteropInitialize`     |
  | `kreate-c-interop-dependencies` | `kreateCInteropDependencies`   |
  | `kreate-c-interop-configure`    | `kreateCInteropConfigure`      |
  | `kreate-c-interop-script`       | `kreateCInteropScript`         |
  | `kreate-c-interop-compile`      | `kreateCInteropCompile`        |
  | `kreate-c-interop-definitions`  | `kreateCInteropDefinitions`    |
  | `kreate-build-constants`        | `kreateBuildConstants`         |
  | `trivyScan`                     | `kreateTrivyScan`              |
  | `trivySecretScan`               | `kreateTrivySecretScan`        |
  | `trivyLicenseScan`              | `kreateTrivyLicenseScan`       |
  | `trivyVulnerabilityScan`        | `kreateTrivyVulnerabilityScan` |

- **Repositories are no longer injected.** The plugin added Maven Central, the Gradle Plugin Portal,
  and Google to every project it was applied to. In a build resolving through an internal mirror
  that is at best a warning under `repositoriesMode`, and at worst a silent bypass of the mirror.
  Now opt-in via `project { applyDefaultRepositories = true }`.
- **The serialization plugin is no longer applied unconditionally.** A compiler plugin participates
  in every compilation; projects that never serialize anything were paying for it. Now opt-in via
  `project { applySerializationPlugin = true }`.
- **The JNI pipeline runs after Kotlin compilation.** Headers are derived from compiled `external`
  declarations, so the previous ordering made generating them impossible. It also keeps a native
  build off the critical path of every Kotlin compile; test, run, and packaging tasks depend on the
  library instead.
- **Actionable failure messages.** Missing prerequisite plugins now raise a `GradleException` naming
  the project, the `plugins { }` block to add, and how to disable the integration instead.
- **The version fallback is logged.** Falling back to `1.0.0` when neither the CI environment
  variable nor the project property yields a version is now a warning; a release accidentally
  published as `1.0.0` cannot be withdrawn from a public repository.
- **`java.library.path` is contributed lazily and additively** through a Gradle argument provider,
  rather than by rewriting `jvmArgs` at configuration time — which previously discarded any library
  path the build had configured for its own reasons.
- **The plugin compiles against Kotlin 2.4.0 and Java 17**, the versions embedded in the minimum
  supported Gradle. A plugin built against a newer API fails on the consumer's machine at runtime
  rather than in its own build.
- **Reproducible archives**: pinned timestamps and file order, verified in CI.
- **Documentation rewritten**, including new topics on header generation, native packaging, JNI
  troubleshooting, CI integration, compatibility, and a complete task reference.

### Removed

- **`Process` interface and `Executable` base class.** `Executable` extended `Exec` while also
  declaring an `execute()` task action, giving it two competing actions. It was never used; the
  interface forced every task into a `@TaskAction override fun execute()` shape that bought nothing,
  since Gradle discovers task actions through the annotation.
- **Internal helpers from the public API**: `initializeJni`, `initializeCInterop`, and
  `getProjectVersion` are now `internal`.

### Build

- Gradle 9.7.0, Detekt 2.0.0-alpha.6.
- `buildSrc` replaced by a `build-logic` included build with three convention plugins.
- Strict plugin validation (`failOnWarning`, `enableStricterValidation`) and
  `allWarningsAsErrors` for the plugin's own sources.
- Dependabot now covers the `kreate-plugin` and `build-logic` builds, which as separate Gradle
  builds were never updated before.

## 1.3.1

### Added
- **JNI Library Runtime Paths**: Added `libraryRuntimePaths` option to the JNI configuration block. This allows specifying additional directories to be included in `java.library.path` at runtime, enabling the JVM to resolve external shared libraries that are not part of the primary JNI build.

### Fixed
- **JNI Library Path Merging**: Improved how `java.library.path` is configured for tests and execution tasks. It now correctly merges the default native build output directory with any user-specified runtime paths, preventing issues where external libraries were not correctly resolved.
- **Native Build Error Reporting**: Enhanced the `kreate-jni-build` task to provide more detailed error messages when CMake builds fail, including the underlying cause of the failure.

## 1.3.0

### Added
- **C and C++ Native Interop**: Extended Kotlin Multiplatform C-interop to support C and C++ as first-class native languages alongside Rust. A new `language` option in the `cInterop` block selects the pipeline: `NativeLanguage.RUST` (default) keeps the Cargo/`cbindgen` flow, while `NativeLanguage.C` and `NativeLanguage.CPP` scaffold and build a CMake static-library project that is bridged through a hand-written C header.
- **Multiple JNI Include Paths**: Added a `libraryIncludePaths` option to the JNI configuration block, allowing multiple C++ library include directories to be specified. Each configured path is passed to the compiler via the generated `CMakeLists.txt`, making it easier to depend on multiple external libraries located in different directories.
- **JNI for Multiplatform JVM Targets**: Extended JNI support to Kotlin Multiplatform projects. Native integration can now be configured via `platform.jvm.jni` and is wired into the JVM target's compilation, test, and run tasks without affecting other platform targets.

### Changed
- **C-Interop Pipeline Refactoring**: Internal reorganization of the C-interop initialization logic to support multiple native backends (Cargo for Rust, CMake for C/C++).
- **Task Registration Strategy**: Refined how native tasks are wired into the Kotlin compilation lifecycle to ensure consistent behavior across JVM and Multiplatform projects.

### Fixed
- **JNI Library Path Resolution**: Fixed issues where native libraries were not correctly resolved during tests in Multiplatform projects by ensuring the JVM target's runtime classpath is properly updated.

## 1.2.5

### Changed
- **Task Registration Refactoring**: Replaced property delegates with direct task registrations in JNI and C-Interop initializer classes to optimize configuration time.
- **Dependency Updates**:
  - Updated Kotlin to **2.4.0**.
  - Updated Gradle Wrapper to **9.6.0**.
  - Updated Detekt Gradle plugin.

### Fixed
- **Code Quality**: Cleaned up unused imports in platform-specific initializer classes.

## 1.2.4

### Added
- **Standalone Trivy Module**: Introduced a dedicated `TrivyModule` that operates independently of platform-specific modules (JVM/KMP), allowing security scans to be performed on any project type.

### Changed
- **Trivy DSL Relocation**: Moved the `trivy { }` configuration block from `kreate.project.trivy` to the top-level `kreate.trivy` scope to support platform-agnostic usage.
- **Example Project**: Updated the example `build.gradle.kts` to reflect the new independent Trivy configuration.

### Fixed
- **Module Decoupling**: Resolved the dependency of Trivy features on the `ProjectModule` lifecycle, ensuring security tasks are initialized correctly regardless of other module applications.

## 1.2.3

### Added
- **KDoc Completion**: Completed the KDoc documentation for the `Project.kt` configuration file, ensuring compliance with strict professional standards for all project identity, organization, and legal constants.

### Changed
- **Minimum Java Version**: Updated the minimum required Java version for the plugin and example projects to **Java 17**.
- **Documentation Update**: Synchronized all documentation (README, Getting Started, Platform guides) to reflect Java 17 as the new minimum requirement.

## 1.2.2

### Added
- **Detekt Report Specialization**: Introduced specialized report specifications for HTML, Markdown, Checkstyle, and SARIF formats.
- **Detekt Defaults**: HTML and Markdown reports are now enabled by default (`required = true`).

### Changed
- **Trivy Dependency Locking**: Refactored Trivy integration to require Gradle lockfiles for dependency scanning, removing the `disableDependencyLocking` option to ensure more reliable security audits.
- **Detekt Report Paths**: Standardized default output locations for all Detekt report types under `build/reports/detekt/`.

### Fixed
- **Code Quality**: Removed unused imports in `Initializer.kt` and cleaned up internal configuration logic.
- **Documentation**: Fixed incorrect project group assignments in documentation and updated KDoc to reflect new report specifications.

## 1.2.1

### Added
- **Trivy Configuration**: Added `disableDependencyLocking` property to `TrivyExtension` to allow manual management of Gradle lockfiles.

### Fixed
- **Trivy Initialization**: Fixed a `ConcurrentModificationException` and resolution strategy errors by ensuring dependency locking is only activated for unresolved configurations.

## 1.2.0

### Added
- **Trivy Security Integration**: Integrated [Trivy](https://trivy.dev/) for automated security and compliance scanning.
  - **Vulnerability Scanning**: Automatically scan project dependencies (via lockfiles) for known CVEs.
  - **License Compliance**: Verify dependency licenses against forbidden or restricted lists to ensure legal compliance.
  - **Secret Detection**: Scan source files, configuration files, and environment files for hardcoded secrets and credentials.
- **Detekt Integration**: Integrated [Detekt](https://detekt.dev/) for automated static code analysis to enforce clean code architecture and design patterns.
- **Unified Security & Quality DSL**: New `trivy { }` and `detekt { }` configuration blocks to easily manage security and code quality settings.
- **Optimized Execution**: Trivy scans are optimized to run in a single aggregate process per project, significantly reducing build times compared to file-by-file scanning.
- **Lifecycle Aggregation**: Added a global `trivyScan` task that serves as a single entry point for all enabled security checks.

### Changed
- **Plugin Application (Breaking Change)**: To resolve lifecycle ordering issues, especially in Kotlin Multiplatform projects, `kreate` no longer automatically applies `maven-publish`, `detekt`, or the Vanniktech Maven Publish plugin. These must now be applied manually in the `plugins { }` block.
- **KDoc Standards**: All new components follow strict professional KDoc guidelines, including mandatory `@param`, `@return`, and `@since` tags.
- **Task Logging**: Improved console output for security findings with better formatting and clear error messages.

### Fixed
- **Publishing Lifecycle**: Fixed a critical bug where Maven and GitLab publication settings were not correctly initialized in Kotlin Multiplatform projects because the required plugins were applied too late by the framework.

## 1.1.1

### Changed
- **Task Naming Convention**: Standardized all Gradle tasks to use a consistent `kreate-<module>-<task>` prefix (e.g., `kreate-jni-build`, `kreate-c-interop-compile`).
- **Task Organization**: Organized plugin tasks into specific Gradle groups (`kreate c-interoperation`, `kreate jni`, `kreate build-constants`) for better visibility and navigation.

## 1.1.0

### Added
- **JNI Support**: Added comprehensive support for Java Native Interface (JNI) in JVM modules.
  - **Automated C++ Integration**: Seamlessly bridge C/C++ libraries with JVM projects using CMake.
  - **Project Scaffolding**: Built-in task to initialize new CMake-based C++ projects for JNI.
  - **Automated Build Pipeline**: Native builds are automatically hooked into the Kotlin compilation process.
  - **Runtime Configuration**: Automatic configuration of `java.library.path` for tests and execution tasks to resolve native libraries.
- **Improved Platform DSL**: Added `jvm` configuration block to the platform DSL for better organization of JVM-specific settings.

### Changed
- **Naming Convention**: Standardized internal naming resolution for native features (JNI, C-Interop) to ensure compatibility across different platforms and toolchains.
- **Documentation**: Updated KDoc across all new JNI components to follow strict professional standards.

## 1.0.1

### Added
- **KDoc Documentation**: Added comprehensive KDoc documentation across the entire project for improved clarity and professionalism.
- **Project Configuration**: Added `.gitignore` and copyright configuration files for project management.
- **Dependency Management**: Added Dependabot configuration for automated Gradle and GitHub Actions updates.
- **C-Interop Docs**: Added detailed documentation and examples for integrating Rust with Kotlin Multiplatform via C-Interop.

### Fixed
- **Versioning**: Corrected Kotlin and KSP versions in `libs.versions.toml` to 2.3.0.
- **Legal**: Updated copyright year to 2026 in multiple files and LICENSE.
- **DSL**: Updated `projectName` parameter to be non-nullable in GitLab and MavenCentral configuration functions for better reliability.
- **Build**: Updated Gradle build commands in documentation to use `--no-daemon`.

### Changed
- **Architecture**: Simplified project and publication configuration by removing the `Davils` object.
- **Docs**: Updated README with more detailed installation and configuration instructions.
- **Build**: Updated various dependencies (Kotest, Gradle Wrapper, GitHub Actions, KSP).

## 1.0.0

### Added

#### Core Architecture & Platform Support
- **Intelligent Platform Detection**: Automatic identification and configuration of `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.multiplatform`, and Android projects.
- **Unified Platform DSL**: Simplified configuration for target Java versions (supporting Java 21+ toolchains).
- **Strict Quality Defaults**:
  - Optional `explicitApi()` mode enforcement.
  - `allWarningsAsErrors` enabled by default for cleaner codebases.
- **Native Multiplatform Support**: Pre-configured targets for Linux (x64), macOS (x64, arm64), and Windows (x64).

#### Rust C-Interop (KMP)
- **Automated Rust Integration**: Seamlessly bridge Rust libraries with Kotlin Multiplatform using `cinterop`.
- **Cargo Toolchain Support**: Automated execution of `cargo build` with cross-compilation support.
- **Project Scaffolding**: Built-in task to initialize new Rust library projects within the Kotlin workspace.
- **Header & Definition Management**: Simplified DSL for `.def` files and C-header synchronization.
- **Multi-Arch Compilation**: Support for major architectures including `x86_64-unknown-linux-gnu` and `aarch64-apple-darwin`.

#### Testing Pipeline
- **Kotest Integration**: Deep integration with Kotest for advanced testing capabilities.
- **Execution Engine**:
  - Configurable parallel test execution (max forks based on CPU availability).
  - Customizable timeouts and failure thresholds.
- **Enhanced Test Logging**: Clear, colorized output for test states (Started, Passed, Skipped, Failed).
- **Reporting**: Automated generation of comprehensive XML and HTML test reports for CI/CD pipelines.

#### Publishing & POM Management
- **Declarative POM DSL**: Easy configuration of metadata including licenses, developers, SCM, and issue management.
- **Registry Support**:
  - **Maven Central**: Streamlined publishing with automatic release and GPG signing.
  - **GitLab Package Registry**: Native support for GitLab CI environments using environment variables (`CI_JOB_TOKEN`, etc.).
- **Security**: Integrated GPG signing for all publications.

#### Documentation & Constants
- **Integrated Dokka Support**: Simplified generation of API documentation via Gradle.
- **Build Constants**: Generate type-safe Kotlin constants from Gradle properties to bridge build-time information into runtime code.

#### Project Management
- **Centralized Versioning**: Global management of project group and version across all modules.
- **Standardized Repositories**: Automatic configuration of Maven Central and Google repositories.

### Changed
- Initial stable release. Transitioned from internal development to a public Gradle plugin.
