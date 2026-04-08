package no.elhub.auth.features.businessprocesses.structuredata.meteringpoints

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import no.elhub.auth.features.businessprocesses.common.AuthConfig
import no.elhub.auth.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.features.businessprocesses.common.JwtTokenProviderImpl

fun Application.meteringPointsServiceModule() {
    dependencies {
        provide<JwtTokenProvider> {
            val appConfig = resolve<ApplicationConfig>()
            val meteringPointsServiceConfig = appConfig.config("structureData.meteringPointsService")
            val idpTokenUrl = appConfig.config("idp").property("tokenUrl").getString()
            JwtTokenProviderImpl(
                httpClient = resolve("commonHttpClient"),
                authConfig = AuthConfig(
                    clientId = meteringPointsServiceConfig.property("idp.clientId").getString(),
                    clientSecret = meteringPointsServiceConfig.property("idp.clientSecret").getString(),
                    tokenUrl = idpTokenUrl
                )
            )
        }
        provide<MeteringPointsApiConfig> {
            val meteringPointsApiConfig = resolve<ApplicationConfig>().config("structureData.meteringPointsService")
            MeteringPointsApiConfig(
                serviceUrl = meteringPointsApiConfig.property("serviceUrl").getString()
            )
        }
        provide<MeteringPointsService> {
            MeteringPointsApi(
                meteringPointsApiConfig = resolve(),
                client = resolve("commonHttpClient"),
                tokenProvider = resolve()
            )
        }
    }
}
