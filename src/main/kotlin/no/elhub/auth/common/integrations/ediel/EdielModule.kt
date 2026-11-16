package no.elhub.auth.common.integrations.ediel

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies

fun Application.edielModule() {
    dependencies {
        provide<EdielConfig> {
            val config = resolve<ApplicationConfig>().config("ediel")
            EdielConfig(
                serviceUrl = config.property("serviceUrl").getString(),
                username = config.property("authentication.basic.username").getString(),
                password = config.property("authentication.basic.password").getString(),
                environment = config.property("environment").getString().toEdielEnvironment(),
            )
        }
        provide<EdielRedirectClient> {
            HttpEdielRedirectClient(resolve(), resolve("proxyHttpClient"))
        }
    }
}

private fun String.toEdielEnvironment(): EdielEnvironment =
    when (trim().uppercase()) {
        "TEST" -> EdielEnvironment.TEST
        "PRODUCTION" -> EdielEnvironment.PRODUCTION
        else -> throw IllegalArgumentException("Invalid ediel.environment value '$this'. Allowed values: test, production")
    }
