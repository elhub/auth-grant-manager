package no.elhub.auth.features.common

import no.elhub.auth.features.requests.AuthorizationRequest

const val TEXT_VERSION_META_KEY = "textVersion"

object AuthorizationTextVersion {
    const val CHANGE_OF_BALANCE_SUPPLIER_FOR_PERSON = "v1"
    const val MOVE_IN_AND_CHANGE_OF_BALANCE_SUPPLIER_FOR_PERSON = "v1"

    fun forRequestType(type: AuthorizationRequest.Type): String =
        when (type) {
            AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson -> CHANGE_OF_BALANCE_SUPPLIER_FOR_PERSON
            AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson -> MOVE_IN_AND_CHANGE_OF_BALANCE_SUPPLIER_FOR_PERSON
        }
}

fun Map<String, String>.withRequestTextVersion(type: AuthorizationRequest.Type): Map<String, String> =
    this + (TEXT_VERSION_META_KEY to AuthorizationTextVersion.forRequestType(type))
