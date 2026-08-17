# Scaffolding

<link-summary>
What kreateJniInitialize generates on the first build, and why it never runs again.
</link-summary>

<card-summary>
A working CMake project on first build, and yours to own from then on.
</card-summary>

<tldr>
<p><b>Task</b>: <code>kreateJniInitialize</code></p>
<p><b>Runs</b>: automatically, as a dependency of <code>kreateJniConfigure</code></p>
<p><b>Overwrites</b>: nothing that already exists</p>
</tldr>

The first time a JNI-enabled project is built, %product% creates a CMake project that compiles.
From then on that project is yours: %product% reads it and never rewrites it.

## What is generated

For a project named `my_module`:

```text
jni/
└── my_module/
    ├── CMakeLists.txt
    └── src/
        └── my_module.cpp
```

<procedure title="Run it manually" id="run-scaffold">
    <step>
        <p>Normally you never need to — it runs as part of the build. To scaffold explicitly:</p>
        <code-block lang="bash">./gradlew kreateJniInitialize</code-block>
    </step>
    <step>
        <p>
            The task is idempotent. Running it against an existing project creates only what is
            missing and leaves everything else untouched.
        </p>
    </step>
</procedure>

## The generated CMakeLists.txt

<code-block lang="cmake" collapsible="true" collapsed-title="jni/my_module/CMakeLists.txt">
<![CDATA[
cmake_minimum_required(VERSION 3.20)
project(my_module CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(JNI REQUIRED)

# CONFIGURE_DEPENDS re-evaluates the glob on every build system invocation, so a
# newly added source file is picked up without a manual reconfigure.
file(GLOB MY_MODULE_SOURCES CONFIGURE_DEPENDS "src/*.cpp" "src/*.cc")

add_library(my_module SHARED ${MY_MODULE_SOURCES})
target_include_directories(my_module PRIVATE
    ${JNI_INCLUDE_DIRS}
    include
    ${KREATE_JNI_INCLUDE_DIRS}
)
target_link_libraries(my_module PRIVATE ${JNI_LIBRARIES})
]]>
</code-block>

Each line earns its place:

<deflist type="wide">
    <def title="CMAKE_POSITION_INDEPENDENT_CODE">
        Required for a shared library on most platforms. Omitting it produces link errors that
        name relocation types rather than the actual problem.
    </def>
    <def title="find_package(JNI REQUIRED)">
        Locates <code>jni.h</code> and the JVM libraries. %product% passes
        <code>JAVA_HOME</code> so this resolves against your toolchain JDK rather than the
        machine default.
    </def>
    <def title="CONFIGURE_DEPENDS">
        Without it, CMake evaluates the glob once at configure time. A source file added later is
        then never compiled, and the failure looks like a missing symbol rather than a missing
        file.
    </def>
    <def title="KREATE_JNI_INCLUDE_DIRS">
        Carries the generated header directory and your configured
        <code>libraryIncludePaths</code>. Because it is a cache variable rather than a literal
        list, changing the Gradle configuration takes effect without anyone editing this file.
    </def>
</deflist>

## The placeholder source

```c++
#include <jni.h>

// Placeholder source for JNI project "my_module".
//
// Run `gradle kreateJniHeaders` to generate declarations for every `external`
// function in this module, then include the generated header here and implement
// the declared functions.
```

It exists so CMake has a translation unit on the very first build. Replace it with your
implementation — and swap the `<jni.h>` include for the generated header, which is what gives you
compile-time checking of your signatures.

## Ownership

<warning>
%product% never overwrites an existing <code>CMakeLists.txt</code> or source file. That is a
deliberate guarantee: a build tool that regenerates files you have edited is a build tool you
cannot use for anything non-trivial.
</warning>

The consequence is that a project scaffolded by an older version does not automatically gain new
generated content. `kreateJniInitialize` detects the most important case and warns:

```
jni/my_module/CMakeLists.txt does not reference ${KREATE_JNI_INCLUDE_DIRS}.
Add it to target_include_directories(...) so that generated JNI headers and
the configured libraryIncludePaths are visible to the compiler.
```

Add the variable and the warning goes away.

## Customising

Once scaffolded, the file is an ordinary CMake project. Common additions:

<tabs group="cmake-custom">
<tab title="Link a system library" group-key="link">

```cmake
find_package(ZLIB REQUIRED)
target_link_libraries(my_module PRIVATE ${JNI_LIBRARIES} ZLIB::ZLIB)
```

</tab>
<tab title="Add a subproject" group-key="subdir">

```cmake
add_subdirectory(vendor/fmt)
target_link_libraries(my_module PRIVATE ${JNI_LIBRARIES} fmt::fmt)
```

</tab>
<tab title="Compile definitions" group-key="defines">

```cmake
target_compile_definitions(my_module PRIVATE
    MY_MODULE_VERSION="1.0.0"
    $<$<CONFIG:Debug>:MY_MODULE_DEBUG>
)
```

</tab>
<tab title="Warnings as errors" group-key="warnings">

```cmake
if (MSVC)
    target_compile_options(my_module PRIVATE /W4 /WX)
else()
    target_compile_options(my_module PRIVATE -Wall -Wextra -Werror)
endif()
```

</tab>
</tabs>

<tip>
Keep <code>add_library(&lt;name&gt; SHARED ...)</code> matching the resolved project name. That
name is what <code>System.loadLibrary</code> receives and what the packaging step looks for.
</tip>

<seealso>
    <category ref="native">
        <a href="JNI-Support.md">JNI support</a>
        <a href="JNI-Configuration.md">Configuration reference</a>
        <a href="JNI-Build-Pipeline.md">Build pipeline</a>
    </category>
    <category ref="external">
        <a href="https://cmake.org/cmake/help/latest/">CMake documentation</a>
    </category>
</seealso>
