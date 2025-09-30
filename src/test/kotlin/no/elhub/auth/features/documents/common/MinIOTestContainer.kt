package no.elhub.auth.features.documents.common

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName

private const val MINIO_PORT = 9000

object MinIOTestContainer : BeforeSpecListener, AfterSpecListener {
    private val minio: MinIOContainer by lazy {
        MinIOContainer(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(MINIO_PORT)
            .withCreateContainerCmdModifier { cmd ->
                cmd.withHostConfig(
                    HostConfig()
                        .withPortBindings(
                            PortBinding(Ports.Binding.bindPort(MINIO_PORT), ExposedPort(MINIO_PORT)),
                        )
                )
            }
            .withUserName("minio")
            .withPassword("miniopassword")
    }

    override suspend fun beforeSpec(spec: Spec) = minio.start()

    override suspend fun afterSpec(spec: Spec) = minio.stop()
}
