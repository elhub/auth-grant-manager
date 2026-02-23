package no.elhub.auth.features.businessprocesses.ediel

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.core.qualifier.named
import org.koin.ktor.plugin.koinModule

fun Application.edielServiceModule() {
    koinModule {
        single {
            val edielApiConfig = get<ApplicationConfig>().config("ediel")
            EdielApiConfig(
                serviceUrl = edielApiConfig.property("serviceUrl").getString(),
                basicAuthConfig = BasicAuthConfig(
                    username = edielApiConfig.property("authentication.basic.username").getString(),
                    password = edielApiConfig.property("authentication.basic.password").getString()
                ),
                environment = edielApiConfig.property("environment").getString().toEdielEnvironment()
            )
        }
        single(named("edielEnvironment")) { get<EdielApiConfig>().environment }
        single<EdielService> {
            EdielApi(
                edielApiConfig = get(),
                client = get(named("commonHttpClient"))
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
