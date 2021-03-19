package com.rohanprabhu.confinity

import com.rohanprabhu.confinity.machine.ImageBuilder
import com.rohanprabhu.confinity.machine.ContainerRunner
import com.rohanprabhu.confinity.machine.DockerOps
import com.rohanprabhu.confinity.machine.JarBundler
import com.rohanprabhu.confinity.models.ConfinedInvocable
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import utils.fromSerializedBytes
import utils.toSerializedBytes
import java.io.Serializable
import java.nio.file.Path
import kotlin.concurrent.fixedRateTimer
import kotlin.reflect.KClass

class Confinity(
    private val cleanupRate: Long = 1_500
) {
    private var locked: Boolean = false
    private var shuttingDown: Boolean = false

    private val dockerOps = DockerOps()
    private val dependantClasses: MutableSet<KClass<*>> = mutableSetOf()
    private val registeredInvocables: MutableList<ConfinedInvocableEntry> = mutableListOf()

    private val jarBundler = JarBundler()
    private val imageBuilder = ImageBuilder()
    private val containerRunner = ContainerRunner(dockerOps)

    companion object {
        const val ConfinityImageName = "confinity-local"
    }

    private val preamble: List<KClass<*>> = listOf(
        ConfinityPackager::class,
        ConfinedInvocable::class
    )

    object CleanupTargets {
        val containerIds: MutableList<String> = mutableListOf()
        val imageIds: MutableList<String> = mutableListOf()
    }
    private var cleanupRunning: Boolean = false

    private val containerCleanupTask = fixedRateTimer("container-cleanup-task", daemon = true, period = cleanupRate) {
        if (!cleanupRunning) {
            val containerIdCopies = CleanupTargets.containerIds.toList()
            containerIdCopies.mapNotNull { containerId ->
                try {
                    dockerOps.forceDeleteContainer(containerId)
                    containerId
                } catch (e: Exception) {
                    null
                }
            }
            .forEach {
                CleanupTargets.containerIds.remove(it)
            }
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            shuttingDown = true
            containerCleanupTask.cancel()

            CleanupTargets.containerIds.forEach {
                try {
                    dockerOps.forceDeleteContainer(it)
                } catch (e: Exception) {
                    println("Could not delete container $it, might have to remove manually")
                }
            }

            CleanupTargets.imageIds.forEach {
                try {
                    dockerOps.forceRemoveImage(it)
                } catch (e: Exception) {
                    println("Could not remove image $it, might have to remove manually")
                }
            }
        })
    }

    private data class ConfinedInvocableEntry(
        val confinedInvocable: ConfinedInvocable<*, *>,
        val inputType: KClass<*>,
        val outputType: KClass<*>
    )

    interface ConfinedInvocableReference<P: Serializable, R: Serializable> {
        fun call(payload: P) : R
    }

    fun addDependantClasses(depClass: List<KClass<*>>) {
        dependantClasses.addAll(depClass)
    }

    fun <P: Serializable, R: Serializable> registerConfinedInvocable(
        confinedInvocable: ConfinedInvocable<P, R>,
        dependencyClasses: List<KClass<*>> = emptyList()
    ) : ConfinedInvocableReference<P, R> {
        disallowIfShuttingDown()
        if (locked) {
            throw IllegalArgumentException("New invocables cannot be registered post locking")
        }

        registeredInvocables.add(confinedInvocable.toConfinedInvocableEntry())
        addDependantClasses(dependencyClasses)

        return object : ConfinedInvocableReference<P, R> {
            private val callTarget = confinedInvocable::class.qualifiedName!!

            override fun call(payload: P): R {
                disallowIfShuttingDown()

                val (containerResponse, containerId) = containerRunner.runImage(
                    ConfinityImageName,
                    callTarget,
                    payload.toSerializedBytes()
                )

                CleanupTargets.containerIds.add(containerId)
                val seek = containerResponse.indexOf("__CONF_BOUNDARY_")
                val seekEnd = containerResponse.indexOf("_CONF_BOUNDARY__")

                return containerResponse.substring((seek + "__CONF_BOUNDARY_".length) until seekEnd)
                    .fromSerializedBytes()
            }
        }
    }

    fun commit() {
        disallowIfShuttingDown()
        locked = true

        val stagingDirectory = jarBundler.bundleJar(getJarBundlingTargets().toList(), loadVendoredJars())
        val imageId = imageBuilder.buildDockerImage(stagingDirectory, ConfinityImageName)

        CleanupTargets.imageIds.add(imageId)
    }

    private fun ConfinedInvocable<*, *>.toConfinedInvocableEntry() : ConfinedInvocableEntry {
        val invokeMethodRef = this::class.java.methods
            .first { it.name == "invoke" }

        val inputType = invokeMethodRef.parameters[0].type.kotlin
        val outputType = invokeMethodRef.returnType.kotlin

        return ConfinedInvocableEntry(
            inputType = inputType,
            outputType = outputType,
            confinedInvocable = this
        )
    }

    private fun getJarBundlingTargets() = preamble + dependantClasses + registeredInvocables.flatMap {
        setOf(it.confinedInvocable::class, it.inputType, it.outputType)
    }

    private fun loadVendoredJars() =
        Reflections(
            ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("vendored"))
                .setScanners(ResourcesScanner())
        )
        .getResources { it.endsWith(".jarx") }
        .map { Path.of(it) }

    private fun disallowIfShuttingDown() {
        if (shuttingDown) { throw IllegalArgumentException("VM is shutting down") }
    }
}
