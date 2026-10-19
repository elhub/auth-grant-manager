package no.elhub.auth.v1

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.tokenAuthorize
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.api.documents.routes.route
import no.elhub.auth.v1.features.documents.CreateDocumentHandler
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.documenthandlers.movein.MoveInAndChangeOfEnergySupplierDocumentHandler as MoveInHandlerImplementation
import no.elhub.auth.v1.documenthandlers.supplierchange.ChangeOfEnergySupplierDocumentHandler as SupplierChangeHandlerImplementation

const val V1_DOCUMENTS_PATH = "/access/v1/authorization-documents"

fun Application.module() {
    if (!environment.config.propertyOrNull("v1.enabled")?.getString().toBoolean()) {
        return
    }

    dependencies {
        provide<ChangeOfEnergySupplierDocumentHandler> {
            SupplierChangeHandlerImplementation()
        }
        provide<MoveInAndChangeOfEnergySupplierDocumentHandler> {
            MoveInHandlerImplementation()
        }
        provide<CreateDocumentHandler> {
            CreateDocumentHandler(resolve(), resolve(), resolve(), resolve())
        }
    }

    val handler: CreateDocumentHandler by dependencies
    val partyService: PartyService by dependencies
    routing {
        tokenAuthorize(AuthInfoPolicy) {
            route(V1_DOCUMENTS_PATH) {
                route(partyService, handler)
            }
        }
    }
}
