package no.elhub.auth.v1.features.grants

import no.elhub.auth.v1.Errors

sealed class UpdateGrantError(override val detail: String) : Errors(detail) {
    class NotAuthorized(override val detail: String) : UpdateGrantError(detail)

    class TypeMismatch : UpdateGrantError("data.type must match the authorization grant's type.")

    class InvalidTransition : UpdateGrantError(
        "Only active AuthorizationGrants may be revoked, and only active consumable grants may be exhausted.",
    )

    class Expired : UpdateGrantError("AuthorizationGrant has expired.")
}
