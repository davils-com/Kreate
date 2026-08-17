# Packaging natives

<link-summary>
Shipping the shared library inside your JAR so consumers need no native setup.
</link-summary>

<card-summary>
Put the .so, .dylib, or .dll into your artifact and let the generated loader extract it at runtime.
</card-summary>

<tldr>
<p><b>Enable</b>: <code>jni { packaging { enabled = true } }</code></p>
<p><b>Result</b>: <code>natives/&lt;os&gt;-&lt;arch&gt;/libfoo.so</code> inside your JAR</p>
<p><b>Default</b>: off</p>
</tldr>

## The problem

A JAR containing native code is not usable on its own. `System.loadLibrary` only resolves against
`java.library.path`, which a consumer of your artifact has no reason to have configured. Locally
your build sets it for you; a downstream project gets:

```
java.lang.UnsatisfiedLinkError: no my_module in java.library.path
```

Packaging closes that gap. The library travels inside the JAR, and a generated loader extracts it
on first use.

## Enabling it

```kotlin
kreate {
    platform {
        jvm {
            jni {
                enabled = true

                packaging {
                    enabled = true
                    generateLoader = true
                    resourcePath = "natives"
                }
            }
        }
    }
}
```

<deflist type="medium">
    <def title="enabled">
        Whether the built library is added to the JAR. Defaults to <code>false</code>, so
        upgrading %product% never changes the contents of an existing artifact.
    </def>
    <def title="generateLoader">
        Whether <code>KreateNativeLoader</code> is generated into your sources. Defaults to
        <code>true</code>.
    </def>
    <def title="resourcePath">
        The directory inside the JAR. Defaults to <code>natives</code>; the
        <code>&lt;os&gt;-&lt;arch&gt;</code> segment is appended automatically.
    </def>
</deflist>

## What ends up in the JAR

```text
my-module-1.0.0.jar
├── com/example/Native.class
├── com/example/my_module/jni/KreateNativeLoader.class
└── natives/
    └── linux-x64/
        └── libmy_module.so
```

Verify it with:

```bash
./gradlew jar
unzip -l build/libs/my-module-1.0.0.jar | grep natives
```

## Using the loader

Replace `System.loadLibrary` with the generated loader:

```kotlin
package com.example

import com.example.my_module.jni.KreateNativeLoader

class Native {
    init {
        KreateNativeLoader.load("my_module")
    }

    external fun greet(): String
}
```

The loader tries `System.loadLibrary` **first**. That ordering matters: during local development
your library path is already configured, and loading the freshly built binary is exactly what you
want. Only when that fails does it fall back to extracting the packaged copy into a temporary
directory and loading it from there.

<deflist type="wide">
    <def title="Why generated source rather than a runtime library">
        A separate <code>kreate-jni-runtime</code> artifact would have to be version-matched
        against the plugin by every consumer, and would add a compile dependency to a project
        that otherwise needs none. Generating the loader avoids both.
    </def>
    <def title="Idempotent">
        Repeated calls for the same library are ignored, so putting the call in an
        <code>init</code> block of a class instantiated many times costs nothing.
    </def>
</deflist>

## Supporting several platforms

The `<os>-<arch>` segment means one JAR can carry binaries for several platforms. A single build
produces the binary for the machine it ran on, so multi-platform artifacts are assembled by
building on each platform and merging the results.

<procedure title="Build a multi-platform artifact in CI" id="multiplatform-natives">
    <step>
        <p>Build on each target platform and publish the native output as a build artifact:</p>
        <code-block lang="yaml">
            strategy:
              matrix:
                os: [ubuntu-latest, macos-latest, windows-latest]
            steps:
              - run: ./gradlew kreateJniBuild
              - uses: actions/upload-artifact@v7
                with:
                  name: natives-${{ matrix.os }}
                  path: my-module/build/jni/
        </code-block>
    </step>
    <step>
        <p>
            In a job that depends on all three, download every artifact back into
            <code>build/jni/</code>. Because each platform writes to its own
            <code>&lt;os&gt;-&lt;arch&gt;</code> directory, they merge without collisions.
        </p>
    </step>
    <step>
        <p>
            Build the JAR. Every present platform directory is packaged, and the loader picks the
            matching one at runtime.
        </p>
        <code-block lang="bash">./gradlew jar</code-block>
    </step>
</procedure>

<warning>
The loader throws <code>UnsatisfiedLinkError</code> when the running platform has no packaged
binary and none is on the library path. If you publish an artifact built on one platform only,
say so in your documentation — a consumer on a different architecture will otherwise discover it
at runtime.
</warning>

## Extraction behaviour

<deflist type="medium">
    <def title="Where">
        A temporary directory created per JVM process.
    </def>
    <def title="Cleanup">
        Both the file and its directory are registered for deletion on JVM exit.
    </def>
    <def title="Cost">
        Once per library per process. Subsequent calls return immediately.
    </def>
</deflist>

<tip>
On a read-only or <code>noexec</code> temporary filesystem — some hardened containers — extraction
cannot work. In that environment, ship the library separately and set
<code>java.library.path</code>, which the loader tries first anyway.
</tip>

<seealso>
    <category ref="native">
        <a href="JNI-Support.md">JNI support</a>
        <a href="JNI-Configuration.md">Configuration reference</a>
        <a href="JNI-Troubleshooting.md">Troubleshooting</a>
    </category>
    <category ref="project">
        <a href="Publishing-Overview.md">Publishing</a>
    </category>
</seealso>
