package no.elhub.auth.features.documents.common.dto

import no.elhub.auth.features.common.AuthorizationMetadata
import no.elhub.auth.features.common.AuthorizationMetadataKeys
import no.elhub.auth.features.common.toResponseMetadata
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty

private val commonPersonPropertyKeys = setOf(
    AuthorizationMetadataKeys.BALANCE_SUPPLIER_CONTRACT_NAME,
    AuthorizationMetadataKeys.BALANCE_SUPPLIER_NAME,
    AuthorizationMetadataKeys.LANGUAGE,
    AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ADDRESS,
    AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ID,
    AuthorizationMetadataKeys.REQUESTED_FOR_METER_NUMBER,
    AuthorizationMetadataKeys.REQUESTED_FROM_NAME,
)

fun List<AuthorizationDocumentProperty>.toDocumentResponseMetadata(
    type: AuthorizationDocument.Type,
): AuthorizationMetadata {
    val allowedKeys = when (type) {
        AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson ->
            commonPersonPropertyKeys

        AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson ->
            commonPersonPropertyKeys + AuthorizationMetadataKeys.MOVE_IN_DATE
    }
    return toResponseMetadata(
        entries = map { it.key to it.value },
        allowedKeys = allowedKeys,
        context = "AuthorizationDocument.${type.name}",
    )
}
