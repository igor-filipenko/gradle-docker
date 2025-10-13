Docker Gradle Plugin
====================

A lightweight Gradle plugin for building and pushing Docker images, designed as a modern replacement for the [Palantir Gradle Docker Plugin](https://github.com/palantir/gradle-docker) which has reached end-of-life.

## 🚀 Features

- **Modern Foundation**: Rewritten in Kotlin for better maintainability and type safety
- **Gradle 9 Compatibility**: Built specifically for Gradle 9 and above
- **Focused Scope**: Dedicated to Docker image building and pushing operations
- **Lightweight**: Minimal dependencies and clean API

## ⚡ Quick Start

# Applying the Plugin

**Groovy DSL:**
```gradle
apply plugin: 'ru.crystals.docker'

docker {
    name "myapp"
    copySpec.from("build/libs").into("build/libs")
    dockerfile new File("Dockerfile".toString())
}
```

**Docker Configuration Parameters**
- `name` the name to use for this container, may include a tag
- `tags` (deprecated) (optional) an argument list of tags to create; any tag in `name` will
  be stripped before applying a specific tag; defaults to the empty set
- `tag` (optional) a tag to create with a specified task name
- `dockerfile` (optional) the dockerfile to use for building the image; defaults to
  `project.file('Dockerfile')` and must be a file object
- `files` (optional) an argument list of files to be included in the Docker build context

**Gradle tasks**
* `docker`: build a docker image with the specified name and Dockerfile
* `dockerTag`: tag the docker image with all specified tags
* `dockerTag<tag>`: tag the docker image with `<tag>`
* `dockerPush`: push the specified image to a docker repository
* `dockerPush<tag>`: push the `<tag>` docker image to a docker repository
* `dockerTagsPush`: push all tagged docker images to a docker repository
* `dockerPrepare`: prepare to build a docker image by copying
  dependent task outputs, referenced files, and `dockerfile` into a temporary directory
* `dockerClean`: remove temporary directory associated with the docker build
* `dockerfileZip`: builds a ZIP file containing the configured Dockerfile

## 📝 Notes
- Removed Features: This plugin intentionally removes docker-compose and docker-run functionality to maintain focus
- Gradle Compatibility: Requires Gradle 9.0 or higher

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
