package no.elhub.auth.features.documents.create.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.AuthorizationDocument
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
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

typealias JsonApiCreateDocumentRequest = JsonApiRequest.SingleDocumentWithMeta<CreateDocumentRequestAttributes, CreateDocumentMeta>

fun JsonApiCreateDocumentRequest.toModel(): CreateDocumentModel =
    CreateDocumentModel(
        documentType = this.data.attributes.documentType,
        meta = this.data.meta,
    )
