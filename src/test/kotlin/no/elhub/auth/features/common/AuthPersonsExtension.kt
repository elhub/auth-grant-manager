package no.elhub.auth.features.common

import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

object AuthPersonsTestContainer {

    private val image = DockerImageName.parse("docker.jfrog.elhub.cloud/frzq0sxltynr/auth/auth-persons-mock:0.1.28-52")
    private var container: GenericContainer<*>? = null

    fun start() {
        if (container != null) return
        container = GenericContainer(image).apply {
            withExposedPorts(8080)
            waitingFor(
                Wait.forHttp("/__admin/health")
                    .forStatusCodeMatching { it == 200 }
                    .withStartupTimeout(Duration.ofSeconds(60)) // increased timeout
            )
            start()
        }
    }

    fun stop() {
        container?.runCatching { stop() }
        container = null
    }

    fun baseUri(): String {
        val c = container ?: error("AuthPersonsTestContainer not started")
        return "http://${c.host}:${c.getMappedPort(8080)}"
    }
}

object AuthPersonsTestContainerExtension : BeforeSpecListener {
    override suspend fun beforeSpec(spec: Spec) = AuthPersonsTestContainer.start()
}

object StopAuthPersonsTestContainerExtension : AfterProjectListener {
    override suspend fun afterProject() = AuthPersonsTestContainer.stop()
}
