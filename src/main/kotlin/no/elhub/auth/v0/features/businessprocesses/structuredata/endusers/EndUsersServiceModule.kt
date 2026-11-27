package no.elhub.auth.v0.features.businessprocesses.structuredata.endusers

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import no.elhub.auth.v0.features.businessprocesses.common.AuthConfig
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProviderImpl

fun Application.endUsersServiceModule() {
    dependencies {
        provide<JwtTokenProvider>(name = "endUsersServiceTokenProvider") {
            val appConfig = resolve<ApplicationConfig>()
            val endUsersServiceConfig = appConfig.config("structureData.endUsersService")
            val idpTokenUrl = appConfig.config("idp").property("tokenUrl").getString()
            JwtTokenProviderImpl(
                httpClient = resolve("commonHttpClient"),
                authConfig = AuthConfig(
                    clientId = endUsersServiceConfig.property("idp.clientId").getString(),
                    clientSecret = endUsersServiceConfig.property("idp.clientSecret").getString(),
                    tokenUrl = idpTokenUrl,
                )
            )
        }
        provide<EndUsersApiConfig> {
            val endUsersApiConfig = resolve<ApplicationConfig>().config("structureData.endUsersService")
            EndUsersApiConfig(
                serviceUrl = endUsersApiConfig.property("serviceUrl").getString()
            )
        }
        provide<EndUsersService> {
            EndUsersApi(
                endUsersApiConfig = resolve(),
                client = resolve("commonHttpClient"),
                tokenProvider = resolve("endUsersServiceTokenProvider"),
            )
        }
    }
}
