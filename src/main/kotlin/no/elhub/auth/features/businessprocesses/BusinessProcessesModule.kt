package no.elhub.auth.features.businessprocesses

import io.ktor.server.application.Application
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.movein.MoveInBusinessHandler
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.common.ProxyDocumentBusinessHandler
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.plugin.koinModule

fun Application.businessProcessesModule() {
    koinModule {
        singleOf(::ChangeOfSupplierBusinessHandler)
        singleOf(::MoveInBusinessHandler)
        singleOf(::ProxyDocumentBusinessHandler) bind DocumentBusinessHandler::class
        singleOf(::ProxyRequestBusinessHandler) bind RequestBusinessHandler::class
    }
}
