package no.elhub.auth.features.documents.create.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.filegenerator.SupportedLanguage
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
    val startDate: LocalDate? = null,
    val language: SupportedLanguage = SupportedLanguage.DEFAULT,
) : JsonApiResourceMeta

typealias JsonApiCreateDocumentRequest = JsonApiRequest.SingleDocumentWithMeta<CreateDocumentRequestAttributes, CreateDocumentMeta>

fun JsonApiCreateDocumentRequest.toModel(authorizedParty: AuthorizationParty): CreateDocumentModel =
    CreateDocumentModel(
        authorizedParty = authorizedParty,
        documentType = this.data.attributes.documentType,
        meta = this.data.meta,
    )
