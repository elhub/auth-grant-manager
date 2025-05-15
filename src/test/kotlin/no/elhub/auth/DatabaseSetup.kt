package no.elhub.auth

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.sql.DriverManager
import kotlin.reflect.full.findAnnotation
import kotlin.use

/**
 * Annotate any Spec that needs a Postgres.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class RequireTestContainer

object DatabaseSetup : BeforeSpecListener, AfterProjectListener {
    private const val POSTGRES_PORT = 5432

    private lateinit var postgres: PostgreSQLContainer<*>

    override suspend fun beforeSpec(spec: Spec) {
        val hasRequiredAnnotation = spec::class.findAnnotation<RequireTestContainer>() != null
        if (hasRequiredAnnotation && !::postgres.isInitialized) {
            postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
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
                }.also {
                    it.start()
                    migrate(it)
                }
        }
    }

    override suspend fun afterProject() {
        if (::postgres.isInitialized) {
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
