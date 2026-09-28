package no.elhub.auth.v0.features.documents

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.common.documents.pdf.pdfModule
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.tokenAuthorize
import no.elhub.auth.v0.features.documents.common.DocumentPropertiesRepository
import no.elhub.auth.v0.features.documents.common.DocumentRepository
import no.elhub.auth.v0.features.documents.common.ExposedDocumentPropertiesRepository
import no.elhub.auth.v0.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.v0.features.documents.common.SignatureService
import no.elhub.auth.v0.features.documents.common.V0SignatureServiceAdapter
import no.elhub.auth.v0.features.documents.create.FileGenerator
import no.elhub.auth.v0.features.documents.create.V0FileGeneratorAdapter
import no.elhub.auth.v0.features.documents.confirm.Handler as ConfirmHandler
import no.elhub.auth.v0.features.documents.confirm.route as confirmRoute
import no.elhub.auth.v0.features.documents.create.Handler as CreateHandler
import no.elhub.auth.v0.features.documents.create.route as createRoute
import no.elhub.auth.v0.features.documents.get.Handler as GetHandler
import no.elhub.auth.v0.features.documents.get.route as getRoute
import no.elhub.auth.v0.features.documents.query.Handler as QueryHandler
import no.elhub.auth.v0.features.documents.query.route as queryRoute

const val DOCUMENTS_PATH = "/access/v0/authorization-documents"

fun Application.module() {
    // PDF services are only required by the v0 authorization-document flow. This must be
    // initialized before registering the v0 dependencies below, since they resolve the
    // common PDF services when the handlers are created.
    pdfModule()

    dependencies {
        provide<FileGenerator> { V0FileGeneratorAdapter(resolve<PdfGenerator>()) }
        provide<SignatureService> { V0SignatureServiceAdapter(resolve<PdfSigner>(), resolve<PdfSignatureValidator>()) }
        provide<DocumentRepository> { ExposedDocumentRepository(resolve(), resolve(), resolve(), resolve(), resolve()) }
        provide<DocumentPropertiesRepository> { ExposedDocumentPropertiesRepository() }
        provide<ConfirmHandler> { ConfirmHandler(resolve(), resolve(), resolve(), resolve()) }
        provide<CreateHandler> { CreateHandler(resolve(), resolve(), resolve(), resolve(), resolve()) }
        provide<GetHandler> { GetHandler(resolve(), resolve()) }
        provide<QueryHandler> { QueryHandler(resolve(), resolve()) }
    }

    val createHandler: CreateHandler by dependencies
    val confirmHandler: ConfirmHandler by dependencies
    val getHandler: GetHandler by dependencies
    val queryHandler: QueryHandler by dependencies

    routing {
        tokenAuthorize(AuthInfoPolicy) {
            route(DOCUMENTS_PATH) {
                createRoute(createHandler)
                confirmRoute(confirmHandler)
                getRoute(getHandler)
                queryRoute(queryHandler)
            }
        }
    }
}
