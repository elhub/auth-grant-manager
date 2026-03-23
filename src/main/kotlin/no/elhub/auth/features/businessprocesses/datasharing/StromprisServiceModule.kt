package no.elhub.auth.features.businessprocesses.datasharing

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies

fun Application.stromprisServiceModule() {
    val appEnvironment = environment

    dependencies {
        provide<Boolean>(name = "validateBalanceSupplierContractName") {
            appEnvironment.config.propertyOrNull("dataSharing.validateContractNameFeature")
                ?.getString()?.toBoolean() ?: false
        }

        provide<JwtTokenProvider> {
            val appConfig = resolve<ApplicationConfig>()
            val stromprisServiceConfig = appConfig.config("dataSharing.stromprisService")
            val idpTokenUrl = appConfig.config("idp").property("tokenUrl").getString()
            JwtTokenProviderImpl(
                httpClient = resolve("commonHttpClient"),
                authConfig = AuthConfig(
                    clientId = stromprisServiceConfig.property("idp.clientId").getString(),
                    clientSecret = stromprisServiceConfig.property("idp.clientSecret").getString(),
                    tokenUrl = idpTokenUrl
                )
            )
        }

        provide<StromprisApiConfig> {
            val stromprisServiceConfig = resolve<ApplicationConfig>().config("dataSharing.stromprisService")
            StromprisApiConfig(
                serviceUrl = stromprisServiceConfig.property("serviceUrl").getString()
            )
        }

        provide<StromprisService> {
            StromprisApi(
                stromprisApiConfig = resolve(),
                client = resolve("commonHttpClient"),
                tokenProvider = resolve()
            )
        }
    }
}
