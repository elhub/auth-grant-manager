package no.elhub.auth.v1.features.requests

import no.elhub.auth.v1.Errors

sealed class GetRequestError : Errors() {
    class NotAuthorized : GetRequestError()
}
