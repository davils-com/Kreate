# Task reference

<link-summary>
Every task %product% registers: what enables it, what it consumes, and what it produces.
</link-summary>

<card-summary>
A complete index of %product%'s tasks, their inputs and outputs, and their caching behaviour.
</card-summary>

Task names are part of %product%'s public contract. They all follow one scheme: the `kreate`
prefix, then the feature, then the action, in camel case.

<tip>
List what is actually registered in your project with
<code>./gradlew tasks --group kreate</code>. A task you do not see is a feature you have not
enabled — nothing is registered speculatively.
</tip>

## JNI

Registered when `platform.jvm.jni.enabled` is `true`. See [JNI build pipeline](JNI-Build-Pipeline.md)
for how they fit together.

<table>
    <tr>
        <td>Task</td>
        <td>Purpose</td>
        <td>Inputs</td>
        <td>Outputs</td>
    </tr>
    <tr>
        <td><code>kreateJniInitialize</code></td>
        <td>Scaffolds the native project on first run.</td>
        <td>Project name</td>
        <td><code>jni/&lt;name&gt;/</code></td>
    </tr>
    <tr>
        <td><code>kreateJniHeaders</code></td>
        <td>Generates JNI declarations from compiled <code>native</code> methods.</td>
        <td>Compiled main classes</td>
        <td><code>build/generated/jni/include/</code></td>
    </tr>
    <tr>
        <td><code>kreateJniConfigure</code></td>
        <td>Runs the CMake configure step.</td>
        <td><code>CMakeLists.txt</code>, include paths, JDK, absolute paths</td>
        <td><code>build/jni/&lt;os&gt;-&lt;arch&gt;/cmake/CMakeCache.txt</code></td>
    </tr>
    <tr>
        <td><code>kreateJniBuild</code></td>
        <td>Compiles and links the shared library.</td>
        <td>Native sources, generated headers, CMake cache</td>
        <td><code>build/jni/&lt;os&gt;-&lt;arch&gt;/lib/</code></td>
    </tr>
    <tr>
        <td><code>kreateJniLoader</code></td>
        <td>Generates the runtime loader for packaged natives.</td>
        <td>Package name, resource path</td>
        <td><code>build/generated/jni/kotlin/</code></td>
    </tr>
</table>

<note>
The JNI pipeline runs <b>after</b> Kotlin compilation, not before it. The headers are derived
from the compiled <code>external</code> declarations, so the ordering is a requirement rather
than a preference — and it keeps a native build off the critical path of every Kotlin compile.
Test and run tasks depend on <code>kreateJniBuild</code>, which is where the library is actually
needed.
</note>

## C-interop

Registered when `platform.multiplatform.cInterop.enabled` is `true`. Which tasks exist depends on
the configured `language`.

<table>
    <tr>
        <td>Task</td>
        <td>Languages</td>
        <td>Purpose</td>
    </tr>
    <tr>
        <td><code>kreateCInteropInitialize</code></td>
        <td>All</td>
        <td>Scaffolds the Cargo or CMake project.</td>
    </tr>
    <tr>
        <td><code>kreateCInteropDependencies</code></td>
        <td>Rust</td>
        <td>Adds the required crates to <code>Cargo.toml</code>.</td>
    </tr>
    <tr>
        <td><code>kreateCInteropConfigure</code></td>
        <td>Rust</td>
        <td>Configures the Cargo build for the target list.</td>
    </tr>
    <tr>
        <td><code>kreateCInteropScript</code></td>
        <td>Rust</td>
        <td>Generates <code>build.rs</code> for header generation.</td>
    </tr>
    <tr>
        <td><code>kreateCInteropCompile</code></td>
        <td>All</td>
        <td>Builds the native library for every configured target.</td>
    </tr>
    <tr>
        <td><code>kreateCInteropDefinitions</code></td>
        <td>All</td>
        <td>Writes the <code>.def</code> files Kotlin/Native consumes.</td>
    </tr>
</table>

## Security and compliance

Registered when `trivy.enabled` is `true`.

<table>
    <tr>
        <td>Task</td>
        <td>Purpose</td>
        <td>Consumes</td>
    </tr>
    <tr>
        <td><code>kreateTrivyScan</code></td>
        <td>Aggregate — runs all three scans below.</td>
        <td>—</td>
    </tr>
    <tr>
        <td><code>kreateTrivyVulnerabilityScan</code></td>
        <td>Finds known CVEs in dependencies.</td>
        <td>Dependency lock files</td>
    </tr>
    <tr>
        <td><code>kreateTrivyLicenseScan</code></td>
        <td>Checks licence compliance.</td>
        <td>Dependency lock files</td>
    </tr>
    <tr>
        <td><code>kreateTrivySecretScan</code></td>
        <td>Finds hard-coded credentials.</td>
        <td>Source files, <code>trivy-secret.yaml</code></td>
    </tr>
</table>

<warning>
The vulnerability and licence scans read Gradle dependency lock files. Without them the scans
have nothing to inspect and skip with a message telling you to run
<code>./gradlew dependencies --write-locks</code>. Enable locking with
<code>dependencyLocking { lockAllConfigurations() }</code>.
</warning>

## Build constants

<table>
    <tr>
        <td>Task</td>
        <td>Purpose</td>
        <td>Outputs</td>
    </tr>
    <tr>
        <td><code>kreateBuildConstants</code></td>
        <td>Generates a Kotlin object from the configured constants.</td>
        <td><code>build/&lt;path&gt;/&lt;package&gt;/&lt;ClassName&gt;.kt</code></td>
    </tr>
</table>

Registered when `project.buildConstant.enabled` is `true`, and wired to run before Kotlin
compilation so the generated code is always available to your sources.

## Task groups

Tasks are grouped so that `./gradlew tasks` stays readable.

| Group | Contains |
|---|---|
| `kreate jni` | The JNI pipeline |
| `kreate c-interop` | The C-interop pipeline |
| `kreate trivy` | The security scans |
| `kreate build-constants` | Constant generation |

## Caching and up-to-date behaviour

<deflist type="wide">
    <def title="Cacheable">
        <code>kreateJniHeaders</code> and <code>kreateJniLoader</code> produce output that
        depends only on their declared inputs, so they are safe to share through the build cache.
    </def>
    <def title="Up-to-date checked, not cached">
        The native build tasks. Their output is tied to the local toolchain and to absolute
        paths, which makes it correct to reuse in place but wrong to relocate. They will skip
        when nothing changed, and re-run when a source, header, or the configuration does.
    </def>
    <def title="Never up to date">
        The Trivy scans. A dependency that was clean yesterday can have a CVE today, so a result
        cached against unchanged inputs would be actively misleading.
    </def>
</deflist>

<include from="lib.topic" element-id="config-cache-note"/>

<seealso>
    <category ref="reference">
        <a href="Compatibility.md">Compatibility</a>
        <a href="CI-Integration.md">CI integration</a>
    </category>
    <category ref="native">
        <a href="JNI-Build-Pipeline.md">JNI build pipeline</a>
        <a href="C-Interoperation-Gradle-Task.md">C-interop tasks</a>
    </category>
</seealso>
