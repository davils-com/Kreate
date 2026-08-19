import com.davils.kreate.module.platform.multiplatform.cinterop.NativeLanguage
import com.davils.kreate.module.trivy.LicenseSeverity
import com.davils.kreate.module.trivy.SecretSeverity
import com.davils.kreate.module.trivy.Score
import java.time.Year

plugins {
    alias(libs.plugins.kreate)
    id("dev.detekt") version "2.0.0-alpha.6"
    kotlin("jvm") version "2.4.10"
    application
}

application {
    mainClass = "com.davils.example.JNIKt"
}

group = "com.example"

detekt {
    // Detekt would otherwise auto-discover config/detekt/detekt.yml from the repository
    // root, which is Kreate's own strict configuration and needs rule set plugins that a
    // consumer does not have.
    config.setFrom(rootProject.file("config/detekt/detekt-consumer.yml"))
    buildUponDefaultConfig = true
}

kreate {
    platform {
        javaVersion = JavaVersion.VERSION_17
        explicitApi = true
        allWarningsAsErrors = false

        multiplatform {
            cInterop {
                enabled = false
                // Selects the native interop language: RUST (default), C or CPP.
                language = NativeLanguage.RUST
                nameOverride = "example"
                projectDirectory = file("cinterop")
                packageNameOverride.set("com.davils.example.cinterop")
                rustTargets = listOf("x86_64-unknown-linux-gnu")

                defFile {
                    fileName = "cinterop.def"
                    dirName = "defs"
                }

                mingw {
                    // Configure MinGW-specific settings here
                }

                linux {
                    // Configure Linux-specific settings here
                }

                macos {
                    // Configure macOS-specific settings here
                }
            }
        }

        jvm {
            jni {
                enabled = true
                projectDirectory = layout.projectDirectory.dir("jni")
                nameOverride = "example"
                buildType = "Release"
                libraryRuntimePaths = listOf()
                libraryIncludePaths = listOf()

                headers {
                    // Generates example_jni.h from the `external` declarations in JNI.kt.
                    enabled = true
                }

                packaging {
                    // Puts libexample.so into the JAR under natives/<os>-<arch>/ and
                    // generates KreateNativeLoader so the artifact works without
                    // -Djava.library.path.
                    enabled = true
                    generateLoader = true
                }
            }
        }
    }

    trivy {
        enabled = true

        vulnerability {
            score = listOf(Score.CRITICAL, Score.HIGH, Score.MEDIUM, Score.LOW)
            failOnFindings = true
            lockFiles.from(
                fileTree(projectDir) {
                    include("*.lockfile")
                }
            )
        }

        license {
            severity = listOf(LicenseSeverity.CRITICAL, LicenseSeverity.HIGH, LicenseSeverity.UNKNOWN)
            failOnForbidden = true
            ignoredLicenses = listOf("MIT")
            lockFiles.from(
                fileTree(projectDir) {
                    include("*.lockfile")
                }
            )
        }

        secrets {
            severity = listOf(SecretSeverity.CRITICAL, SecretSeverity.HIGH, SecretSeverity.MEDIUM, SecretSeverity.LOW)
            failOnFindings = true
            secretConfig = rootProject.layout.projectDirectory.file("trivy-secret.yaml")
            sourceFiles.from(
                fileTree(projectDir) {
                    include("src/**/*.kt", "src/**/*.java", "**/*.yaml", "**/*.yml", "**/*.env", "**/*.properties", "**/*.json")
                }
            )
        }
    }

    project {
        name = "Example"
        description = "Example project"

        dependencyLocking {
            // Locks `compileClasspath` and `runtimeClasspath` only — see the property's
            // documentation for why locking everything makes the Trivy scans below useless.
            enabled = true
        }

        apiValidation {
            // Records the public API in example/api/example.api. `kreateApiCheck` runs as
            // part of `check`.
            enabled = true
        }

        version {
            environment = "CI_COMMIT_TAG"
            property = "version"
        }

        buildConstant {
            enabled = true
            className = "ExampleConstants"
            path = "generated/compile"

            constant("example", "value")
            constant("example2", 1)
        }

        docs {
            enabled = true
            outputDirectory = "docs"
            moduleName = "Example"
            copyright = "Copyright ${Year.now()} Example"
        }

        tests {
            enabled = false
            maxParallelForks = Runtime.getRuntime().availableProcessors()
            timeoutMinutes = 10L
            ignoreFailures = false
            alwaysRunTests = false
            failOnNoDiscoveredTests = false

            logging {
                logPassedTests = true
                logSkippedTests = true
                logTestStarted = true
            }

            report {
                enabled = true
                xml = true
                html = true
            }
        }

        detekt {
            enabled = false
            buildUponDefaultConfig = true
            allRules = true
            config = rootProject.file("config/detekt/detekt-consumer.yml")

            reports {
                checkstyle {
                    required = true
                    outputLocation = layout.buildDirectory.file("reports/detekt/checkstyle.xml")
                }

                html {
                    required = true
                    outputLocation = layout.buildDirectory.file("reports/detekt/html.html")
                }

                markdown {
                    required = true
                    outputLocation = layout.buildDirectory.file("reports/detekt/markdown.md")
                }

                sarif {
                    required = true
                    outputLocation = layout.buildDirectory.file("reports/detekt/sarif.sarif")
                }
            }
        }

        publish {
            enabled = false
            inceptionYear = 2026
            website = "https://example.com"

            pom {
                issueManagement {
                    system = "Github Issues"
                    url = "https://github.com/example/issues"
                }

                ciManagement {
                    system = "Github Actions"
                    url = "https://github.com/example/actions"
                }

                licenses {
                    license {
                        name = "Apache 2.0"
                        url = "https://github.com/example/example-project/blob/main/LICENSE"
                        distribution = "repo"
                    }
                }

                developers {
                    developer {
                        id = "example"
                        name = "Example"
                        email = "example@example.com"
                        organization = "Example"
                        timezone = "Europe/Berlin"
                    }
                }

                scm {
                    url = "https://github.com/example/example-project.git"
                    connection = "scm:git:https://github.com/example/example-project.git"
                    developerConnection = "scm:git:ssh://git@github.com:example/example-project.git"
                }
            }

            repositories {
                gitlab {
                    enabled = true
                    name = "ExampleInstance"
                    tokenEnv = "CI_JOB_TOKEN"
                    projectIdEnv = "CI_PROJECT_ID"
                    apiUrlEnv = "CI_API_V4_URL"
                }

                mavenCentral {
                    enabled = true
                    automaticRelease = true
                    signPublications = true
                }
            }
        }
    }
}
