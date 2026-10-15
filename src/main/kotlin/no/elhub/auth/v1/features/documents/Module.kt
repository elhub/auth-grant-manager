package no.elhub.auth.v1.features.documents

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.tokenAuthorize
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.features.documents.create.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.create.DocumentService
import no.elhub.auth.v1.features.documents.create.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.create.PlaceholderChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.create.PlaceholderMoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.create.route

const val V1_DOCUMENTS_PATH = "/access/v1/authorization-documents"

fun Application.module() {
    if (!environment.config.propertyOrNull("v1.enabled")?.getString().toBoolean()) {
        return
    }

    dependencies {
        provide<ChangeOfEnergySupplierDocumentHandler> {
            PlaceholderChangeOfEnergySupplierDocumentHandler()
        }
        provide<MoveInAndChangeOfEnergySupplierDocumentHandler> {
            PlaceholderMoveInAndChangeOfEnergySupplierDocumentHandler()
        }
        provide<DocumentService> {
            DocumentService(resolve(), resolve(), resolve(), resolve())
        }
    }

    val handler: DocumentService by dependencies
    val partyService: PartyService by dependencies
    routing {
        tokenAuthorize(AuthInfoPolicy) {
            route(V1_DOCUMENTS_PATH) {
                route(partyService, handler)
            }
        }
    }
}
