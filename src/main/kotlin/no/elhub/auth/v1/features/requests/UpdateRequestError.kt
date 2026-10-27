package no.elhub.auth.v1.features.requests

import no.elhub.auth.v1.Errors

sealed class UpdateRequestError(override val detail: String) : Errors(detail) {
    class NotAuthorized : UpdateRequestError("Only the requestedOf party may approve or reject this AuthorizationRequest.")

    class InvalidTransition : UpdateRequestError("Only 'Approved' and 'Rejected' statuses are allowed.")

    class AlreadyProcessed : UpdateRequestError("AuthorizationRequest must be in 'Pending' status to update.")

    class Expired : UpdateRequestError("AuthorizationRequest has expired.")

    class InvalidGrantValidity : UpdateRequestError(
        "The requested grant validity period has ended or ends before it starts.",
    )

    class UnsupportedRequestedOf : UpdateRequestError(
        "Only AuthorizationRequests requested of a person can currently be approved or rejected.",
    )
}
