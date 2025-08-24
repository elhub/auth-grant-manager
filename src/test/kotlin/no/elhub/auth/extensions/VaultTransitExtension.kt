package no.elhub.auth.extensions

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import no.elhub.auth.config.VaultConfig
import no.elhub.auth.utils.TestCertificateUtil
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths

internal const val VAULT_PORT = 8200

object VaultTransitTestContainer {

    var started = false
        private set

    val container by lazy {
        val hostKeyFile = Paths.get(TestCertificateUtil.Constants.PRIVATE_KEY_LOCATION).toAbsolutePath()

        GenericContainer(DockerImageName.parse("docker.jfrog.elhub.cloud/frzq0sxltynr/auth/vault-transit-secrets-engine-mock:0.1.0-2"))
            .withExposedPorts(VAULT_PORT)
            .withCopyFileToContainer(
                MountableFile.forHostPath(
                    hostKeyFile.toString()
                ),
                "key/private.pem"
            )
            .withCreateContainerCmdModifier { cmd ->
                cmd.withHostConfig(
                    HostConfig().withPortBindings(
                        PortBinding(Ports.Binding.bindPort(VAULT_PORT), ExposedPort(VAULT_PORT))
                    )
                )
            }
            .waitingFor(
                Wait.forHttp("/ping")
                    .forStatusCode(200)
            )
    }

    fun start() {
        container.start()
        started = true
    }

    fun stopIfStarted() {
        if (started) {
            container.stop()
        }
    }
}

object VaultTransitTestContainerExtension : BeforeSpecListener {
    override suspend fun beforeSpec(spec: Spec) {
        if (!VaultTransitTestContainer.started) {
            VaultTransitTestContainer.start()
        }
    }
}

object StopVaultTransitTestContainer : AfterProjectListener {
    override suspend fun afterProject() {
        VaultTransitTestContainer.stopIfStarted()
    }
}

val localVaultConfig = VaultConfig(url = "http://localhost:$VAULT_PORT/v1/transit", key = "test-key", tokenPath = "src/test/resources/vault_token_mock.txt")
