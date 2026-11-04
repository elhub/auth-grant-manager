package no.elhub.auth.v1.features.grants

import no.elhub.auth.v1.Errors

sealed class GetGrantError : Errors() {
    class NotAuthorized : GetGrantError()
}
