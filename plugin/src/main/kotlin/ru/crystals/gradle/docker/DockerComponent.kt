package ru.crystals.gradle.docker

import org.gradle.api.artifacts.*
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.model.ObjectFactory
import java.util.*

class DockerComponent(
    docketArtifact: PublishArtifact,
    private val runtimeDependencies: DependencySet,
    objectFactory: ObjectFactory,
    attributesFactory: AttributesFactory
) : SoftwareComponentInternal {

    private val runtimeUsage: UsageContext
    private val artifacts: MutableSet<PublishArtifact> = LinkedHashSet()

    init {
        artifacts.add(docketArtifact)
        val usage = objectFactory.named(Usage::class.java, Usage.JAVA_RUNTIME)
        val attributes = attributesFactory.of(Usage.USAGE_ATTRIBUTE, usage)
        runtimeUsage = RuntimeUsageContext(usage, attributes)
    }

    override fun getName(): String = "docker"

    override fun getUsages(): Set<UsageContext> = setOf(runtimeUsage)

    private inner class RuntimeUsageContext(
        private val usage: Usage,
        private val attributes: ImmutableAttributes
    ) : UsageContext {

        fun getUsage(): Usage = usage

        override fun getArtifacts(): Set<PublishArtifact> = artifacts

        override fun getDependencies(): Set<ModuleDependency> =
            runtimeDependencies.withType(ModuleDependency::class.java)

        override fun getName(): String = "runtime"

        override fun getAttributes(): AttributeContainer = attributes

        override fun getDependencyConstraints(): Set<DependencyConstraint> = emptySet()

        override fun getCapabilities(): Set<Capability> = emptySet()

        override fun getGlobalExcludes(): Set<ExcludeRule> = emptySet()
    }
}