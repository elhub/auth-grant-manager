package no.elhub.auth.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.elhub.auth.features.documents.AuthorizationDocumentHandler
import no.elhub.auth.features.grants.AuthorizationGrantService
import no.elhub.auth.features.requests.AuthorizationRequestHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

val appModule = module {
    singleOf(::AuthorizationGrantService) { bind<AuthorizationGrantService>() }
    singleOf(::AuthorizationDocumentHandler) { bind<AuthorizationDocumentHandler>() }
    singleOf(::AuthorizationRequestHandler) { bind<AuthorizationRequestHandler>() }
}

fun Application.configureKoin() {
    install(Koin) {
        modules(appModule)
    }
}
