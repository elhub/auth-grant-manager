package no.elhub.auth.grantmanager.domain.services

import no.elhub.auth.grantmanager.domain.models.SignableDocument

interface DocumentSigningService {
    suspend fun sign(document: SignableDocument): SignableDocument
}
