plugins {
    alias(libs.plugins.elhub.gradle.plugin)
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp.plugin)
    alias(libs.plugins.liquibase.plugin)
    alias(libs.plugins.gradle.docker)
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
    implementation(libs.openpdf)
    // Observability
    implementation(libs.bundles.logging)
    implementation(libs.bundles.monitoring)
    // JSON validation
    implementation(libs.json.skema)
    // Unit Testing
    testImplementation(libs.database.postgresql)
    testImplementation(libs.test.mockk)
    testImplementation(libs.test.ktor.server.test.host)
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.kotest.assertions.core)
    testImplementation(libs.test.kotest.assertions.json)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.postgres)
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
    jvmArgs = arrayOf(
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

dockerCompose {
    createNested("database").apply {
        useComposeFiles.set(listOf("db/db-compose.yaml"))
        environment.putAll(
            mapOf(
                "DB_USERNAME" to dbUsername,
                "DB_PASSWORD" to dbPassword
            )
        )
    }
}

tasks.named<JavaExec>("run").configure {
    dependsOn(tasks.named("databaseComposeUp"))
    dependsOn(tasks.named("liquibaseUpdate"))

    environment("JDBC_URL", "jdbc:postgresql://localhost:5432/auth")
    environment("APP_USERNAME", "app")
    environment("APP_PASSWORD", "app")
}
