package no.elhub.auth.v1.integrations.pdp

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies
import no.elhub.auth.v0.features.businessprocesses.common.AuthConfig
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProviderImpl
import no.elhub.auth.v1.common.AuthorizationRulesService

fun Application.authorizationRulesServiceModule() {
    dependencies {
        provide<JwtTokenProvider>(name = "pdpServiceTokenProvider") {
            val appConfig = resolve<ApplicationConfig>()
            val pdpConfig = appConfig.config("pdp")
            JwtTokenProviderImpl(
                httpClient = resolve("commonHttpClient"),
                authConfig = AuthConfig(
                    clientId = pdpConfig.property("idp.clientId").getString(),
                    clientSecret = pdpConfig.property("idp.clientSecret").getString(),
                    tokenUrl = appConfig.property("idp.tokenUrl").getString(),
                ),
            )
        }

        provide<AuthorizationRulesService> {
            PdpAuthorizationRulesService(
                httpClient = resolve("commonHttpClient"),
                pdpBaseUrl = resolve<ApplicationConfig>().property("pdp.baseUrl").getString(),
                tokenProvider = resolve("pdpServiceTokenProvider"),
            )
        }
    }
}
