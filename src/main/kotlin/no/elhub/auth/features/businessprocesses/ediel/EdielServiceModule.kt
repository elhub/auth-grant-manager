package no.elhub.auth.features.businessprocesses.ediel

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies

fun Application.edielServiceModule() {
    val appEnvironment = environment

    dependencies {
        provide<Boolean>(name = "validateRedirectUriFeature") {
            appEnvironment.config.propertyOrNull("ediel.validateRedirectUriFeature")
                ?.getString()?.toBoolean() ?: true
        }

        provide<EdielApiConfig> {
            val edielApiConfig = resolve<ApplicationConfig>().config("ediel")
            EdielApiConfig(
                serviceUrl = edielApiConfig.property("serviceUrl").getString(),
                basicAuthConfig = BasicAuthConfig(
                    username = edielApiConfig.property("authentication.basic.username").getString(),
                    password = edielApiConfig.property("authentication.basic.password").getString()
                ),
                environment = edielApiConfig.property("environment").getString().toEdielEnvironment()
            )
        }

        provide<EdielEnvironment>(name = "edielEnvironment") { resolve<EdielApiConfig>().environment }

        provide<EdielService> {
            EdielApi(
                edielApiConfig = resolve(),
                client = resolve()
            )
        }
    }
}

private fun String.toEdielEnvironment(): EdielEnvironment =
    when (trim().uppercase()) {
        "TEST" -> EdielEnvironment.TEST
        "PRODUCTION" -> EdielEnvironment.PRODUCTION
        else -> throw IllegalArgumentException("Invalid ediel.environment value '$this'. Allowed values: test, production")
    }
