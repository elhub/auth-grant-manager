package no.elhub.auth.grantmanager.domain.services

import no.elhub.auth.grantmanager.domain.models.ChangeSupplierRequest
import no.elhub.auth.grantmanager.domain.models.SignableDocument

interface DocumentGenerator {
    suspend fun generate(request: ChangeSupplierRequest): SignableDocument
}
