# Examples

<link-summary>Worked examples of generated build constants.</link-summary>

<card-summary>Common patterns for versioning, feature flags, and endpoints.</card-summary>

## Minimal Setup

Enable build constants with the project version embedded:

```kotlin
kreate {
    project {
        name = "MyLibrary"
        projectGroup = group.toString()

        buildConstant {
            enabled = true
            constant("version", project.version)
        }
    }
}
```

Generated output in `com.example.mylibrary.build`:

```kotlin
object BuildConstants {
    const val VERSION: String = "1.0.0"
}
```

## Embedding Multiple Values

Register any number of constants in a single block:

```kotlin
buildConstant {
    enabled = true
    constant("version", project.version)
    constant("name", project.name)
    constant("group", project.group)
    constant("buildTime", System.currentTimeMillis())
    constant("debug", "false")
}
```

## Custom Class Name and Package

Override the defaults for full control over the generated type:

```kotlin
buildConstant {
    enabled = true
    className = "AppInfo"
    packageNameOverride = "com.davils.app.meta"
    constant("version", project.version)
}
```

Generated file at `build/generated/compile/com/davils/app/meta/build/AppInfo.kt`:

```kotlin
object AppInfo {
    const val VERSION: String = "2.3.1"
}
```

## Custom Output Path

Place the generated sources in a non-default directory inside `build/`:

```kotlin
buildConstant {
    enabled = true
    path = "generated/kreate"
    constant("version", project.version)
}
```

The source set is still wired automatically — no manual `srcDir` call is needed.

## Using Constants in Code

After the `kreateBuildConstants` task runs (automatically before compilation), import
the generated object:

```kotlin
import com.example.mylibrary.build.BuildConstants

fun printBuildInfo() {
    println("Version : ${BuildConstants.VERSION}")
    println("Name    : ${BuildConstants.NAME}")
}
```

For Kotlin Multiplatform, the class is available in `commonMain` and all its dependant
source sets (`jvmMain`, `nativeMain`, etc.) without additional setup.

<seealso>
    <category ref="project">
        <a href="Constants-Overview.md">Overview</a>
        <a href="Constans-Configuration.md">Configuration</a>
    </category>
</seealso>
