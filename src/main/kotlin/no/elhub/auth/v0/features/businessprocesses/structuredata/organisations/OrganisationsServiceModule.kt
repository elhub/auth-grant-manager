package no.elhub.auth.v0.features.businessprocesses.structuredata.organisations

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import no.elhub.auth.v0.features.businessprocesses.common.AuthConfig
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProviderImpl

fun Application.organisationsServiceModule() {
    dependencies {
        provide<JwtTokenProvider>(name = "organisationsServiceTokenProvider") {
            val appConfig = resolve<ApplicationConfig>()
            val organisationsServiceConfig = appConfig.config("structureData.organisationsService")
            val idpTokenUrl = appConfig.config("idp").property("tokenUrl").getString()
            JwtTokenProviderImpl(
                httpClient = resolve("commonHttpClient"),
                authConfig = AuthConfig(
                    clientId = organisationsServiceConfig.property("idp.clientId").getString(),
                    clientSecret = organisationsServiceConfig.property("idp.clientSecret").getString(),
                    tokenUrl = idpTokenUrl,
                )
            )
        }
        provide<OrganisationsApiConfig> {
            val organisationsApiConfig = resolve<ApplicationConfig>().config("structureData.organisationsService")
            OrganisationsApiConfig(
                serviceUrl = organisationsApiConfig.property("serviceUrl").getString()
            )
        }

        provide<OrganisationsService> {
            OrganisationsApi(
                organisationsApiConfig = resolve(),
                client = resolve("commonHttpClient"),
                tokenProvider = resolve("organisationsServiceTokenProvider")
            )
        }
    }
}
