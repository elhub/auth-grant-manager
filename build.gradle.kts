plugins {
    alias(libs.plugins.elhub.gradle.plugin)
    alias(libs.plugins.google.cloud.tools.jib)
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
    implementation(libs.bundles.ktor.server)
    // Koin
    implementation(libs.bundles.ktor.koin)
    ksp(libs.di.koin.ksp.compiler)
    // Logging
    implementation(libs.logging.logback.classic)
    // Monitoring
    implementation(libs.bundles.ktor.monitoring)
    // Liquibase
    liquibaseRuntime(libs.database.liquibase.core)
    liquibaseRuntime(libs.cli.picocli)
    liquibaseRuntime(libs.serialization.yaml.snakeyaml)
    liquibaseRuntime(libs.database.postgresql)
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
