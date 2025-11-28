package no.elhub.auth.features.requests

import no.elhub.auth.features.requests.common.ExposedRequestPropertiesRepository
import no.elhub.auth.features.requests.common.ExposedRequestRepository
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeHandler
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeOrchestrator
import no.elhub.auth.features.requests.create.requesttypes.changeofsupplierconfirmation.ChangeOfSupplierConfirmationRequestTypeHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import no.elhub.auth.features.requests.confirm.Handler as ConfirmHandler
import no.elhub.auth.features.requests.create.Handler as CreateHandler
import no.elhub.auth.features.requests.get.Handler as GetHandler
import no.elhub.auth.features.requests.query.Handler as QueryHandler

val requestsModule =
    module {
        singleOf(::ExposedRequestRepository) bind RequestRepository::class
        singleOf(::ExposedRequestPropertiesRepository) bind RequestPropertiesRepository::class
        singleOf(::ChangeOfSupplierConfirmationRequestTypeHandler) bind RequestTypeHandler::class
        singleOf(::RequestTypeOrchestrator)
        singleOf(::ConfirmHandler)
        singleOf(::CreateHandler)
        singleOf(::GetHandler)
        singleOf(::QueryHandler)
    }
