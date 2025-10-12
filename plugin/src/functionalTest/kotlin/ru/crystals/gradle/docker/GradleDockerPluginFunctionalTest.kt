/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.crystals.gradle.docker

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Ignore

class GradleDockerPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    @Test
    fun `fail when missing docker configuration`() {
        // given
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }
        """.trimIndent())

        // when
        val buildResult = with("docker").buildAndFail()

        // then
        assertTrue(buildResult.output.contains("name is a required docker configuration item."))
    }

    @Test
    fun `fail with empty container name`() {
        // given
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }
            docker {
                name ''
            }
        """.trimIndent())

        // when
        val buildResult = with("docker").buildAndFail()

        // then
        assertTrue(buildResult.output.contains("name is a required docker configuration item."))
    }

    @Test
    fun `check plugin creates a docker container with default configuration`() {
        // given
        val id = "id1"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
            }
        """.trimIndent())

        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `check plugin creates a docker container with non-standard Dockerfile name`() {
        // given
        val id = "id2"
        file("foo").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                dockerfile project.file("foo")
            }
        """.trimIndent())

        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `check files are correctly added to docker context`() {
        // given
        val id = "id3"
        val filename = "foo.txt"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
            ADD ${filename} /tmp/
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                files "${filename}"
            }
        """.trimIndent())
        createFile(filename)
        
        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `check multiarch`() {
        // given
        val id = "id4"
        val filename = "foo.txt"
        file("Dockerfile").writeText("""
            FROM alpine
            MAINTAINER ${id}
            ADD ${filename} /tmp/
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }
            docker {
                name '${id}'
                files "${filename}"
                buildx true
                load true
                platform 'linux/arm64'
            }
        """.trimIndent())
        createFile(filename)
        
        // when
        val buildResult = with("docker").build()
        
        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertEquals("'arm64'\n", exec("docker inspect --format '{{.Architecture}}' ${id}"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `can apply docker plugin`() {
        // given
        file("Dockerfile").writeText("Foo")
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name 'foo'
            }
        """.trimIndent())

        // when
        val buildResult = with("tasks").build()
        
        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":tasks")?.outcome)
    }

    @Test
    fun `tag and push tasks created for each tag`() {
        // given
        val id = "id5"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                tags 'latest', 'another', 'withTaskName@2.0', 'newImageName@${id}-new:latest'
                tag 'withTaskNameByTag', '${id}:new-latest'
            }
        """.trimIndent())

        // when
        val buildResult = with("tasks").build()

        // then
        assertTrue(buildResult.output.contains("dockerTagLatest"))
        assertTrue(buildResult.output.contains("dockerTagAnother"))
        assertTrue(buildResult.output.contains("dockerTagWithTaskName"))
        assertTrue(buildResult.output.contains("dockerTagNewImageName"))
        assertTrue(buildResult.output.contains("dockerTagWithTaskNameByTag"))
        assertTrue(buildResult.output.contains("dockerPushLatest"))
        assertTrue(buildResult.output.contains("dockerPushAnother"))
        assertTrue(buildResult.output.contains("dockerPushWithTaskName"))
        assertTrue(buildResult.output.contains("dockerPushNewImageName"))
        assertTrue(buildResult.output.contains("dockerPushWithTaskNameByTag"))
    }

    @Test @Ignore
    fun `does not throw if name is configured after evaluation phase`() {
        // given
        val id = "id6"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                tags 'latest', 'another', 'withTaskName@2.0', 'newImageName@${id}-new:latest'
                tag 'withTaskNameByTag', '${id}:new-latest'
            }

            afterEvaluate {
                docker.name = '${id}'
            }
        """.trimIndent())

        // when
        val buildResult = with("dockerTag").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerTag")?.outcome)
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:latest"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:another"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:2.0"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}-new:latest"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:new-latest"))
        execCond("docker rmi -f ${id}")
        execCond("docker rmi -f ${id}:another")
        execCond("docker rmi -f ${id}:latest")
        execCond("docker rmi -f ${id}:2.0")
        execCond("docker rmi -f ${id}-new:latest")
        execCond("docker rmi -f ${id}:new-latest")
    }

    @Test @Ignore
    fun `running tag task creates images with specified tags`() {
        // given
        val id = "id6"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name 'fake-service-name'
                tags 'latest', 'another', 'withTaskName@2.0', 'newImageName@${id}-new:latest'
                tag 'withTaskNameByTag', '${id}:new-latest'
            }

            afterEvaluate {
                docker.name = '${id}'
            }

            task printInfo {
                doLast {
                    println "LATEST: $${""}{tasks.dockerTagLatest.commandLine}"
                    println "ANOTHER: $${""}{tasks.dockerTagAnother.commandLine}"
                    println "WITH_TASK_NAME: $${""}{tasks.dockerTagWithTaskName.commandLine}"
                    println "NEW_IMAGE_NAME: $${""}{tasks.dockerTagNewImageName.commandLine}"
                    println "WITH_TASK_NAME_BY_TAG: $${""}{tasks.dockerTagWithTaskNameByTag.commandLine}"
                }
            }
        """.trimIndent())

        // when
        val buildResult = with("dockerTag", "printInfo").build()

        // then
        assertTrue(buildResult.output.contains("LATEST: [docker, tag, ${id}, ${id}:latest]"))
        assertTrue(buildResult.output.contains("ANOTHER: [docker, tag, ${id}, ${id}:another]"))
        assertTrue(buildResult.output.contains("WITH_TASK_NAME: [docker, tag, ${id}, ${id}:2.0]"))
        assertTrue(buildResult.output.contains("NEW_IMAGE_NAME: [docker, tag, ${id}, ${id}-new:latest]"))
        assertTrue(buildResult.output.contains("WITH_TASK_NAME_BY_TAG: [docker, tag, ${id}, ${id}:new-latest]"))
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerTag")?.outcome)
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:latest"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:another"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:2.0"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}-new:latest"))
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}:new-latest"))
        execCond("docker rmi -f ${id}")
        execCond("docker rmi -f ${id}:latest")
        execCond("docker rmi -f ${id}:another")
        execCond("docker rmi -f ${id}:2.0")
        execCond("docker rmi -f ${id}-new:latest")
        execCond("docker rmi -f ${id}:new-latest")
    }

    @Test
    fun `build args are correctly processed`() {
        // given
        val id = "id7"
        file("Dockerfile").writeText("""
            FROM alpine:latest
            ARG BUILD_ARG_NO_DEFAULT
            ARG BUILD_ARG_WITH_DEFAULT=defaultBuildArg
            ENV ENV_BUILD_ARG_NO_DEFAULT $${""}BUILD_ARG_NO_DEFAULT
            ENV ENV_BUILD_ARG_WITH_DEFAULT $${""}BUILD_ARG_WITH_DEFAULT
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                buildArgs([BUILD_ARG_NO_DEFAULT: 'gradleBuildArg', BUILD_ARG_WITH_DEFAULT: 'gradleOverrideBuildArg'])
            }
        """.trimIndent())

        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        val envOutput = exec("docker inspect --format '{{.Config.Env}}' ${id}")
        assertTrue(envOutput.contains("ENV_BUILD_ARG_NO_DEFAULT=gradleBuildArg"))
        assertTrue(envOutput.contains("BUILD_ARG_WITH_DEFAULT=gradleOverrideBuildArg"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `rebuilding an image does it from scratch when noCache parameter is set`() {
        // given
        val id = "id66"
        val filename = "bar.txt"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            ADD ${filename} /tmp/
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                files "${filename}"
                noCache true
            }
        """.trimIndent())
        createFile(filename)

        // when
        val buildResult1 = with("--info", "docker").build()
        val imageID1 = exec("docker inspect --format=\"{{.Id}}\" ${id}")
        val buildResult2 = with("--info", "docker").build()
        val imageID2 = exec("docker inspect --format=\"{{.Id}}\" ${id}")

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult1.task(":docker")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult2.task(":docker")?.outcome)
        assertNotEquals(imageID1, imageID2)
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `base image is pulled when pull parameter is set`() {
        // given
        val id = "id8"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                pull true
            }
        """.trimIndent())

        // when
        execCond("docker pull alpine:3.2")
        val buildResult = with("-i", "docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertTrue(buildResult.output.contains("load metadata for docker.io/library/alpine"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `can build docker with network mode configured`() {
        // given
        val id = "id11"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            RUN curl localhost:404
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                // this should trigger the error because this is invalid option
                // thus it is possible to validate the --network was set correctly
                network 'foobar'
            }
        """.trimIndent())

        // when
        val buildResult = with("-i", "docker").buildAndFail()

        // then
        assertEquals(TaskOutcome.FAILED, buildResult.task(":docker")?.outcome)
        assertTrue(
            buildResult.output.contains("network foobar not found") ||
            buildResult.output.contains("No such network: foobar") ||
            buildResult.output.contains("network mode \"foobar\" not supported by buildkit")
        )
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `can add files from project directory to build context`() {
        // given
        val id = "id9"
        val filename = "bar.txt"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
            ADD ${filename} /tmp/
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                files "bar.txt"
            }
        """.trimIndent())
        createFile(filename)
        
        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertEquals("'${id}'\n", exec("docker inspect --format '{{.Author}}' ${id}"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `when adding a project-dir file and a Tar file, then they both end up (unzipped) in the docker image`() {
        // given
        val id = "id10"
        createFile("from_project")
        createFile("from_tgz")

        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER id
            ADD foo.tgz /tmp/
            ADD from_project /tmp/
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            task myTgz(type: Tar) {
                destinationDirectory = project.buildDir
                archiveBaseName = 'foo'
                archiveExtension = 'tgz'
                compression = Compression.GZIP
                into('.') {
                    from 'from_tgz'
                }
            }

            docker {
                name '${id}'
                files tasks.myTgz.outputs, 'from_project'
            }
        """.trimIndent())
        
        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":myTgz")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `can build Docker image from standard Gradle distribution plugin`() {
        // given
        val id = "id11"

        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER id
            ADD . /tmp/
        """.trimIndent())

        file("src/main/java/test/Test.java").writeText("""
        package test;
        public class Test { public static void main(String[] args) {} }
        """.trimIndent())

        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
                id 'java'
                id 'application'
            }

            docker {
                name '${id}'
                files tasks.distTar.outputs
            }
        """.trimIndent())
        
        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":distTar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `check labels are correctly applied to image`() {
        // given
        val id = "id10"
        file("Dockerfile").writeText("""
            FROM alpine:3.2
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                labels 'test-label': 'test-value', 'another.label': 'another.value'
            }
        """.trimIndent())
        
        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":docker")?.outcome)
        assertTrue(exec("docker inspect --format '{{.Config.Labels}}' ${id}").contains("test-label"))
        execCond("docker rmi -f ${id}")
    }

    @Test
    fun `fail with bad label key character`() {
        // given
        file("Dockerfile").writeText("""
            FROM alpine:3.2
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name 'test-bad-labels'
                labels 'test_label': 'test_value'
            }
        """.trimIndent())
        
        // when
        val buildResult = with("docker").buildAndFail()

        // then
        assertTrue(buildResult.output.contains("Docker label 'test_label' contains illegal characters. Label keys " +
            "must only contain lowercase alphanumberic, `.`, or `-` characters (must match " +
            "^[a-z0-9.-]*\$)."))
    }

    @Test
    fun `can add entire directories via copyspec`() {
        // given
        val id = "id1"
        createFile("myDir/bar")
        file("Dockerfile").writeText("""
            FROM alpine:3.2
            MAINTAINER ${id}
            ADD myDir /myDir/
        """.trimIndent())
        buildFile.writeText("""
            plugins {
                id 'ru.crystals.docker'
            }

            docker {
                name '${id}'
                copySpec.from("myDir").into("myDir")
            }
        """.trimIndent())
        
        // when
        val buildResult = with("docker").build()

        // then
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":dockerPrepare")?.outcome)
        assertTrue(file("build/docker/myDir/bar").exists())
    }

    private fun with(vararg tasks: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*tasks)
            .withPluginClasspath()
            .withDebug(true)
    }

    private fun exec(task: String): String {
        val process = ProcessBuilder(*task.split(" ").toTypedArray())
            .directory(projectDir)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()
        process.waitFor()

        return output
    }

    private fun execCond(task: String): Boolean {
        val process = ProcessBuilder(*task.split(" ").toTypedArray())
            .directory(projectDir)
            .start()

        process.inputStream.bufferedReader().readText()
        process.errorStream.bufferedReader().readText()
        return process.waitFor() == 0
    }

    private fun createFile(path: String, baseDir: File = projectDir): File {
        val file = file(path, baseDir)
        assertFalse(file.exists(), "File should not exist before creation")
        file.parentFile.mkdirs()
        assertTrue(file.createNewFile(), "File should be created successfully")
        return file
    }

    private fun file(path: String, baseDir: File = projectDir): File {
        val splitted = path.split("/")
        val directory = if (splitted.size > 1) {
            directory(splitted.dropLast(1).joinToString("/"), baseDir)
        } else {
            baseDir
        }
        return File(directory, splitted.last())
    }

    private fun directory(path: String, baseDir: File = projectDir): File {
        return File(baseDir, path).apply { mkdirs() }
    }

}
