package com.rohanprabhu.confinity.machine

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

class JarBundler {
    fun bundleJar(list: List<KClass<out Any>>, bundlingJars: List<Path>): String {
        val jarStaging = Files.createTempDirectory("jar-stage")

        list.forEach {
            val classBinary = it.java.getResource("${it.simpleName}.class")
                .openStream()
                .readAllBytes()

            val destinationFile = File(
                jarStaging.toString() + "/classes/" +
                    it.qualifiedName!!.replace(".", "/") + ".class"
            )

            FileUtils.writeByteArrayToFile(destinationFile, classBinary)
        }

        bundlingJars.forEach {
            val targetName = if (it.toString().endsWith(".jarx")) {
                it.toString().removeSuffix(".jarx") + ".jar"
            } else {
                it.toString()
            }

            val jarInputStream = this::class.java.classLoader.getResourceAsStream(it.toString())

            FileUtils.copyInputStreamToFile(
                jarInputStream,
                File("$jarStaging/libs/$targetName")
            )
        }

        val targetJar = ZipFile("$jarStaging/confinity.jar")

        Files.walk(Path.of(jarStaging.toString(), "classes"))
            .forEach {
                targetJar.addFile(
                    it.toFile(),
                    ZipParameters().apply { fileNameInZip = it.toString().removePrefix("$jarStaging/classes/") }
                )
            }

        return jarStaging.toString()
    }
}
