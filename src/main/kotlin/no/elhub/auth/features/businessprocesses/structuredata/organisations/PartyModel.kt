package no.elhub.auth.features.businessprocesses.structuredata.organisations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse

@Serializable
data class Attributes(
    val partyType: PartyType,
    val partyId: String,
    val name: String,
    val status: PartyStatus
) : JsonApiAttributes

@Serializable
enum class PartyType {
    @SerialName("BALANCE_SUPPLIER")
    BalanceSupplier
}

@Serializable
enum class PartyStatus {
    ACTIVE,
    INACTIVE
}

@Serializable
data class Relationships(
    val organizationNumber: JsonApiRelationshipToOne? = null
) : JsonApiRelationships

typealias PartyResponse = JsonApiResponse.SingleDocumentWithRelationships<Attributes, Relationships>
