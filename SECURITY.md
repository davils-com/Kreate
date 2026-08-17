# Security Policy

## Supported Versions

Security fixes are released for the latest minor version of the current major release.

| Version | Supported        |
|---------|------------------|
| 2.0.x   | Yes              |
| 1.3.x   | Until 2026-12-31 |
| < 1.3   | No               |

## Reporting a Vulnerability

**Do not open a public issue for a security problem.**

Report it privately through GitHub's
[private vulnerability reporting](https://github.com/davils-com/kreate/security/advisories/new),
or by email to development@davils.com.

Please include:

- the affected Kreate version and the Gradle version you are running,
- a description of the impact — what an attacker gains,
- the steps to reproduce it, ideally as a minimal build script,
- any mitigation you are already aware of.

## Scope

Kreate is a build plugin: it runs with the full privileges of the developer or CI agent
executing the build, and it deliberately invokes external toolchains (CMake, Cargo, Trivy).

The following are **in scope**:

- the plugin resolving or executing an unexpected binary — for example an executable path
  that can be influenced by untrusted repository content,
- credentials configured through the publishing DSL being written to logs, reports, or
  build outputs,
- generated code or build files that introduce a vulnerability into a consumer's artifact.

The following are **not** vulnerabilities in Kreate:

- a vulnerability in CMake, Cargo, Trivy, Gradle, or the Kotlin compiler itself — report
  those to the respective project,
- Kreate executing a malicious `CMakeLists.txt` that is already part of the repository
  being built; a build script is trusted code by definition,
- findings that require an attacker to already have write access to the build.

## Verifying a Release

Releases are published to Maven Central and signed. The plugin JAR is built reproducibly:
building the same tag twice produces byte-identical archives, and CI verifies this on every
run, so a published artifact can be checked against its source.
