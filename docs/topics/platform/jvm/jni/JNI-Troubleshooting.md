# Troubleshooting

<link-summary>
The native build failures you are most likely to hit, what each one means, and how to fix it.
</link-summary>

<card-summary>
UnsatisfiedLinkError, CMake cache errors, missing toolchains, and stale libraries — diagnosed.
</card-summary>

## UnsatisfiedLinkError at runtime

The single most common JNI failure, with several distinct causes. The message tells you which.

### "no &lt;name&gt; in java.library.path"

The library was not found at all.

<procedure title="Diagnose a missing library" id="diag-missing">
    <step>
        <p>Confirm the library was built:</p>
        <code-block lang="bash">./gradlew kreateJniBuild
ls build/jni/*/lib/</code-block>
    </step>
    <step>
        <p>
            Check that the name you pass to <code>System.loadLibrary</code> matches the CMake
            <code>add_library</code> target name. They must be identical — <code>libfoo.so</code>
            is loaded as <code>"foo"</code>, without the prefix or extension.
        </p>
    </step>
    <step>
        <p>
            If this happens only for a <i>consumer</i> of your published artifact, the library was
            never packaged. See <a href="JNI-Packaging.md">Packaging natives</a>.
        </p>
    </step>
    <step>
        <p>
            If this happens in a task %product% does not know about — a custom
            <code>JavaExec</code>, or an application run outside Gradle — set the library path
            yourself, or use the generated loader.
        </p>
    </step>
</procedure>

### "&lt;method signature&gt;" — the library loaded but a function was not found

The shared library was located, but the symbol for that specific method was not.

This is almost always a signature mismatch: the C++ function name does not match what the JVM
derived from your Kotlin declaration.

<warning>
Fix this by generating the header and including it, not by adjusting the name until it works.
Once <code>#include "&lt;name&gt;_jni.h"</code> is in your source, the C++ compiler checks your
definitions against the declarations the JVM will actually look up, and this failure becomes a
compile error. See <a href="JNI-Headers.md">Header generation</a>.
</warning>

Common causes:

<deflist type="medium">
    <def title="The class was renamed or moved">
        The mangled name embeds the full package and class name. Regenerate and re-include.
    </def>
    <def title="An overload was added">
        Adding a second <code>external fun</code> with the same name switches <b>both</b> to the
        long form with the argument signature appended.
    </def>
    <def title="Missing extern &quot;C&quot;">
        Without it, C++ name mangling is applied on top of JNI mangling. The generated header
        already wraps its declarations in <code>extern "C"</code>, which is another reason to
        include it.
    </def>
</deflist>

## CMakeCache.txt directory errors

```
CMake Error: The current CMakeCache.txt directory /path/a/build is different
than the directory /path/b/build where CMakeCache.txt was created.
```

CMake bakes absolute paths into its cache and refuses to reuse one from a different location.

%product% declares those paths as a task input, so renaming or moving the project triggers a
reconfiguration automatically. If you see this error anyway, the cache was carried in from
outside the build — for example restored from a CI cache keyed across different workspace paths.

```bash
./gradlew clean
```

<tip>
This is recoverable precisely because the CMake build directory lives under
<code>build/</code>. A native build directory inside the source tree is not reachable by
<code>clean</code>, which is what made this failure unrecoverable without a manual
<code>rm -rf</code> in earlier versions.
</tip>

## A C++ change has no effect

You edited a `.cpp`, rebuilt, and the old behaviour persists.

<procedure title="Diagnose a stale library" id="diag-stale">
    <step>
        <p>Check whether the task actually ran:</p>
        <code-block lang="bash">./gradlew kreateJniBuild --info | grep kreateJniBuild</code-block>
        <p>
            <code>UP-TO-DATE</code> after a real source change means the file is not being seen as
            an input.
        </p>
    </step>
    <step>
        <p>
            Confirm the file is where %product% looks: under
            <code>jni/&lt;projectName&gt;/src/</code> with a <code>.cpp</code> or <code>.cc</code>
            extension. Sources elsewhere are not tracked unless your
            <code>CMakeLists.txt</code> adds them.
        </p>
    </step>
    <step>
        <p>
            If you added a <i>new</i> source file and it is not being compiled, check that your
            <code>file(GLOB ...)</code> uses <code>CONFIGURE_DEPENDS</code>. Without it, CMake
            evaluates the glob once and never notices new files. The scaffolded
            <code>CMakeLists.txt</code> includes it; an older hand-written one may not.
        </p>
        <code-block lang="cmake">file(GLOB MY_MODULE_SOURCES CONFIGURE_DEPENDS "src/*.cpp" "src/*.cc")</code-block>
    </step>
</procedure>

## CMake not found

```
CMake configure failed with exit code 127.
```

or a message naming `cmake` as an unknown command.

<procedure title="Make CMake discoverable" id="diag-cmake">
    <step>
        <p>Verify it is installed and meets the minimum:</p>
        <code-block lang="bash">cmake --version</code-block>
    </step>
    <step>
        <p>
            If it works in your terminal but not in Gradle, the daemon does not have your shell's
            <code>PATH</code> — typical for IDE-launched builds on macOS. %product% searches the
            conventional install directories automatically, so this only remains for unusual
            locations.
        </p>
    </step>
    <step>
        <p>Point at it explicitly:</p>
        <code-block lang="kotlin">
            jni {
                enabled = true
                cmakeExecutable = "/opt/homebrew/bin/cmake"
            }
        </code-block>
    </step>
</procedure>

## Wrong JDK headers

Symptoms range from `jni.h: No such file or directory` to link errors mentioning JNI symbols, or
a library that loads but crashes.

%product% passes the Gradle toolchain JDK to CMake as `JAVA_HOME`, so `find_package(JNI)` resolves
against the same JDK your Kotlin code targets. Check what was actually used:

```bash
grep JAVA_HOME build/jni/*/cmake/CMakeCache.txt
```

<note>
If that path is not the JDK you expect, the toolchain resolved differently than you intended.
Check <code>platform { javaVersion }</code> and your installed toolchains with
<code>./gradlew javaToolchains</code>.
</note>

## The DLL is not found on Windows

Multi-configuration generators place output in a per-configuration subdirectory.

%product% pins `CMAKE_RUNTIME_OUTPUT_DIRECTORY_<CONFIG>` for every configuration to prevent this,
so it should not occur with a scaffolded `CMakeLists.txt`. A hand-written one that overrides
those variables will reintroduce it.

```bash
find build/jni -name "*.dll"
```

If the result is under `lib/Release/` rather than `lib/`, your CMake configuration is overriding
the pinned output directory.

## Getting more detail

<deflist type="medium">
    <def title="Full CMake output">
        <code-block lang="bash">./gradlew kreateJniBuild --info</code-block>
        <p>Successful CMake output is logged at info level; failures always include it.</p>
    </def>
    <def title="Inspect the configuration">
        <code-block lang="bash">cat build/jni/*/cmake/CMakeCache.txt</code-block>
    </def>
    <def title="Start from scratch">
        <code-block lang="bash">./gradlew clean kreateJniBuild</code-block>
    </def>
    <def title="Check the generated header">
        <code-block lang="bash">cat build/generated/jni/include/*_jni.h</code-block>
        <p>Compare its declarations against your definitions.</p>
    </def>
</deflist>

<seealso>
    <category ref="native">
        <a href="JNI-Support.md">JNI support</a>
        <a href="JNI-Headers.md">Header generation</a>
        <a href="JNI-Build-Pipeline.md">Build pipeline</a>
    </category>
</seealso>
