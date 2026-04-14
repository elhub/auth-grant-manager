package no.elhub.auth.features.documents.create.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.CreateDocumentBusinessMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentCoreMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class CreateDocumentRequestAttributes(
    val documentType: AuthorizationDocument.Type
) : JsonApiAttributes

@Serializable
data class CreateDocumentMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val moveInDate: LocalDate? = null,
    val language: SupportedLanguageDTO = SupportedLanguageDTO.DEFAULT,
) : JsonApiResourceMeta

typealias JsonApiCreateDocumentRequest = JsonApiRequest.SingleDocumentWithMeta<CreateDocumentRequestAttributes, CreateDocumentMeta>

fun JsonApiCreateDocumentRequest.toModel(authorizedParty: AuthorizationParty): CreateDocumentModel =
    CreateDocumentModel(
        authorizedParty = authorizedParty,
        documentType = this.data.attributes.documentType,
        coreMeta = CreateDocumentCoreMeta(
            requestedBy = this.data.meta.requestedBy,
            requestedTo = this.data.meta.requestedTo,
            requestedFrom = this.data.meta.requestedFrom
        ),
        businessMeta = CreateDocumentBusinessMeta(
            requestedFromName = this.data.meta.requestedFromName,
            requestedForMeteringPointId = this.data.meta.requestedForMeteringPointId,
            requestedForMeteringPointAddress = this.data.meta.requestedForMeteringPointAddress,
            balanceSupplierName = this.data.meta.balanceSupplierName,
            balanceSupplierContractName = this.data.meta.balanceSupplierContractName,
            moveInDate = this.data.meta.moveInDate,
            language = this.data.meta.language,
        ),
    )
