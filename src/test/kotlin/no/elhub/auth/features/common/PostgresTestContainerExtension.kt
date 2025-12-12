package no.elhub.auth.features.common

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.sql.DriverManager

class PostgresTestContainer {
    companion object {
        private const val POSTGRES_PORT = 5432
        val USERNAME = "postgres"
        val PASSWORD = "postgres"
        val DRIVER = "org.postgresql.Driver"
        val JDBC_URL = "jdbc:postgresql://localhost:$POSTGRES_PORT/auth"
        val DATABASE_NAME = "auth"
    }

    private val postgres: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName(DATABASE_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
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
    }

    fun stop() {
        postgres.stop()
    }

    private fun migrate(pg: PostgreSQLContainer) {
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

class PostgresTestContainerExtension : BeforeSpecListener, AfterSpecListener {
    val postgres = PostgresTestContainer()
    override suspend fun beforeSpec(spec: Spec) {
        postgres.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        postgres.stop()
    }
}
