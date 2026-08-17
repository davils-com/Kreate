# Overview

<link-summary>Bridging Rust, C, and C++ into Kotlin/Native.</link-summary>

<card-summary>Cargo or CMake builds wired into your multiplatform compilation.</card-summary>

<tldr>
<p><b>Enable</b>: <code>platform { multiplatform { cInterop { enabled = true } } }</code></p>
<p><b>Languages</b>: Rust (Cargo), C and C++ (CMake)</p>
<p><b>Build</b>: <code>./gradlew kreateCInteropCompile</code></p>
</tldr>

The C-Interop feature of the Kreate Gradle plugin provides a fully automated pipeline for bridging
Kotlin/Native multiplatform projects with native Rust libraries. When enabled, Kreate orchestrates
every step — from initializing a Rust project via Cargo, adding dependencies, compiling for
multiple native targets, generating C headers with `cbindgen`, producing Kotlin/Native `.def`
files, and wiring the resulting static libraries into your Kotlin Multiplatform build — all without
manual setup.

> **Prerequisites**
>
> - [Rust toolchain](https://rustup.rs/) installed (including `cargo` and `rustup`)
> - Required cross-compilation targets added via `rustup target add <target>`
> - Kotlin Multiplatform plugin applied to your Gradle module
> - Kreate plugin applied to your Gradle module
>
{style="note"}

## Pipeline Overview

The C-Interop pipeline consists of six ordered Gradle tasks that run automatically before any
Kotlin/Native compilation:

| Step | Task                      | Description                                                             |
|------|---------------------------|-------------------------------------------------------------------------|
| 1    | `kreateCInteropInitialize`   | Creates a new Rust library project with `cargo new --lib`               |
| 2    | `kreateCInteropDependencies` | Adds `libc` and `cbindgen` (or custom crates) via `cargo add`           |
| 3    | `kreateCInteropConfigure`    | Appends `[lib] crate-type = ["staticlib"]` to `Cargo.toml`              |
| 4    | `kreateCInteropScript`       | Generates a `build.rs` that runs `cbindgen` to produce C headers        |
| 5    | `kreateCInteropCompile`      | Runs `cargo build --release --target <target>` for each target          |
| 6    | `kreateCInteropDefinitions`  | Writes the Kotlin/Native `.def` file pointing to the compiled artifacts |

After step 6 completes, all `CInteropProcess` tasks automatically depend on
`kreateCInteropDefinitions`, so your normal `build` or `assemble` invocation drives the entire chain.

## Native Language Selection

Since Kreate **1.3.0**, C-Interop supports C and C++ as first-class native languages alongside Rust.
The `language` option selects the underlying pipeline:

| `language`             | Toolchain        | Pipeline summary                                                                 |
|------------------------|------------------|----------------------------------------------------------------------------------|
| `NativeLanguage.RUST`  | Cargo + cbindgen | _(default)_ Initializes a Cargo project and generates headers with `cbindgen`    |
| `NativeLanguage.C`     | CMake            | Scaffolds a CMake C project and builds a static library bridged via a C header   |
| `NativeLanguage.CPP`   | CMake            | Scaffolds a CMake C++ project and builds a static library bridged via a C header |

For `C` and `CPP`, the pipeline is reduced to three tasks:

| Step | Task                           | Description                                                              |
|------|--------------------------------|--------------------------------------------------------------------------|
| 1    | `kreateCInteropInitialize`  | Scaffolds a CMake project with `CMakeLists.txt`, `include/` and `src/`   |
| 2    | `kreateCInteropCompile`     | Runs `cmake` to build the static library `lib<name>.a`                   |
| 3    | `kreateCInteropDefinitions` | Writes the Kotlin/Native `.def` file pointing to the compiled artifacts  |

The C/C++ flow requires **CMake 3.20 or later** and a C/C++ compiler instead of the Rust toolchain.
The public API is declared in a hand-written header `include/<projectName>.h` (using an
`extern "C"` boundary for C++), which Kotlin/Native consumes directly.

## Enabling C-Interop

C-Interop is **disabled by default**. Enable it inside your module's `build.gradle.kts` within the
`kreate` extension block:

```kotlin
kreate {
    platform {
        multiplatform {
            cInterop {
                enabled = true
            }
        }
    }
}
```

Once enabled, all six pipeline tasks are registered automatically and execute in order before the
first `CInteropProcess` task runs.


<seealso>
    <category ref="native">
        <a href="C-Interoperation-Configuration-Reference.md">Configuration reference</a>
        <a href="C-Interoperation-Gradle-Task.md">Tasks</a>
        <a href="C-Interoperation-Examples.md">Examples</a>
        <a href="C-Interoperation-Troubleshooting.md">Troubleshooting</a>
    </category>
    <category ref="platform">
        <a href="JNI-Support.md">JNI (for the JVM)</a>
    </category>
</seealso>
