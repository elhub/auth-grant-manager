package no.elhub.auth.features.common

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

typealias AuthorizationMetadata = Map<String, JsonElement>

object AuthorizationMetadataKeys {
    const val BALANCE_SUPPLIER_CONTRACT_NAME = "balanceSupplierContractName"
    const val BALANCE_SUPPLIER_NAME = "balanceSupplierName"
    const val LANGUAGE = "language"
    const val MOVE_IN_DATE = "moveInDate"
    const val REDIRECT_URI = "redirectURI"
    const val REQUESTED_FOR_METERING_POINT_ADDRESS = "requestedForMeteringPointAddress"
    const val REQUESTED_FOR_METERING_POINT_ID = "requestedForMeteringPointId"
    const val REQUESTED_FOR_METER_NUMBER = "requestedForMeterNumber"
    const val REQUESTED_FROM_NAME = "requestedFromName"
    const val TEXT_VERSION = "textVersion"
}

fun Map<String, String>.toStringAuthorizationMetadata(): AuthorizationMetadata =
    mapValues { (_, value) -> JsonPrimitive(value) }
