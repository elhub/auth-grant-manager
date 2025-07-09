package no.elhub.auth.grantmanager.presentation.extensions

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.AutoScan
import io.kotest.core.spec.Spec
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.sql.DriverManager

private object PostgresTestContainer {
    private const val POSTGRES_PORT = 5432
    var started = false
        private set

    private val postgres: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("auth")
            .withUsername("postgres")
            .withPassword("postgres")
            .withExposedPorts(POSTGRES_PORT)
            .withCreateContainerCmdModifier { cmd ->
                cmd.withHostConfig(
                    HostConfig().withPortBindings(
                        PortBinding(Ports.Binding.bindPort(POSTGRES_PORT), ExposedPort(POSTGRES_PORT))
                    )
                )
            }
    }

    fun start() {
        postgres.start()
        migrate(postgres)
        started = true
    }

    fun stopIfStarted() {
        if (started) {
            postgres.stop()
        }
    }

    private fun migrate(pg: PostgreSQLContainer<*>) {
        val url = pg.jdbcUrl
        val user = pg.username
        val password = pg.password
        DriverManager.getConnection(url, user, password).use { conn ->
            val db = DatabaseFactory
                .getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(conn))
            Liquibase(
                "db/db-changelog.yaml",
                DirectoryResourceAccessor(Paths.get(System.getProperty("user.dir"))),
                db
            ).apply {
                setChangeLogParameter("APP_USERNAME", "app")
                setChangeLogParameter("APP_PASSWORD", "app")
                update("")
            }
        }
    }
}

object PostgresTestContainerExtension : BeforeSpecListener {
    override suspend fun beforeSpec(spec: Spec) {
        if (!PostgresTestContainer.started) {
            PostgresTestContainer.start()
        }
    }
}

@AutoScan
object StopPostgresTestContainerExtension : AfterProjectListener {
    override suspend fun afterProject() {
        PostgresTestContainer.stopIfStarted()
    }
}
