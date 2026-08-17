# Publishing

<link-summary>Publishing signed artifacts to Maven Central and GitLab.</link-summary>

<card-summary>Repositories, signing, and POM metadata from one block.</card-summary>

<tldr>
<p><b>Enable</b>: <code>project { publish { enabled = true } }</code></p>
<p><b>Requires</b>: the <code>com.vanniktech.maven.publish</code> plugin applied by you</p>
</tldr>

The `publish { }` block inside `kreate { project { } }` integrates artifact publishing into your
Gradle build. Kreate supports two publish targets out of the box:

- **Maven Central** via the [Gradle Maven Publish Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) by Vanniktech
- **GitLab Package Registry** via the standard Gradle `maven-publish` plugin with CI job token authentication

Publishing is **disabled by default**. To use it, you must manually apply the required plugins and then enable it in Kreate.

### Required Plugins

For GitLab publishing, apply the standard `maven-publish` plugin:

```kotlin
plugins {
    `maven-publish`
}
```

For Maven Central publishing, apply the [Vanniktech Maven Publish Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/):

```kotlin
plugins {
    id("com.vanniktech.maven.publish") version "0.30.0" // Use the latest version
}
```

### Kreate Configuration

Enable the publishing module:

```kotlin
kreate {
    project {
        publish {
            enabled = true
        }
    }
}
```

> The `publish { }` block is evaluated inside `afterEvaluate`. All properties and nested
> blocks must be configured before the Gradle configuration phase ends.
>
{style="note"}

## How It Works

When `enabled` is `true`, Kreate evaluates each repository target independently. A repository
is only configured when its own `enabled` property is also `true`. The two targets are
completely independent and can be active simultaneously.

```
publish { enabled = true }
│
├── repositories.mavenCentral { enabled = true }
│ └── requires com.vanniktech.maven.publish plugin
│ └── calls publishToMavenCentral(automaticRelease)
│ └── calls signAllPublications() if signPublications = true
│ └── sets coordinates(group, name, version)
│ └── configures pom { ... }
│
└── repositories.gitlab { enabled = true }
└── requires maven-publish plugin
└── reads CI_JOB_TOKEN, CI_PROJECT_ID, CI_API_V4_URL from env
└── registers Maven repository with HttpHeaderAuthentication
└── configures pom { ... } on all MavenPublication tasks
```

## Top-Level Properties

| Property        | Type                | Default     | Description                                                        |
|-----------------|---------------------|-------------|--------------------------------------------------------------------|
| `enabled`       | `Property<Boolean>` | `false`     | Master switch — must be `true` for any publishing to be configured |
| `inceptionYear` | `Property<Int>`     | `2024`      | Written into the POM `<inceptionYear>` field                       |
| `website`       | `Property<String>`  | *(not set)* | Written into the POM `<url>` field                                 |

All POM metadata is shared between both Maven Central and GitLab publish targets. Configure
it once in `pom { }` and Kreate applies it to both.


<seealso>
    <category ref="project">
        <a href="Publishing-Maven-Central.md">Maven Central</a>
        <a href="Publishing-Gitlab-Registry.md">GitLab Package Registry</a>
        <a href="Publishing-POM-Configuration.md">POM configuration</a>
        <a href="Publishing-Examples.md">Examples</a>
    </category>
</seealso>
