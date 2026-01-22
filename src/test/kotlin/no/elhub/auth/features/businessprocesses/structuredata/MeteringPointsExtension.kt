package no.elhub.auth.features.businessprocesses.structuredata

import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

object MeteringPointsServiceTestContainer {
    private val image = DockerImageName.parse("sha256:06f22e15c477bfec4f733e34ab367ad35d10c0f76b6e87d0341c17625370172c")
    private var container: GenericContainer<*>? = null

    fun start() {
        if (container != null) return
        container = GenericContainer(image).apply {
            withExposedPorts(8080)
            waitingFor(
                Wait.forHttp("/__admin/health")
                    .forStatusCodeMatching { it == 200 }
                    .withStartupTimeout(Duration.ofSeconds(30))
            )
            start()
        }
    }

    fun stop() {
        container?.runCatching { stop() }
        container = null
    }

    fun serviceUrl(): String {
        val c = container ?: error("MeteringPointsServiceTestContainer not started")
        return "http://${c.host}:${c.getMappedPort(8080)}/service"
    }
}

object MeteringPointsServiceTestContainerExtension : BeforeSpecListener {
    override suspend fun beforeSpec(spec: Spec) = MeteringPointsServiceTestContainer.start()
}

object StopMeteringPointsServiceTestContainerExtension : AfterProjectListener {
    override suspend fun afterProject() = MeteringPointsServiceTestContainer.stop()
}
