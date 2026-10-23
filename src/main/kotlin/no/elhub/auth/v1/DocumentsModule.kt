package no.elhub.auth.v1

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.tokenAuthorize
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.api.documents.routes.route
import no.elhub.auth.v1.features.documents.ConfirmDocumentHandler
import no.elhub.auth.v1.features.documents.CreateDocumentHandler
import no.elhub.auth.v1.features.documents.GetDocumentHandler
import no.elhub.auth.v1.features.documents.ListDocumentsHandler
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.repositories.DocumentRepository
import no.elhub.auth.v1.repositories.ExposedDocumentRepository
import no.elhub.auth.v1.repositories.ExposedGrantRepository
import no.elhub.auth.v1.repositories.ExposedPartyRepository
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.PartyRepository
import no.elhub.auth.v1.documenthandlers.movein.MoveInAndChangeOfEnergySupplierDocumentHandler as MoveInHandlerImplementation
import no.elhub.auth.v1.documenthandlers.supplierchange.ChangeOfEnergySupplierDocumentHandler as SupplierChangeHandlerImplementation

const val V1_DOCUMENTS_PATH = "/access/v1/authorization-documents"

fun Application.module() {
    if (!environment.config.propertyOrNull("v1.enabled")?.getString().toBoolean()) {
        return
    }

    dependencies {
        provide<PartyRepository> { ExposedPartyRepository() }
        provide<GrantRepository> { ExposedGrantRepository(resolve()) }
        provide<DocumentRepository> { ExposedDocumentRepository(resolve(), resolve()) }
        provide<ChangeOfEnergySupplierDocumentHandler> {
            SupplierChangeHandlerImplementation(resolve())
        }
        provide<MoveInAndChangeOfEnergySupplierDocumentHandler> {
            MoveInHandlerImplementation(resolve())
        }
        provide<CreateDocumentHandler> {
            CreateDocumentHandler(resolve(), resolve(), resolve(), resolve(), resolve())
        }
        provide<ConfirmDocumentHandler> {
            ConfirmDocumentHandler(resolve(), resolve(), resolve())
        }
        provide<GetDocumentHandler> {
            GetDocumentHandler(resolve(), resolve())
        }
        provide<ListDocumentsHandler> {
            ListDocumentsHandler(resolve(), resolve())
        }
    }

    val createHandler: CreateDocumentHandler by dependencies
    val confirmHandler: ConfirmDocumentHandler by dependencies
    val getHandler: GetDocumentHandler by dependencies
    val listHandler: ListDocumentsHandler by dependencies
    val partyService: PartyService by dependencies
    routing {
        tokenAuthorize(AuthInfoPolicy) {
            route(V1_DOCUMENTS_PATH) {
                route(partyService, createHandler)
                route(getHandler)
                route(listHandler)
                route(confirmHandler)
            }
        }
    }
}
