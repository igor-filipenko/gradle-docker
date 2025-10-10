package ru.crystals.gradle.docker

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import java.io.File

open class DockerExtension(project: Project) {
    val project: Project

    companion object {
        private const val DEFAULT_DOCKERFILE_PATH = "Dockerfile"
    }

    var name: String? = null
    var dockerfile: File? = null
    var dockerComposeTemplate: String = "docker-compose.yml.template"
    var dockerComposeFile: String = "docker-compose.yml"
    var dependencies: Set<Task> = emptySet()
    var tags: Set<String> = emptySet()
    val namedTags: MutableMap<String, String> = HashMap()
    var labels: Map<String, String> = emptyMap()
    var buildArgs: Map<String, String> = emptyMap()
    var pull: Boolean = false
    var noCache: Boolean = false
    var network: String? = null
    var buildx: Boolean = false
    var platform: Set<String> = emptySet()
    var load: Boolean = false
    var push: Boolean = false
    var builder: String? = null

    var resolvedDockerfile: File? = null
    private var resolvedDockerComposeTemplate: File? = null
    private var resolvedDockerComposeFile: File? = null

    // The CopySpec defining the Docker Build Context files
    val copySpec: CopySpec

    init {
        this.project = project
        this.copySpec = project.copySpec()
    }

    fun dependsOn(vararg args: Task) {
        this.dependencies = args.toSet()
    }

    fun files(vararg files: Any) {
        copySpec.from(*files)
    }

    @Deprecated("")
    fun tags(vararg args: String) {
        this.tags = args.toSet()
    }

    fun labels(labels: Map<String, String>) {
        this.labels = labels.toMap()
    }

    fun getResolvedDockerComposeTemplate(): File? {
        return resolvedDockerComposeTemplate
    }

    fun getResolvedDockerComposeFile(): File? {
        return resolvedDockerComposeFile
    }

    fun resolvePathsAndValidate() {
        if (name.isNullOrEmpty()) {
            throw org.gradle.api.GradleException("name is a required docker configuration item.")
        }
        resolvedDockerfile = dockerfile ?: project.file(DEFAULT_DOCKERFILE_PATH)
        resolvedDockerComposeFile = project.file(dockerComposeFile)
        resolvedDockerComposeTemplate = project.file(dockerComposeTemplate)
    }


    fun buildArgs(buildArgs: Map<String, String>) {
        this.buildArgs = buildArgs.toMap()
    }

    fun pull(pull: Boolean) {
        this.pull = pull
    }

    fun noCache(noCache: Boolean) {
        this.noCache = noCache
    }

    fun load(load: Boolean) {
        this.load = load
    }

    fun push(push: Boolean) {
        this.push = push
    }

    fun buildx(buildx: Boolean) {
        this.buildx = buildx
    }

    fun platform(vararg args: String) {
        this.platform = args.toSet()
    }

    fun builder(builder: String) {
        this.builder = builder
    }
}