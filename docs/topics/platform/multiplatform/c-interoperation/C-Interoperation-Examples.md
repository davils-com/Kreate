# Examples

<link-summary>Worked C-interop examples for Rust, C, and C++.</link-summary>

<card-summary>Complete native interop setups you can copy.</card-summary>

## Minimal Configuration

The simplest possible setup: C-Interop is enabled, and Kreate auto-detects everything else.

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

This uses the Gradle project name as the Rust project name, auto-detects the host target, and
places all files under `<module>/cinterop/`.

## Custom Name and Directory

Override the Rust project name and the directory where it is created:

```kotlin
kreate {
    platform {
        multiplatform {
            cInterop {
                enabled = true
                nameOverride = "myRustLib"
                projectDirectory = layout.projectDirectory.dir("native")
            }
        }
    }
}
```

The Rust project is created at `<module>/native/myRustLib/`, and the C-Interop compilation unit is
registered under the name `myRustLib`.

## Multi-Target Cross-Compilation

Compile for all four supported native targets at once:

```kotlin
kreate {
    platform {
        multiplatform {
            cInterop {
                enabled = true
                rustTargets = listOf(
                    "x86_64-unknown-linux-gnu",
                    "aarch64-unknown-linux-gnu",
                    "aarch64-apple-darwin",
                    "x86_64-pc-windows-gnu"
                )
                linux {
                    // applied to both linuxX64 and linuxArm64
                }
                macos {
                    // applied to macosArm64
                }
                mingw {
                    // applied to mingwX64
                }
            }
        }
    }
}
```

All four targets are compiled in sequence and their release directories are all listed in the
generated `.def` file's `libraryPaths`.

> Make sure to add each required Rust target beforehand:
> ```bash
> rustup target add x86_64-unknown-linux-gnu
> rustup target add aarch64-unknown-linux-gnu
> rustup target add aarch64-apple-darwin
> rustup target add x86_64-pc-windows-gnu
> ```
{style="note"}

## Custom Def File Location

Change where the Kotlin/Native definition file is written:

```kotlin
kreate {
    platform {
        multiplatform {
            cInterop {
                enabled = true
                defFile {
                    fileName = "bindings.def"
                    dirName = "kotlin-native"
                }
            }
        }
    }
}
```

The definition file is written to
`<module>/cinterop/<projectName>/kotlin-native/bindings.def`.

## Full Configuration

All available options combined:

```kotlin
kreate {
    platform {
        multiplatform {
            cInterop {
                enabled = true
                nameOverride = "mylib"
                projectDirectory = layout.projectDirectory.dir("rust")
                packageNameOverride = "com.davils.myapp.native"
                rustTargets = listOf(
                    "x86_64-unknown-linux-gnu",
                    "aarch64-apple-darwin"
                )
                defFile {
                    fileName = "cinterop.def"
                    dirName = "defs"
                }
                linux {
                    compilations.all {
                        kotlinOptions.freeCompilerArgs += listOf("-opt")
                    }
                }
                macos { }
            }
        }
    }
}
```

<seealso>
    <category ref="native">
        <a href="C-Interoperation-Overview.md">Overview</a>
        <a href="C-Interoperation-Configuration-Reference.md">Configuration reference</a>
    </category>
</seealso>
