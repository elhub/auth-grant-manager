import no.elhub.auth.utils.generateSelfSignedCertificate
import no.elhub.auth.utils.validateJsonApiSpec

plugins {
    alias(libs.plugins.elhub.gradle.plugin)
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp.plugin)
    alias(libs.plugins.liquibase.plugin)
    alias(libs.plugins.gradle.docker)
    alias(libs.plugins.openapi.generator.plugin)
}

buildscript {
    dependencies {
        classpath(libs.database.liquibase.core)
    }
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
    // Liquibase
    liquibaseRuntime(libs.database.liquibase.core)
    liquibaseRuntime(libs.cli.picocli)
    liquibaseRuntime(libs.serialization.yaml.snakeyaml)
    liquibaseRuntime(libs.database.postgresql)
    // Documentation
    implementation(libs.bundles.documentation)
    // PDF generation and signing
    implementation(libs.bundles.pdf.generation)
    implementation(libs.bundles.dss)
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
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.kotest.assertions.arrow)
    testImplementation(libs.test.kotest.assertions.core)
    testImplementation(libs.test.kotest.assertions.json)
    testImplementation(libs.test.kotest.extensions.koin)
    testImplementation(libs.test.koin.test)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.postgres)
    testImplementation(libs.test.mybatis)
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

val dbUsername = System.getenv("DB_USERNAME") ?: ""
val dbPassword = System.getenv("DB_PASSWORD") ?: ""

liquibase {
    jvmArgs =
        arrayOf(
            "-Dliquibase.command.url=jdbc:postgresql://localhost:5432/auth",
            "-Dliquibase.command.username=$dbUsername",
            "-Dliquibase.command.password=$dbPassword",
            "-DAPP_USERNAME=app",
            "-DAPP_PASSWORD=app",
            "-Dliquibase.command.driver=org.postgresql.Driver",
            "-Dliquibase.command.changeLogFile=db/db-changelog.yaml",
        )
    activities.register("main")
}

val certDir = layout.buildDirectory.dir("tmp/test-certs")
val testCertPath = certDir.map { it.file("self-signed-cert.pem").asFile.path }
val testKeyPath = certDir.map { it.file("self-signed-key.pem").asFile.path }

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
    inputSpec = "$projectDir/src/main/resources/openapi.yaml"
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
        validateJsonApiSpec("src/main/resources/schemas")
    }
}

tasks.named("test").configure {
    dependsOn(tasks.named("generateTestCerts"))
    dependsOn(tasks.named("openApiValidate"))
    dependsOn(tasks.named("validateJsonApiSpec"))
}

tasks.named("liquibaseUpdate").configure {
    dependsOn(tasks.named("servicesComposeUp"))
}

val localEnvVars = mapOf(
    "JDBC_URL" to "jdbc:postgresql://localhost:5432/auth",
    "APP_USERNAME" to "app",
    "APP_PASSWORD" to "app",
    "MUSTACHE_RESOURCE_PATH" to "templates",
    "VAULT_URL" to "http://localhost:8200",
    "VAULT_KEY" to "test-key",
    "VAULT_TOKEN_PATH" to "somepath",
    "PATH_TO_SIGNING_CERTIFICATE" to testCertPath.get(),
    "PATH_TO_SIGNING_CERTIFICATE_CHAIN" to testCertPath.get(),
)

tasks.named<JavaExec>("run").configure {
    dependsOn("generateTestCerts", "servicesComposeUp", "liquibaseUpdate")
    localEnvVars.forEach { (key, value) -> environment(key, value) }
}
