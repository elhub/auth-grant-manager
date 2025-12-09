package no.elhub.auth.features.documents.create.command

import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessCommand
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessMeta
import no.elhub.auth.features.common.party.PartyIdentifier

data class ChangeOfSupplierDocumentCommand(
    private val commandRequestedFrom: PartyIdentifier,
    private val commandRequestedTo: PartyIdentifier,
    private val commandRequestedBy: PartyIdentifier,
    val cosMeta: ChangeOfSupplierBusinessMeta,
) : DocumentCommand(
    requestedFrom = commandRequestedFrom,
    requestedTo = commandRequestedTo,
    requestedBy = commandRequestedBy,
    meta = cosMeta,
)

fun ChangeOfSupplierBusinessCommand.toDocumentCommand(): ChangeOfSupplierDocumentCommand =
    ChangeOfSupplierDocumentCommand(
        commandRequestedFrom = this.requestedFrom,
        commandRequestedTo = this.requestedTo,
        commandRequestedBy = this.requestedBy,
        cosMeta = this.meta,
    )
