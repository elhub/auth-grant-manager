package no.elhub.auth.grantmanager.domain.services

import no.elhub.auth.grantmanager.domain.models.ChangeSupplierRequest
import no.elhub.auth.grantmanager.domain.models.SignableDocument

interface SignedDocumentGenerator {
    suspend fun generateAndSign(request: ChangeSupplierRequest): SignableDocument
}
