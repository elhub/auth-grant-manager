package no.elhub.auth.features.businessprocesses.structuredata.domain

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse

@Serializable
data class Attributes(
    val gridAccessContract: GridAccessContract? = null,
    val balanceSupplierContract: BalanceSupplierContract? = null,
    val accessType: AccessType? = null,
    val isHistoricMeteringPoint: Boolean? = null
) : JsonApiAttributes {
    @Serializable
    data class GridAccessContract(
        val start: String,
        val end: String? = null,
        val partyFunction: PartyFunction
    )

    @Serializable
    data class BalanceSupplierContract(
        val start: String,
        val end: String? = null,
        val partyFunction: PartyFunction,
        val contractOfLastResort: Boolean
    )

    @Serializable
    data class PartyFunction(
        val name: String,
        val partyId: String,
    )

    @Serializable
    enum class AccessType {
        OWNED,
        SHARED
    }
}

@Serializable
data class Relationships(
    val endUser: JsonApiRelationshipToOne? = null
) : JsonApiRelationships {
    companion object {
        const val END_USER = "end-user"
    }
}

typealias MeteringPointResponse = JsonApiResponse.SingleDocumentWithRelationships<Attributes, Relationships>
