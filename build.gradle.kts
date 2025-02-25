plugins {
    id("no.elhub.devxp.kotlin-service") version "0.2.5"
    alias(libs.plugins.google.cloud.tools.jib)
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp.plugin)
    id("org.liquibase.gradle") version "3.0.1"
    id("com.avast.gradle.docker-compose") version "0.17.12"
}

buildscript {
    repositories {
        maven(url = "https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
    }
    dependencies {
        classpath("org.liquibase:liquibase-core:4.28.0")
    }
}

dependencies {
    // implementation("org.liquibase:liquibase-core:4.28.0")
    // Ktor
    implementation(libs.bundles.ktor.server)

    // Koin
    implementation(libs.bundles.ktor.koin)
    ksp(libs.di.koin.ksp.compiler)

    // Logging
    implementation(libs.logging.logback.classic)

    // Monitoring
    implementation(libs.bundles.ktor.monitoring)

    // Liquibase
    liquibaseRuntime("org.liquibase:liquibase-core:4.28.0")
    liquibaseRuntime("org.liquibase:liquibase-groovy-dsl:2.1.1")
    liquibaseRuntime("info.picocli:picocli:4.7.5")
    liquibaseRuntime("org.yaml:snakeyaml:1.28")
    liquibaseRuntime("org.postgresql:postgresql:42.7.2")

    // Testing
    testImplementation(libs.test.ktor.server.test.host)
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.kotest.assertions.core)
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

liquibase {
    jvmArgs = arrayOf(
        "-Dliquibase.command.url=jdbc:postgresql://localhost:5432/postgres",
        "-Dliquibase.command.username=postgres",
        "-Dliquibase.command.password=postgres",
        "-Dliquibase.command.driver=org.postgresql.Driver",
        "-Dliquibase.command.changeLogFile=db/db-changelog.yaml"
    )
    activities.register("main")
}

dockerCompose {
    createNested("database").apply {
        useComposeFiles.set(listOf("db/db-compose.yaml"))
    }
}
