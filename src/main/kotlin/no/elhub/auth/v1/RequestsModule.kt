package no.elhub.auth.v1

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.tokenAuthorize
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.api.requests.routes.route
import no.elhub.auth.v1.features.requests.CreateRequestHandler
import no.elhub.auth.v1.features.requests.creation.ChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.features.requests.creation.EnergySupplierFrameworkAgreementRequestHandler
import no.elhub.auth.v1.features.requests.creation.MoveInAndChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.repositories.ExposedRequestRepository
import no.elhub.auth.v1.repositories.RequestRepository
import no.elhub.auth.v1.requesthandlers.frameworkagreement.EnergySupplierFrameworkAgreementRequestHandler as FrameworkAgreementHandlerImplementation
import no.elhub.auth.v1.requesthandlers.movein.MoveInAndChangeOfEnergySupplierRequestHandler as MoveInHandlerImplementation
import no.elhub.auth.v1.requesthandlers.supplierchange.ChangeOfEnergySupplierRequestHandler as SupplierChangeHandlerImplementation

const val V1_REQUESTS_PATH = "/access/v1/authorization-requests"

fun Application.requestsModule() {
    if (!environment.config.propertyOrNull("v1.enabled")?.getString().toBoolean()) {
        return
    }

    dependencies {
        provide<RequestRepository> { ExposedRequestRepository(resolve(), resolve()) }
        provide<ChangeOfEnergySupplierRequestHandler> { SupplierChangeHandlerImplementation() }
        provide<MoveInAndChangeOfEnergySupplierRequestHandler> { MoveInHandlerImplementation() }
        provide<EnergySupplierFrameworkAgreementRequestHandler> { FrameworkAgreementHandlerImplementation() }
        provide<CreateRequestHandler> { CreateRequestHandler(resolve(), resolve(), resolve(), resolve()) }
    }

    val createHandler: CreateRequestHandler by dependencies
    val partyService: PartyService by dependencies
    routing {
        tokenAuthorize(AuthInfoPolicy) {
            route(V1_REQUESTS_PATH) {
                route(partyService, createHandler)
            }
        }
    }
}
