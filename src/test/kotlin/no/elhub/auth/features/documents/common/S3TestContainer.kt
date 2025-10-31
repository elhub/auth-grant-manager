package no.elhub.auth.features.documents.common

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

object S3TestContainer : BeforeSpecListener, AfterSpecListener {

    private const val SOURCE_PATH_GARAGE_CONF = "garage-conf.toml"
    private const val TARGET_PATH_GARAGE_CONF = "/etc/garage.toml"
    private val GARAGE_PORTS = listOf(3900, 3901, 3902, 3903)

    private val garage: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("dxflrs/garage:v2.1.0"))
            .withExposedPorts(*GARAGE_PORTS.toTypedArray())
            .withCopyFileToContainer(
                MountableFile.forClasspathResource(SOURCE_PATH_GARAGE_CONF),
                TARGET_PATH_GARAGE_CONF,
            )
            .withCreateContainerCmdModifier { cmd ->
                cmd.withHostConfig(
                    HostConfig()
                        .withPortBindings(
                            GARAGE_PORTS.map {
                                PortBinding(Ports.Binding.bindPort(it), ExposedPort(it))
                            }
                        )
                )
            }
    }

    override suspend fun beforeSpec(spec: Spec) = garage.start()

    override suspend fun afterSpec(spec: Spec) = garage.stop()
}
