package no.elhub.auth.features.businessprocesses.datasharing

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.core.qualifier.named
import org.koin.ktor.plugin.koinModule

fun Application.stromprisServiceModule() {
    koinModule {
        single { environment.config }
        single(named("stromprisValidation")) {
            environment.config.propertyOrNull("dataSharing.stromprisValidationFeature")?.getString()?.toBoolean() ?: false
        }
        single<JwtTokenProvider> {
            val stromprisServiceConfig = get<ApplicationConfig>().config("dataSharing.stromprisService")
            JwtTokenProviderImpl(
                httpClient = get(named("commonHttpClient")),
                authConfig = AuthConfig(
                    clientId = stromprisServiceConfig.property("idp.clientId").getString(),
                    clientSecret = stromprisServiceConfig.property("idp.clientSecret").getString(),
                    tokenUrl = stromprisServiceConfig.property("idp.tokenUrl").getString()
                )
            )
        }
        single {
            val stromprisServiceConfig = get<ApplicationConfig>().config("businessProcesses.dataSharing.stromprisService")
            StromprisApiConfig(
                serviceUrl = stromprisServiceConfig.property("serviceUrl").getString(),
            )
        }
        single<StromprisService> {
            StromprisApi(
                stromprisApiConfig = get(),
                client = get(named("commonHttpClient")),
                tokenProvider = get()
            )
        }
    }
}
