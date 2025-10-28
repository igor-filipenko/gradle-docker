package io.github.igor_filipenko.gradle.docker

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Plugin to build and push Docker images.
 */
class GradleDockerPlugin: Plugin<Project> {

    companion object {
        private val log: Logger = Logging.getLogger(GradleDockerPlugin::class.java)
        private val LABEL_KEY_PATTERN: Pattern = Pattern.compile("^[a-z0-9.-]*$")
        
        fun computeName(name: String, tag: String): String {
            val firstAt = tag.indexOf("@")

            val tagValue = if (firstAt > 0) {
                tag.substring(firstAt + 1, tag.length)
            } else {
                tag
            }

            return if (tagValue.contains(':') || tagValue.contains('/')) {
                // tag with ':' or '/' -> force use the tag value
                tagValue
            } else {
                // tag without ':' and '/' -> replace the tag part of original name
                val lastColon = name.lastIndexOf(':')
                val lastSlash = name.lastIndexOf('/')

                val endIndex = if (lastColon > lastSlash) lastColon else name.length

                name.substring(0, endIndex) + ":" + tagValue
            }
        }
        
        private fun generateTagTaskName(name: String): String {
            var tagTaskName = name
            val firstAt = name.indexOf("@")

            tagTaskName = if (firstAt > 0) {
                // Get substring of task name
                name.substring(0, firstAt)
            } else if (firstAt == 0) {
                // Task name must not be empty
                throw GradleException("Task name of docker tag '$name' must not be empty.")
            } else if (name.contains(':') || name.contains('/')) {
                // Tags which with repo or name must have a task name
                throw GradleException("Docker tag '$name' must have a task name.")
            } else {
                name
            }

            val sb = StringBuilder(tagTaskName)
            // Uppercase the first letter of task name
            sb.replace(0, 1, tagTaskName.substring(0, 1).uppercase())
            return sb.toString()
        }
    }

    private val objectFactory: ObjectFactory
    private val attributesFactory: AttributesFactory

    @Inject
    constructor(objectFactory: ObjectFactory, attributesFactory: AttributesFactory) {
        this.objectFactory = objectFactory
        this.attributesFactory = attributesFactory
    }

    override fun apply(project: Project) {
        val ext = project.extensions.create("docker", DockerExtension::class.java, project)
        if (project.configurations.findByName("docker") == null) {
            project.configurations.create("docker")
        }

        val clean = project.tasks.register("dockerClean", Delete::class.java) {
            it.group = "Docker"
            it.description = "Clean Docker build directory"
        }

        val prepare = project.tasks.register("dockerPrepare", Copy::class.java) {
            it.group = "Docker"
            it.description = "Prepares Docker build directory."
            it.dependsOn(clean)
        }

        val exec = project.tasks.register("docker", Exec::class.java) {
            it.group = "Docker"
            it.description = "Builds Docker image."
            it.dependsOn(prepare)
        }

        val tag = project.tasks.register("dockerTag") {
            it.group = "Docker"
            it.description = "Applies all tags to the Docker image."
            it.dependsOn(exec)
        }

        val pushAllTags = project.tasks.register("dockerTagsPush") {
            it.group = "Docker"
            it.description = "Pushes all tagged Docker images to configured Docker Hub."
        }

        project.tasks.register("dockerPush") {
            it.group = "Docker"
            it.description = "Pushes named Docker image to configured Docker Hub."
            it.dependsOn(pushAllTags)
        }

        val dockerfileZip = project.tasks.register("dockerfileZip", Zip::class.java) {
            it.group = "Docker"
            it.description = "Bundles the configured Dockerfile in a zip file"
        }

        val dockerConfiguration = project.configurations.getByName("docker")
        val dockerArtifact = project.artifacts.add("docker", dockerfileZip)
        project.components.add(DockerComponent(dockerArtifact, dockerConfiguration.allDependencies, objectFactory, attributesFactory))

        project.afterEvaluate {
            ext.resolvePathsAndValidate()
            val dockerDir = project.layout.buildDirectory.get().toString() + "/docker"
            clean.configure { it.delete(dockerDir) }

            prepare.configure { task ->
                task.with(ext.copySpec)
                ext.resolvedDockerfile?.let { dockerfile ->
                    task.from(dockerfile) {
                        it.rename { fileName ->
                            dockerfile.name.let { dockerfileName ->
                                fileName.replace(dockerfileName, "Dockerfile")
                            }
                        }
                    }
                }
                task.into(dockerDir)
            }

            exec.configure { task ->
                task.workingDir = project.file(dockerDir)
                task.commandLine = buildCommandLine(ext)
                task.dependsOn(ext.dependencies)
                task.logging.captureStandardOutput(LogLevel.INFO)
                task.logging.captureStandardError(LogLevel.ERROR)
            }

            val tags = mutableMapOf<String, TagConfig>()
            ext.namedTags.forEach { (taskName, tagName) ->
                tags[generateTagTaskName(taskName)] = TagConfig(
                    tagName = tagName,
                    tagTask = { tagName }
                )
            }

            if (ext.tags.isNotEmpty()) {
                ext.tags.forEach { unresolvedTagName ->
                    val taskName = generateTagTaskName(unresolvedTagName)

                    if (tags.containsKey(taskName)) {
                        throw IllegalArgumentException("Task name '$taskName' already exists.")
                    }

                    tags[taskName] = TagConfig(
                        tagName = unresolvedTagName,
                        tagTask = { computeName(ext.name, unresolvedTagName) }
                    )
                }
            }

            tags.forEach { (taskName, tagConfig) ->
                val tagSubTask = project.tasks.register("dockerTag$taskName", Exec::class.java) {
                    it.group = "Docker"
                    it.description = "Tags Docker image with tag '${tagConfig.tagName}'"
                    it.workingDir = project.file(dockerDir)
                    it.dependsOn(exec)
                    val commandLineProvider = project.provider { listOf("docker", "tag", ext.name, tagConfig.tagTask()) }
                    val task = it
                    it.doFirst {
                        task.commandLine = commandLineProvider.get()
                    }
                }
                tag.get().dependsOn(tagSubTask)

                val pushSubTask = project.tasks.register("dockerPush$taskName", Exec::class.java) {
                    it.group = "Docker"
                    it.description = "Pushes the Docker image with tag '${tagConfig.tagName}' to configured Docker Hub"
                    it.workingDir = project.file(dockerDir)
                    it.dependsOn(tagSubTask)
                    val commandLineProvider = project.provider { listOf("docker", "push", tagConfig.tagTask()) }
                    val task = it
                    it.doFirst {
                        task.commandLine = commandLineProvider.get()
                    }
                }
                pushAllTags.get().dependsOn(pushSubTask)
            }
        }
    }

    private fun buildCommandLine(ext: DockerExtension): List<String> {
        val buildCommandLine = mutableListOf("docker")
        if (ext.buildx) {
            buildCommandLine.addAll(listOf("buildx", "build"))
            if (ext.platform.isNotEmpty()) {
                buildCommandLine.addAll(listOf("--platform", ext.platform.joinToString(",")))
            }
            if (ext.load) {
                buildCommandLine.add("--load")
            }
            if (ext.push) {
                buildCommandLine.add("--push")
                if (ext.load) {
                    throw Exception("cannot combine 'push' and 'load' options")
                }
            }
            if (ext.builder != null) {
                buildCommandLine.addAll(listOf("--builder", ext.builder!!))
            }
        } else {
            buildCommandLine.add("build")
        }
        if (ext.noCache) {
            buildCommandLine.add("--no-cache")
        }
        if (ext.network != null) {
            buildCommandLine.addAll(listOf("--network", ext.network!!))
        }
        if (ext.buildArgs.isNotEmpty()) {
            for ((key, value) in ext.buildArgs) {
                buildCommandLine.addAll(listOf("--build-arg", "$key=$value"))
            }
        }
        if (ext.labels.isNotEmpty()) {
            for ((key, value) in ext.labels) {
                if (!LABEL_KEY_PATTERN.matcher(key).matches()) {
                    throw GradleException(
                        String.format(
                            "Docker label '%s' contains illegal characters. " +
                                    "Label keys must only contain lowercase alphanumberic, `.`, or `-` characters (must match %s).",
                            key, LABEL_KEY_PATTERN.pattern()
                        )
                    )
                }
                buildCommandLine.addAll(listOf("--label", "$key=$value"))
            }
        }
        if (ext.pull) {
            buildCommandLine.add("--pull")
        }
        buildCommandLine.addAll(listOf("-t", ext.name, "."))
        log.debug("Using command line: {}", buildCommandLine)
        return buildCommandLine
    }



    private data class TagConfig(
        val tagName: String,
        val tagTask: () -> String
    )

}
