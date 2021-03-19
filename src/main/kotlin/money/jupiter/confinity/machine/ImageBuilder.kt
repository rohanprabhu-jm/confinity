package money.jupiter.confinity.machine

import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.DockerDaemonImage
import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class ImageBuilder {
    fun buildDockerImage(bundlingDirectory: String, imageName: String): String {
        val vendoredPaths = Files.walk(Path.of(bundlingDirectory, "libs")).collect(Collectors.toList())

        return Jib.from("gcr.io/distroless/java:11")
            .addLayer(listOf(Paths.get("$bundlingDirectory/confinity.jar")), AbsoluteUnixPath.get("/"))
            .addLayer(
                vendoredPaths,
                AbsoluteUnixPath.get("/libs")
            )
            .setEntrypoint("java", "-cp", "/libs/*:/confinity.jar", "money.jupiter.confinity.ConfinityPackager")
            .containerize(
                Containerizer.to(DockerDaemonImage.named(imageName))
            )
            .imageId
            .hash
    }
}
