package no.elhub.auth.v1.features.grants

import no.elhub.auth.v1.Errors

sealed class UpdateGrantError(override val detail: String) : Errors(detail) {
    class NotAuthorized : UpdateGrantError("Only systems may update an AuthorizationGrant.")

    class InvalidTransition : UpdateGrantError(
        "Only active AuthorizationGrants may be revoked, and only active consumable grants may be exhausted.",
    )

    class Expired : UpdateGrantError("AuthorizationGrant has expired.")
}
