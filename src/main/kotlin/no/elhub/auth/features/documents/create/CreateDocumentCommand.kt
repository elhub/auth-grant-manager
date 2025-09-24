package no.elhub.auth.features.documents.create

import no.elhub.auth.features.documents.AuthorizationDocument

data class CreateDocumentCommand(
    val type: AuthorizationDocument.Type,
    val requestedFrom: String,
    val requestedFromName: String,
    val requestedBy: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val meteringPointId: String,
    val meteringPointAddress: String,
)
