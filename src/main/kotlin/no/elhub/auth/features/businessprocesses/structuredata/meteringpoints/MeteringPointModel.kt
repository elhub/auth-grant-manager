package no.elhub.auth.features.businessprocesses.structuredata.meteringpoints

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import kotlin.Boolean

@Serializable
data class Attributes(
    val gridAccessContract: GridAccessContract? = null,
    val balanceSupplierContract: BalanceSupplierContract? = null,
    val accessType: AccessType? = null,
    val accountingPoint: AccountingPoint
) : JsonApiAttributes

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

@Serializable
data class Relationships(
    val endUser: JsonApiRelationshipToOne? = null
) : JsonApiRelationships

@Serializable
data class AccountingPoint(
    val blockedForSwitching: Boolean? = null,
    val meter: Meter? = null
)

@Serializable
data class Meter(
    val meterConstant: Double,
    val meterNumber: String,
)

typealias MeteringPointResponse = JsonApiResponse.SingleDocumentWithRelationships<Attributes, Relationships>
