package io.github.igor_filipenko.gradle.docker

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertNotNull

class GradleDockerPluginTest {

    @Test fun `plugin registers task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.igor_filipenko.docker")

        assertNotNull(project.tasks.findByName("docker"))
        assertNotNull(project.tasks.findByName("dockerClean"))
        assertNotNull(project.tasks.findByName("dockerPush"))
    }

    @ParameterizedTest
    @CsvSource(
        "v1, latest, v1:latest",
        "v1:1, latest, v1:latest",
        "host/v1, latest, host/v1:latest",
        "host/v1:1, latest, host/v1:latest",
        "host:port/v1, latest, host:port/v1:latest",
        "host:port/v1:1, latest, host:port/v1:latest",
        "v1, name@latest, v1:latest",
        "v1:1, name@latest, v1:latest",
        "host/v1, name@latest, host/v1:latest",
        "host/v1:1, name@latest, host/v1:latest",
        "host:port/v1, name@latest, host:port/v1:latest",
        "host:port/v1:1, name@latest, host:port/v1:latest",
        "v1, name@v2:latest, v2:latest",
        "v1:1, name@v2:latest, v2:latest",
        "host/v1, name@v2:latest, v2:latest",
        "host/v1:1, name@v2:latest, v2:latest",
        "host:port/v1, name@v2:latest, v2:latest",
        "host:port/v1:1, name@v2:latest, v2:latest",
        "v1, name@host/v2, host/v2",
        "v1:1, name@host/v2, host/v2",
        "host/v1, name@host/v2, host/v2",
        "host/v1:1, name@host/v2, host/v2",
        "host:port/v1, name@host/v2, host/v2",
        "host:port/v1:1, name@host/v2, host/v2",
        "v1, name@host/v2:2, host/v2:2",
        "v1:1, name@host/v2:2, host/v2:2",
        "host/v1, name@host/v2:2, host/v2:2",
        "host/v1:1, name@host/v2:2, host/v2:2",
        "host:port/v1, name@host/v2:2, host/v2:2",
        "host:port/v1:1, name@host/v2:2, host/v2:2",
        "v1, name@host:port/v2:2, host:port/v2:2",
        "v1:1, name@host:port/v2:2, host:port/v2:2",
        "host/v1, name@host:port/v2:2, host:port/v2:2",
        "host/v1:1, name@host:port/v2:2, host:port/v2:2",
        "host:port/v1, name@host:port/v2:2, host:port/v2:2",
        "host:port/v1:1, name@host:port/v2:2, host:port/v2:2"
    )
    fun `check if compute name replaces the name correctly`(name: String, tag: String, result: String) {
        assertEquals(result, GradleDockerPlugin.computeName(name, tag))
    }

}
