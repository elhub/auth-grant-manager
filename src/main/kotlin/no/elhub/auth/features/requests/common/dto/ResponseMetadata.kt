package no.elhub.auth.features.requests.common.dto

import no.elhub.auth.features.common.AuthorizationMetadata
import no.elhub.auth.features.common.AuthorizationMetadataKeys
import no.elhub.auth.features.common.toResponseMetadata
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty

private val commonPersonPropertyKeys = setOf(
    AuthorizationMetadataKeys.BALANCE_SUPPLIER_CONTRACT_NAME,
    AuthorizationMetadataKeys.BALANCE_SUPPLIER_NAME,
    AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ADDRESS,
    AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ID,
    AuthorizationMetadataKeys.REQUESTED_FOR_METER_NUMBER,
    AuthorizationMetadataKeys.REQUESTED_FROM_NAME,
)

private val personRequestPropertyKeys = commonPersonPropertyKeys + setOf(
    AuthorizationMetadataKeys.LANGUAGE,
    AuthorizationMetadataKeys.REDIRECT_URI,
    AuthorizationMetadataKeys.TEXT_VERSION,
)

fun List<AuthorizationRequestProperty>.toRequestResponseMetadata(
    type: AuthorizationRequest.Type,
): AuthorizationMetadata {
    val allowedKeys = when (type) {
        AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson ->
            personRequestPropertyKeys

        AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson ->
            personRequestPropertyKeys + AuthorizationMetadataKeys.MOVE_IN_DATE
    }
    return toResponseMetadata(
        entries = map { it.key to it.value },
        allowedKeys = allowedKeys,
        context = "AuthorizationRequest.${type.name}",
    )
}
