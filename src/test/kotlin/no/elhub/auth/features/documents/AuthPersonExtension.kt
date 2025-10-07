package no.elhub.auth.features.documents

import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

object AuthPersonTestContainer {

    private val image = DockerImageName.parse("docker.jfrog.elhub.cloud/frzq0sxltynr/auth/auth-persons-mock:0.1.8-17")
    private var container: GenericContainer<*>? = null

    fun start() {
        if (container != null) return
        container = GenericContainer(image).apply {
            withExposedPorts(8080)
            waitingFor(
                Wait.forHttp("/persons/18084190426")
                    .forStatusCodeMatching{ it == 200 || it == 404 }
                    .withStartupTimeout(Duration.ofSeconds(30))
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

object StartAuthPersonTestContainer : BeforeSpecListener {
    override suspend fun beforeSpec(spec: Spec) = AuthPersonTestContainer.start()
}

object StopAuthPersonTestContainer : AfterProjectListener {
    override suspend fun afterProject() = AuthPersonTestContainer.stop()
}
