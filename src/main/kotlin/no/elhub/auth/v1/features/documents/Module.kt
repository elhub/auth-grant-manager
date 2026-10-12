package no.elhub.auth.v1.features.documents

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.tokenAuthorize
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.features.documents.create.ChangeOfEnergySupplierDocumentActionHandler
import no.elhub.auth.v1.features.documents.create.ChangeOfEnergySupplierDocumentActionValidator
import no.elhub.auth.v1.features.documents.create.DocumentService
import no.elhub.auth.v1.features.documents.create.MoveInAndChangeOfEnergySupplierDocumentActionHandler
import no.elhub.auth.v1.features.documents.create.MoveInAndChangeOfEnergySupplierDocumentActionValidator
import no.elhub.auth.v1.features.documents.create.PlaceholderChangeOfEnergySupplierDocumentActionHandler
import no.elhub.auth.v1.features.documents.create.PlaceholderMoveInAndChangeOfEnergySupplierDocumentActionHandler
import no.elhub.auth.v1.features.documents.create.route

const val V1_DOCUMENTS_PATH = "/access/v1/authorization-documents"

fun Application.module() {
    if (!environment.config.propertyOrNull("v1.enabled")?.getString().toBoolean()) {
        return
    }

    dependencies {
        provide<ChangeOfEnergySupplierDocumentActionHandler> {
            PlaceholderChangeOfEnergySupplierDocumentActionHandler()
        }
        provide<MoveInAndChangeOfEnergySupplierDocumentActionHandler> {
            PlaceholderMoveInAndChangeOfEnergySupplierDocumentActionHandler()
        }
        provide<DocumentService> {
            DocumentService(resolve(), resolve(), resolve(), resolve())
        }
        provide<ChangeOfEnergySupplierDocumentActionValidator> { ChangeOfEnergySupplierDocumentActionValidator() }
        provide<MoveInAndChangeOfEnergySupplierDocumentActionValidator> {
            MoveInAndChangeOfEnergySupplierDocumentActionValidator()
        }
    }

    val handler: DocumentService by dependencies
    val partyService: PartyService by dependencies
    val changeOfEnergySupplierActionValidator: ChangeOfEnergySupplierDocumentActionValidator by dependencies
    val moveInAndChangeOfEnergySupplierActionValidator: MoveInAndChangeOfEnergySupplierDocumentActionValidator by dependencies

    routing {
        tokenAuthorize(AuthInfoPolicy) {
            route(V1_DOCUMENTS_PATH) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }
        }
    }
}
