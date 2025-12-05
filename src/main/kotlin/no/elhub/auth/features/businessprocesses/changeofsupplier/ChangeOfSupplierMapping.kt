package no.elhub.auth.features.businessprocesses.changeofsupplier

import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand

fun ChangeOfSupplierBusinessCommand.toRequestCommand(): RequestCommand =
    RequestCommand(
        type = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        validTo = this.validTo,
        meta = this.meta,
    )
