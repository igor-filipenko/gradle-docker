package ru.crystals.gradle.docker

import org.gradle.internal.impldep.com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.internal.impldep.com.google.api.client.util.Preconditions
import org.gradle.internal.impldep.com.google.api.client.util.Strings
import org.gradle.internal.impldep.com.google.common.collect.ImmutableMap
import org.gradle.internal.impldep.com.google.common.collect.Sets
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import java.io.File

class DockerExtension(project: Project) {
    val project: Project

    companion object {
        private const val DEFAULT_DOCKERFILE_PATH = "Dockerfile"
    }

    var name: String? = null
    var dockerfile: File? = null
    var dockerComposeTemplate: String = "docker-compose.yml.template"
    var dockerComposeFile: String = "docker-compose.yml"
    var dependencies: Set<Task> = ImmutableSet.of()
    var tags: Set<String> = ImmutableSet.of()
    val namedTags: MutableMap<String, String> = HashMap()
    var labels: Map<String, String> = ImmutableMap.of()
    var buildArgs: Map<String, String> = ImmutableMap.of()
    var pull: Boolean = false
    var noCache: Boolean = false
    var network: String? = null
    var buildx: Boolean = false
    var platform: Set<String> = ImmutableSet.of()
    var load: Boolean = false
    var push: Boolean = false
    var builder: String? = null

    private var resolvedDockerfile: File? = null
    private var resolvedDockerComposeTemplate: File? = null
    private var resolvedDockerComposeFile: File? = null

    // The CopySpec defining the Docker Build Context files
    private val copySpec: CopySpec

    init {
        this.project = project
        this.copySpec = project.copySpec()
    }

    fun setName(name: String) {
        this.name = name
    }

    fun getName(): String {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is a required docker configuration item.")
        return name!!
    }

    fun setDockerfile(dockerfile: File) {
        this.dockerfile = dockerfile
    }

    fun setDockerComposeTemplate(dockerComposeTemplate: String) {
        this.dockerComposeTemplate = dockerComposeTemplate
        Preconditions.checkArgument(
            project.file(dockerComposeTemplate).exists(),
            "Could not find specified template file: %s", project.file(dockerComposeTemplate)
        )
    }

    fun setDockerComposeFile(dockerComposeFile: String) {
        this.dockerComposeFile = dockerComposeFile
    }

    fun dependsOn(vararg args: Task) {
        this.dependencies = ImmutableSet.copyOf(args)
    }

    fun getDependencies(): Set<Task> {
        return dependencies
    }

    fun files(vararg files: Any) {
        copySpec.from(*files)
    }

    fun getTags(): Set<String> {
        return Sets.union(this.tags, ImmutableSet.of(project.version.toString()))
    }

    @Deprecated("")
    fun tags(vararg args: String) {
        this.tags = ImmutableSet.copyOf(args)
    }

    fun getNamedTags(): Map<String, String> {
        return ImmutableMap.copyOf(namedTags)
    }

    fun getLabels(): Map<String, String> {
        return labels
    }

    fun labels(labels: Map<String, String>) {
        this.labels = ImmutableMap.copyOf(labels)
    }

    fun getResolvedDockerfile(): File? {
        return resolvedDockerfile
    }

    fun getResolvedDockerComposeTemplate(): File? {
        return resolvedDockerComposeTemplate
    }

    fun getResolvedDockerComposeFile(): File? {
        return resolvedDockerComposeFile
    }

    fun getCopySpec(): CopySpec {
        return copySpec
    }

    fun resolvePathsAndValidate() {
        resolvedDockerfile = dockerfile ?: project.file(DEFAULT_DOCKERFILE_PATH)
        resolvedDockerComposeFile = project.file(dockerComposeFile)
        resolvedDockerComposeTemplate = project.file(dockerComposeTemplate)
    }

    fun getBuildArgs(): Map<String, String> {
        return buildArgs
    }

    fun getNetwork(): String? {
        return network
    }

    fun setNetwork(network: String) {
        this.network = network
    }

    fun buildArgs(buildArgs: Map<String, String>) {
        this.buildArgs = ImmutableMap.copyOf(buildArgs)
    }

    fun getPull(): Boolean {
        return pull
    }

    fun pull(pull: Boolean) {
        this.pull = pull
    }

    fun getNoCache(): Boolean {
        return noCache
    }

    fun noCache(noCache: Boolean) {
        this.noCache = noCache
    }

    fun getLoad(): Boolean {
        return load
    }

    fun load(load: Boolean) {
        this.load = load
    }

    fun getPush(): Boolean {
        return push
    }

    fun push(push: Boolean) {
        this.push = push
    }

    fun getBuildx(): Boolean {
        return buildx
    }

    fun buildx(buildx: Boolean) {
        this.buildx = buildx
    }

    fun getPlatform(): Set<String> {
        return platform
    }

    fun platform(vararg args: String) {
        this.platform = ImmutableSet.copyOf(args)
    }

    fun getBuilder(): String? {
        return builder
    }

    fun builder(builder: String) {
        this.builder = builder
    }
}