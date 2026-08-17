<p align="center">
  <img src="docs/images/kreate.svg" alt="Kreate Logo" width="250">
</p>

<h1 align="center">Kreate</h1>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0">
    <img src="https://img.shields.io/badge/License-Apache_2.0-Redtronics?style=for-the-badge&logo=apache&labelColor=white&color=blue" alt="License">
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-2.4-Redtronics?style=for-the-badge&logo=kotlin&labelColor=white&color=purple" alt="Kotlin">
  </a>
  <a href="https://gradle.org">
    <img src="https://img.shields.io/badge/Gradle-9.0%2B-Redtronics?style=for-the-badge&logo=gradle&labelColor=white&color=02303A" alt="Gradle">
  </a>
  <a href="https://adoptium.net">
    <img src="https://img.shields.io/badge/JDK-17%2B-Redtronics?style=for-the-badge&logo=openjdk&labelColor=white&color=ED8B00" alt="JDK">
  </a>
</p>

<p align="center">
  <strong>Kreate</strong> is an opinionated Gradle plugin for Kotlin JVM and Multiplatform projects.
  It replaces the platform, native interop, security, testing, documentation and publishing
  boilerplate that every serious build accumulates with a single declarative DSL.
</p>

---

## Table of Contents

- [Why Kreate](#why-kreate)
- [Core Features](#core-features)
- [Quick Start](#quick-start)
- [Configuration Reference](#configuration-reference)
- [Compatibility](#compatibility)
- [Documentation](#documentation)
- [Third-Party Software](#third-party-software)
- [Contributing](#contributing)
- [License & Ethics](#license--ethics)

---

## Why Kreate

Kreate is a *convention* plugin, not a framework.

- **It does not wrap Gradle.** Your build script stays a Gradle build script, and every task
  Kreate registers is an ordinary task you can depend on, reconfigure, or disable.
- **It does not apply plugins behind your back.** Where an integration needs Detekt or the Maven
  Publish plugin, you apply it and choose its version; Kreate configures it.
- **It does not touch your repositories or dependency resolution** unless you explicitly ask.
- **Everything is opt-in.** Applying the plugin on its own registers no tasks and changes no
  behaviour.

---

## Core Features

### Platform Configuration

Kreate reacts to the Kotlin plugin you applied rather than guessing at your project type.

- **Toolchain alignment**: one `javaVersion` drives the Kotlin toolchain *and*
  `sourceCompatibility`/`targetCompatibility`, which is where "works on my machine" bytecode
  mismatches usually come from.
- **Compiler policy**: `explicitApi` and `allWarningsAsErrors` applied consistently across modules.
- **Multiplatform aware**: JVM and Multiplatform projects follow the appropriate configuration path.

### JNI (C/C++ on the JVM)

- **Generated headers**: `kreateJniHeaders` reads your compiled classes and emits the exact C
  declarations the JVM will look up — correct mangling, correct types, overload disambiguation.
  Kotlin has no `javac -h` equivalent, so this is the only automated way to stop a mistyped symbol
  from becoming a runtime `UnsatisfiedLinkError`.
- **Correct CMake builds**: the JDK comes from your Gradle toolchain rather than the machine
  default, output paths are pinned so multi-configuration generators behave predictably, and the
  full compiler output is surfaced on failure.
- **Reliable incremental builds**: every file the native build reads is a declared task input, so a
  C++ edit rebuilds and an untouched project does not.
- **Distributable artifacts**: optionally package the shared library into your JAR with a generated
  loader, so consumers need no `java.library.path` setup.

### C-Interoperability (Kotlin/Native)

- **Multi-language**: Rust via Cargo, C and C++ via CMake.
- **Automatic scaffolding** of the native project structure for the selected language.
- **Binding generation**: manages C headers and `.def` files for you.
- **Multi-architecture**: targets `x86_64`, `aarch64`, and other native triples.

### Security & Compliance

- **Vulnerability scanning**: CVEs in dependencies, from Gradle lock files.
- **Licence compliance**: verify third-party licences against a forbidden list.
- **Secret detection**: built-in and custom rules for hard-coded credentials.
- **Platform-agnostic**: the `trivy { }` block works in any project, including ones using no other
  Kreate feature.

### Project & Release

- **Build constants**: generate a type-safe Kotlin object from build values.
- **Testing**: parallel execution, readable logging, and HTML/XML reports.
- **Documentation**: Dokka configured from the same metadata as your POM.
- **Publishing**: signed releases to Maven Central and the GitLab Package Registry, with a
  declarative DSL for licences, developers, and SCM information.

---

## Quick Start

### Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.0"
    id("com.davils.kreate") version "2.0.0"
}
```

> **Note**
> Kreate configures Detekt and the Maven Publish plugin but does not apply them, so their versions
> stay under your control. Apply them yourself if you enable those integrations:
> `id("dev.detekt") version "..."` and `id("com.vanniktech.maven.publish") version "..."`.

### Configuration

```kotlin
group = "com.example"

kreate {
    platform {
        javaVersion = JavaVersion.VERSION_17
        explicitApi = true
        allWarningsAsErrors = true

        jvm {
            jni {
                enabled = true

                headers { enabled = true }      // generate JNI declarations
                packaging { enabled = true }    // ship natives inside the JAR
            }
        }
    }

    project {
        name = "MyProject"
        description = "A project powered by Kreate"

        version {
            environment = "CI_COMMIT_TAG"
            property = "version"
        }

        buildConstant {
            enabled = true
            className = "BuildConfig"
            constant("apiUrl", "https://api.example.com")
        }
    }

    trivy {
        enabled = true

        vulnerability {
            failOnFindings = true
            lockFiles.from(fileTree(projectDir) { include("*.lockfile") })
        }
    }
}
```

### Tasks

```bash
./gradlew tasks --group kreate      # what is registered
./gradlew kreateJniBuild            # build the native library
./gradlew kreateTrivyScan           # run all enabled security scans
./gradlew kreateBuildConstants      # regenerate build constants
```

---

## Configuration Reference

| Block                             | Property                   | Description                                | Default      |
|:----------------------------------|:---------------------------|:-------------------------------------------|:-------------|
| `platform`                        | `javaVersion`              | Java toolchain and bytecode target         | `VERSION_17` |
| `platform`                        | `explicitApi`              | Kotlin explicit API mode                   | `false`      |
| `platform`                        | `allWarningsAsErrors`      | Compiler warnings become errors            | `true`       |
| `platform.jvm.jni`                | `enabled`                  | CMake-based JNI integration                | `false`      |
| `platform.jvm.jni`                | `buildType`                | CMake build type                           | `Release`    |
| `platform.jvm.jni`                | `cmakeExecutable`          | Explicit CMake path                        | *resolved*   |
| `platform.jvm.jni.headers`        | `enabled`                  | Generate JNI headers from compiled classes | `true`       |
| `platform.jvm.jni.packaging`      | `enabled`                  | Package natives into the JAR               | `false`      |
| `platform.multiplatform.cInterop` | `enabled`                  | Native interop for Kotlin/Native           | `false`      |
| `platform.multiplatform.cInterop` | `language`                 | `RUST`, `C`, or `CPP`                      | `RUST`       |
| `project`                         | `applyDefaultRepositories` | Add public repositories to the project     | `false`      |
| `project`                         | `applySerializationPlugin` | Apply the serialization compiler plugin    | `false`      |
| `project.buildConstant`           | `enabled`                  | Generate type-safe Kotlin constants        | `false`      |
| `project.docs`                    | `enabled`                  | Dokka documentation                        | `false`      |
| `project.tests`                   | `enabled`                  | Test execution and reporting               | `true`       |
| `project.detekt`                  | `enabled`                  | Static analysis configuration              | `false`      |
| `project.publish`                 | `enabled`                  | Maven Central / GitLab publishing          | `false`      |
| `trivy`                           | `enabled`                  | Security and compliance scanning           | `false`      |

---

## Compatibility

| Component        | Minimum | Verified in CI                     |
|:-----------------|:--------|:-----------------------------------|
| Gradle           | 9.0     | 9.0 and the current release        |
| JDK              | 17      | 17, 21, 25                         |
| Kotlin           | 2.4.0   | 2.4.0                              |
| Operating system | —       | Linux, macOS, Windows              |
| CMake            | 3.20    | Required only for native features  |

Kreate is compiled against the Kotlin and Java versions embedded in the *minimum* supported
Gradle. A plugin built against a newer API does not fail in its own repository — it fails at
runtime on a consumer's machine, which is exactly what building against the floor prevents.

---

## Documentation

- **[Documentation site](https://davils-com.github.io/kreate/)**: guides, configuration
  references, and troubleshooting.
- **[Example project](./example)**: a working reference covering JNI, C-interop, Trivy, build
  constants, testing, and publishing.
- **[Changelog](./CHANGELOG.md)**: what changed in each release.
- **[Security policy](./SECURITY.md)**: how to report a vulnerability.

---

## Third-Party Software

Kreate leverages various open-source technologies. For a full list of libraries and licenses,
please refer to the [Third-Party Software](./THIRDPARTY.md) document.

---

## Contributing

Contributions are welcome. To keep the quality bar where it is:

- **Tests**: new or changed behaviour needs a test. The suite drives real Gradle builds through
  TestKit, so a behavioural change is genuinely verifiable.
- **Documentation**: API and behaviour changes must be reflected in `docs/topics/`.
- **Public API**: run `./gradlew apiDump` and commit the result if the DSL changed.
- **Standards**: follow the KDoc rules in `.junie/AGENTS.md` — every public declaration carries
  `@param`, `@return`, and `@since`, and Detekt enforces it.

Detailed instructions are in the [Contributing Guidelines](CONTRIBUTING.md).

---

## License & Ethics

- **License**: Published under the **Apache License 2.0**. See `LICENSE` for details.
- **Code of Conduct**: We adhere to our [Code of Conduct](CODE_OF_CONDUCT.md).

---

<p align="center">
  Maintained by <a href="https://github.com/davils-com"><b>Davils</b></a>
</p>
