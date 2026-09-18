package no.elhub.auth.v0.features.documents.common

import kotlinx.datetime.LocalDate
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.documents.AuthorizationDocument
import no.elhub.auth.v0.features.documents.create.dto.SupportedLanguageDTO

data class CreateDocumentBusinessModel(
    val authorizedParty: AuthorizationParty,
    val documentType: AuthorizationDocument.Type,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val meta: CreateDocumentBusinessMeta,
)

data class CreateDocumentBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val moveInDate: LocalDate? = null,
    val language: SupportedLanguageDTO = SupportedLanguageDTO.DEFAULT,
)
