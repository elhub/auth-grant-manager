package no.elhub.auth.features.requests.create.command

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessCommand
import no.elhub.auth.features.requests.AuthorizationRequest

fun ChangeOfSupplierBusinessCommand.toRequestCommand(validTo: LocalDate): RequestCommand =
    RequestCommand(
        type = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        validTo = validTo,
        meta = this.meta,
    )
