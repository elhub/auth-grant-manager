package no.elhub.auth.features.businessprocesses.structuredata

import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

object MeteringPointsServiceTestContainer {
    private val image = DockerImageName.parse("docker.jfrog.elhub.cloud/frzq0sxltynr/elhub/structure-data-metering-points-service-mock:0.1.36-196")
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

object MeteringPointsServiceTestData {
    const val VALID_METERING_POINT = "300362000000000008"
    const val END_USER_ID = "d6784082-8344-e733-e053-02058d0a6752"
    const val ANOTHER_END_USER_ID = "00662e04-2fd6-3b06-b672-3965abe7b7c5"
    const val SHARED_END_USER_ID = "384c71a3-4db6-2171-e063-04058d0a09b2"
    const val NON_EXISTING_METERING_POINT = "300362000000000000"
}
