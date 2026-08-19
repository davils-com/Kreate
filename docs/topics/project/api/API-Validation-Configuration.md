# API validation configuration

<link-summary>Every property in the apiValidation block, with defaults.</link-summary>

<card-summary>Where the dump lives, and how to keep a declaration out of it.</card-summary>

## Properties

| Property           | Type                  | Default              | Purpose                                                     |
|--------------------|-----------------------|----------------------|-------------------------------------------------------------|
| `enabled`          | `Property<Boolean>`   | `false`              | Activates the feature and registers the two tasks            |
| `apiDirectory`     | `DirectoryProperty`   | `<project>/api`      | Directory holding the checked-in dump                        |
| `dumpFileName`     | `Property<String>`    | `<project name>.api` | File name of the dump inside `apiDirectory`                  |
| `nonPublicMarkers` | `SetProperty<String>` | empty                | Annotations that hide whatever they are applied to           |
| `ignoredPackages`  | `SetProperty<String>` | empty                | Packages excluded from the dump, subpackages included        |
| `ignoredClasses`   | `SetProperty<String>` | empty                | Fully qualified class names excluded from the dump           |

```kotlin
kreate {
    project {
        apiValidation {
            enabled = true
            apiDirectory = layout.projectDirectory.dir("api")
            dumpFileName = "my-library.api"
            nonPublicMarkers = setOf("com.example.InternalApi")
            ignoredPackages = setOf("com.example.impl")
            ignoredClasses = setOf("com.example.LegacyBridge")
        }
    }
}
```

## Hiding an opt-in API

Some declarations have to be `public` in Kotlin — a consumer's inline function needs them,
or a sibling module does — without being part of the API you support. Mark them with an
annotation of your own and name it in `nonPublicMarkers`:

```kotlin
@Retention(AnnotationRetention.BINARY)
annotation class InternalApi
```

```kotlin
apiValidation {
    enabled = true
    nonPublicMarkers = setOf("com.example.InternalApi")
}
```

The annotation must be retained in the class file — `AnnotationRetention.SOURCE` is gone by
the time the bytecode is read, so it has no effect here. Applying the marker to a class
also hides everything nested inside it.

## Tasks

<table>
<tr><td>Task</td><td>Does</td><td>Inputs</td></tr>
<tr>
<td><code>kreateApiDump</code></td>
<td>Writes the current public interface to the dump</td>
<td>Compiled main classes</td>
</tr>
<tr>
<td><code>kreateApiCheck</code></td>
<td>Fails when the dump and the compiled classes disagree</td>
<td>Compiled main classes, the checked-in dump</td>
</tr>
</table>

Both are cacheable and both declare their inputs, so a second run is `UP-TO-DATE` and the
configuration cache is reused. `kreateApiCheck` is wired into `check`.

When the check fails it prints the differing lines and the exact command to run:

```
The public binary interface of project ':library' changed.

--- 12 line(s) of context skipped
+	public final fun added ()I

If the change is intended, record it and commit the result:

    ./gradlew :library:kreateApiDump
```

The extracted interface is also written to `build/kreate/api/<dump file name>` so it can be
compared against the checked-in file directly.
