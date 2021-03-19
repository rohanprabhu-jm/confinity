package com.rohanprabhu.confinity.machine

class ContainerRunner(
    private val dockerOps: DockerOps
) {
    data class ContainerRunResult(
        val logs: String,
        val containerId: String
    )

    fun runImage(imageName: String, invokeProcedure: String, invokeArgument: String): ContainerRunResult {
        val containerId = dockerOps.createContainer(imageName, listOf(invokeProcedure, invokeArgument))
        dockerOps.startContainerAndWait(containerId)
        return ContainerRunResult(logs = dockerOps.fetchStdinLogs(containerId), containerId = containerId)
    }
}
