import no.elhub.auth.utils.generateSelfSignedCertificate
import no.elhub.auth.utils.validateJsonApiSpec
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.elhub.gradle.plugin)
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp.plugin)
    alias(libs.plugins.gradle.docker)
    alias(libs.plugins.openapi.generator.plugin)
}

dependencies {
    // Ktor
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.functional.programming)
    // Koin
    implementation(libs.bundles.dependency.injection)
    ksp(libs.di.koin.ksp.compiler)
    // Serialization
    implementation(libs.bundles.serialization)
    // Database
    implementation(libs.bundles.database)
    // Documentation
    implementation(libs.bundles.documentation)
    // PDF generation and signing
    implementation(libs.bundles.pdf.generation)
    implementation(libs.bundles.dss)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)
    // Observability
    implementation(libs.bundles.logging)
    implementation(libs.bundles.monitoring)
    // JSON validation
    implementation(libs.json.skema)
    implementation(libs.elhub.jsonapi)
    // Unit Testing
    testImplementation(testFixtures(libs.elhub.jsonapi))
    testImplementation(libs.database.postgresql)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.functional.programming)
    testImplementation(libs.test.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.kotest.assertions.arrow)
    testImplementation(libs.test.kotest.assertions.core)
    testImplementation(libs.test.kotest.assertions.json)
    testImplementation(libs.test.kotest.extensions.koin)
    testImplementation(libs.test.koin.test)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.postgres)
    testImplementation(libs.test.mybatis)
    testImplementation(libs.test.verapdf.validation.model)
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
    arg("KOIN_DEFAULT_MODULE", "true")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

val dbUsername = System.getenv("DB_USERNAME")?.takeIf { it.isNotBlank() } ?: "admin"
val dbPassword = System.getenv("DB_PASSWORD")?.takeIf { it.isNotBlank() } ?: "admin"

val certDir = layout.buildDirectory.dir("tmp/test-certs")
val testCertPath = certDir.map { it.file("self-signed-cert.pem").asFile.path }
val testKeyPath = certDir.map { it.file("self-signed-key.pem").asFile.path }
val bankIdPath = certDir.map { it.file("bankid-root.pem").asFile.path }

dockerCompose {
    createNested("services").apply {
        useComposeFiles.set(listOf("docker-compose.yaml"))
        environment.putAll(
            mapOf(
                "DB_USERNAME" to dbUsername,
                "DB_PASSWORD" to dbPassword,
                "PRIVATE_KEY_PATH" to testKeyPath.get()
            ),
        )
    }
}

openApiValidate {
    inputSpec = "$projectDir/src/main/resources/static/openapi.yaml"
    recommend = true
}

tasks.register("generateTestCerts") {
    group = "build setup"
    description = "Generates self-signed certificates for local development to be used in document signing and verification."
    doLast {
        generateSelfSignedCertificate(certDir.get().asFile.path)
    }
}

tasks.register("validateJsonApiSpec") {
    group = "build setup"
    description = "Does some stuff"
    doLast {
        validateJsonApiSpec("src/main/resources/static/schemas")
    }
}

tasks.withType<Test>().configureEach {
    // Run AWT-dependent PDF validation in headless mode so tests work without an X server
    systemProperty("java.awt.headless", "true")
}

tasks.named("test").configure {
    dependsOn(tasks.named("generateTestCerts"))
    dependsOn(tasks.named("openApiValidate"))
    dependsOn(tasks.named("validateJsonApiSpec"))
}

tasks.register<Exec>("liquibaseUpdate") {
    group = "database"
    description = "Runs Liquibase update using the CLI."
    dependsOn("servicesComposeUp")

    environment("APP_USERNAME", "app")
    environment("APP_PASSWORD", "app")

    commandLine(
        "liquibase",
        "--url=jdbc:postgresql://localhost:5432/auth",
        "--username=$dbUsername",
        "--password=$dbPassword",
        "--driver=org.postgresql.Driver",
        "--changeLogFile=db/db-changelog.yaml",
        "update",
    )
}

tasks.named("liquibaseUpdate").configure {
    dependsOn(tasks.named("servicesComposeUp"))
}

val localEnvVars = mapOf(
    "JDBC_URL" to "jdbc:postgresql://localhost:5432/auth",
    "APP_USERNAME" to "app",
    "APP_PASSWORD" to "app",
    "MUSTACHE_RESOURCE_PATH" to "templates",
    "VAULT_URL" to "http://localhost:8200/v1/transit",
    "VAULT_KEY" to "test-key",
    "VAULT_TOKEN_PATH" to "src/test/resources/vault_token_mock.txt",
    "PATH_TO_SIGNING_CERTIFICATE" to testCertPath.get(),
    "PATH_TO_SIGNING_CERTIFICATE_CHAIN" to testCertPath.get(),
    "ENABLE_ENDPOINTS" to "true",
    "AUTH_PERSONS_URL" to "http://localhost:8081",
    "PDP_BASE_URL" to "https://auth-policy-decision-point-test9.elhub.cloud",
    "STRUCTURE_DATA_METERING_POINTS_SERVICE_URL" to "http://localhost:8080",
    "STRUCTURE_DATA_METERING_POINTS_SERVICE_API_USERNAME" to "user",
    "STRUCTURE_DATA_METERING_POINTS_SERVICE_API_PASSWORD" to "pass",
)

tasks.named<JavaExec>("run").configure {
    dependsOn("generateTestCerts", "servicesComposeUp", "liquibaseUpdate")
    localEnvVars.forEach { (key, value) -> environment(key, value) }
}

dependencyCheck {
    nvd {
        datafeedUrl = "https://owasp.elhub.cloud"
    }
}
