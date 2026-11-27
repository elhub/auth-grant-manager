package no.elhub.auth.v1.acceptance.support

import kotlinx.coroutines.sync.Mutex
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import no.elhub.auth.v0.features.documents.TestCertificateUtil
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths
import java.sql.DriverManager

/**
 * Everything outside the application, running locally on random ports.
 *
 * The application itself is started unchanged from `application.yaml` (see [ProductionApp]);
 * only the environment variables differ from a deployed environment, and each one points here.
 */
object AcceptanceTestInfrastructure {
    /** Hold across a whole spec: its WireMock mappings and application properties are process-wide. */
    val specLock = Mutex()
    private const val APP_DB_USER = "app"
    private const val APP_DB_PASSWORD = "app"

    private val postgres by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:18"))
            .withDatabaseName("auth")
            .withUsername("postgres")
            .withPassword("postgres")
            .also { it.start() }
            .also(::migrate)
    }

    private val vault by lazy {
        GenericContainer(DockerImageName.parse("docker.jfrog.elhub.cloud/frzq0sxltynr/auth/vault-transit-secrets-engine-mock:0.1.0-2"))
            .withExposedPorts(8200)
            .withCopyFileToContainer(
                MountableFile.forHostPath(Paths.get(TestCertificateUtil.Constants.PRIVATE_KEY_LOCATION).toAbsolutePath()),
                "key/private.pem",
            )
            .waitingFor(Wait.forHttp("/ping").forStatusCode(200))
            .also { it.start() }
    }

    /** All external HTTP services. Programmed per test through [ExternalServiceStubs]. */
    private val wiremock by lazy {
        GenericContainer(DockerImageName.parse("wiremock/wiremock:3.13.2"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200))
            .also { it.start() }
    }

    private val tokenService by lazy {
        GenericContainer(
            DockerImageName.parse(
                "docker.jfrog.elhub.cloud/frzq0sxltynr/auth/auth-test-token-service@sha256:9f6aa92d6176ba344c95aa6b7c7d9225b1cb7a729c7688f11154d353f3a96e80",
            ),
        )
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200))
            .also { it.start() }
    }

    val testTokens by lazy { TestTokens(tokenService.url()) }
    val externalServices by lazy { ExternalServiceStubs(wiremock.url()) }

    /** Remove scenario data while retaining the migrated schema and Liquibase history. */
    fun clearData() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    DO ${'$'}${'$'}
                    DECLARE tables text;
                    BEGIN
                        SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
                        INTO tables
                        FROM pg_tables
                        WHERE schemaname IN ('auth', 'auth_v1');
                        IF tables IS NOT NULL THEN
                            EXECUTE 'TRUNCATE TABLE ' || tables || ' RESTART IDENTITY CASCADE';
                        END IF;
                    END ${'$'}${'$'}
                    """.trimIndent(),
                )
            }
        }
    }

    /** The environment variables a deployed instance gets, pointed at local fakes. */
    val environmentVariables: Map<String, String> by lazy {
        val fakes = wiremock.url()
        mapOf(
            "V1_ENABLED" to "true",
            "USE_PDF_TEST_NOTICE" to "true",
            "JDBC_URL" to postgres.jdbcUrl,
            "APP_USERNAME" to APP_DB_USER,
            "APP_PASSWORD" to APP_DB_PASSWORD,
            "VAULT_URL" to "${vault.url()}/v1/transit",
            "VAULT_KEY" to "test-key",
            "VAULT_TOKEN_PATH" to "src/test/resources/vault_token_mock.txt",
            "PATH_TO_SIGNING_CERTIFICATE" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
            "PATH_TO_INTERMEDIATE_CERTIFICATE" to TestCertificateUtil.Constants.INTERMEDIATE_CERTIFICATE_LOCATION,
            "PATH_TO_BANKID_ROOT_CERTIFICATES_DIR" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATES_DIR,
            "PATH_TO_TSA_ROOT_CERTIFICATES_DIR" to TestCertificateUtil.Constants.TSA_ROOT_CERTIFICATES_DIR,
            "PDP_BASE_URL" to fakes,
            "PDP_IDP_CLIENT_ID" to "acceptance",
            "PDP_IDP_CLIENT_SECRET" to "acceptance",
            "AUTH_PERSONS_URL" to "$fakes${ExternalServiceStubs.AUTH_PERSONS_PATH}",
            "AUTH_PERSONS_IDP_CLIENT_ID" to "acceptance",
            "AUTH_PERSONS_IDP_CLIENT_SECRET" to "acceptance",
            "STRUCTURE_DATA_METERING_POINTS_SERVICE_URL" to "$fakes${ExternalServiceStubs.METERING_POINTS_PATH}",
            "STRUCTURE_DATA_METERING_POINTS_SERVICE_IDP_CLIENT_ID" to "acceptance",
            "STRUCTURE_DATA_METERING_POINTS_SERVICE_IDP_CLIENT_SECRET" to "acceptance",
            "STRUCTURE_DATA_ORGANISATIONS_SERVICE_HOST" to "$fakes${ExternalServiceStubs.ORGANISATIONS_PATH}",
            "STRUCTURE_DATA_ORGANISATIONS_SERVICE_IDP_CLIENT_ID" to "acceptance",
            "STRUCTURE_DATA_ORGANISATIONS_SERVICE_IDP_CLIENT_SECRET" to "acceptance",
            "STRUCTURE_DATA_END_USERS_SERVICE_URL" to "$fakes${ExternalServiceStubs.END_USERS_PATH}",
            "STRUCTURE_DATA_END_USERS_SERVICE_IDP_CLIENT_ID" to "acceptance",
            "STRUCTURE_DATA_END_USERS_SERVICE_IDP_CLIENT_SECRET" to "acceptance",
            "STROMPRIS_SERVICE_URL" to "$fakes/strompris",
            "STROMPRIS_SERVICE_IDP_CLIENT_ID" to "acceptance",
            "STROMPRIS_SERVICE_IDP_CLIENT_SECRET" to "acceptance",
            "VALIDATE_CONTRACT_NAME_FEATURE" to "false",
            "IDP_TOKEN_URL" to "$fakes${ExternalServiceStubs.IDP_TOKEN_PATH}",
            "UNLEASH_ENABLED" to "false",
            "EDIEL_ENV" to "production",
            "EDIEL_SERVICE_URL" to "$fakes${ExternalServiceStubs.EDIEL_PATH}",
            "EDIEL_USERNAME" to "acceptance",
            "EDIEL_PASSWORD" to "acceptance",
        )
    }

    private fun GenericContainer<*>.url() = "http://$host:$firstMappedPort"

    private fun migrate(container: PostgreSQLContainer) {
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase("db/db-changelog.yaml", DirectoryResourceAccessor(Paths.get(System.getProperty("user.dir"))), database).apply {
                setChangeLogParameter("APP_USERNAME", APP_DB_USER)
                setChangeLogParameter("APP_PASSWORD", APP_DB_PASSWORD)
                update("")
            }
        }
    }
}
