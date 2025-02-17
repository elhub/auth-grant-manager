plugins {
    id("no.elhub.devxp.kotlin-service") version "0.2.5"
    alias(libs.plugins.google.cloud.tools.jib)
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp.plugin)
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
