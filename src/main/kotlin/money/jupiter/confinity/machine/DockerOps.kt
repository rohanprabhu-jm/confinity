package money.jupiter.confinity.machine

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import java.io.ByteArrayInputStream

class DockerOps {
    companion object {
        val dockerClient = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("unix:///var/run/docker.sock")
            .build()

        val dockerHttpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(dockerClient.dockerHost)
            .sslConfig(dockerClient.sslConfig)
            .build()

        val objectMapper = ObjectMapper()
    }

    fun createContainer(imageName: String, cmdArgs: List<String>): String {
        val ctrCreateRequest = DockerHttpClient.Request.builder()
            .method(DockerHttpClient.Request.Method.POST)
            .path("/v1.41/containers/create")
            .putHeader("Content-Type", "application/json")
            .body(
                ByteArrayInputStream(
                    objectMapper.writeValueAsBytes(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("Hostname", "")
                            .put("Domainname", "")
                            .put("User", "")
                            .put("AttachStdin", false)
                            .put("AttachStderr", true)
                            .put("AttachStdout", true)
                            .put("Tty", false)
                            .put("OpenStdin", false)
                            .put("StdinOnce", false)
                            .put("Image", imageName)
                            .put("NetworkDisabled", true)
                            .set(
                                "Cmd", JsonNodeFactory.instance.arrayNode()
                                    .let {
                                        cmdArgs.forEach { arg -> it.add(arg) }
                                        it
                                    }
                            )
                    )
                )
            )
            .build()

        val ctrCreateResponse = dockerHttpClient.execute(ctrCreateRequest)
        return objectMapper.readTree(ctrCreateResponse.body.readAllBytes().toString(Charsets.UTF_8)).get("Id").asText()
    }

    fun startContainerAndWait(containerId: String) {
        val startRequest = DockerHttpClient.Request.builder()
            .method(DockerHttpClient.Request.Method.POST)
            .path("/v1.41/containers/$containerId/start")
            .build()

        dockerHttpClient.execute(startRequest)

        val waitRequest = DockerHttpClient.Request.builder()
            .method(DockerHttpClient.Request.Method.POST)
            .path("/v1.41/containers/$containerId/wait")
            .build()

        // We need to read all body for this call to actually be blocking on the wait call
        dockerHttpClient.execute(waitRequest)
            .body
            .readAllBytes()
    }

    fun fetchStdinLogs(containerId: String) : String {
        val logsRequest = DockerHttpClient.Request.builder()
            .method(DockerHttpClient.Request.Method.GET)
            .path("/v1.41/containers/$containerId/logs?stdout=1")
            .build()

        return dockerHttpClient.execute(logsRequest).body.readAllBytes().toString(Charsets.UTF_8)
    }

    fun forceDeleteContainer(containerId: String) {
        val deleteContainerRequest = DockerHttpClient.Request.builder()
            .method(DockerHttpClient.Request.Method.DELETE)
            .path("/v1.41/containers/$containerId?force=true")
            .build()

        dockerHttpClient.execute(deleteContainerRequest)
    }

    fun forceRemoveImage(imageId: String) {
        val removeImageRequest = DockerHttpClient.Request.builder()
            .method(DockerHttpClient.Request.Method.DELETE)
            .path("/v1.41/images/$imageId?force=true")
            .build()

        dockerHttpClient.execute(removeImageRequest)
    }
}
