package no.elhub.auth.v1.features.documents

import no.elhub.auth.v1.Errors

sealed class GetDocumentError : Errors() {
    class NotAuthorized : GetDocumentError()
}
