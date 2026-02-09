package no.elhub.auth.features.businessprocesses.structuredata.organisations

import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

object OrganisationsServiceTestContainer {
    private val image = DockerImageName.parse("docker.jfrog.elhub.cloud/frzq0sxltynr/elhub/structure-data-organisations-service-mock:0.1.25-161")
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
        val c = container ?: error("OrganisationsServiceTestContainer not started")
        return "http://${c.host}:${c.getMappedPort(8080)}/v1/service"
    }
}

object OrganisationsServiceTestContainerExtension : BeforeSpecListener {
    override suspend fun beforeSpec(spec: Spec) = OrganisationsServiceTestContainer.start()
}

object StopOrganisationsServiceTestContainerExtension : AfterProjectListener {
    override suspend fun afterProject() = OrganisationsServiceTestContainer.stop()
}

object OrganisationsServiceTestData {
    const val VALID_PARTY_ID = "3004300000019"
    const val NOT_BALANCE_SUPPLIER_PARTY_ID = "8383000000075"
    const val CURRENT_PARTY_ID = "0128000000599"
    const val INACTIVE_PARTY_ID = "4502100001872"
}
