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
                )
            )
        }
        single<EdielService> {
            EdielApi(
                edielApiConfig = get(),
                client = get(named("commonHttpClient"))
            )
        }
    }
}
