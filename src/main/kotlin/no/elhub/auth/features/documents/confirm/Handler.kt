package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import java.time.LocalDateTime

class Handler(
    private val documentRepository: DocumentRepository,
    private val grantRepository: GrantRepository,
) {
    operator fun invoke(command: Command): Either<ConfirmDocumentError, ConfirmDocumentResult> = either {
        val document = documentRepository.find(command.documentId)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> ConfirmDocumentError.DocumentNotFound
                    is RepositoryReadError.UnexpectedError -> ConfirmDocumentError.DocumentReadError
                }
            }.bind()
        // TODO: Implement validation of the signed file
        val updatedDocument = document.copy(
            file = command.signedFile,
            status = AuthorizationDocument.Status.Signed,
            updatedAt = LocalDateTime.now()
        )
        documentRepository.update(updatedDocument)
            .mapLeft { error ->
                when (error) {
                    is RepositoryWriteError.NotFoundError -> ConfirmDocumentError.DocumentNotFound
                    is RepositoryWriteError.ConflictError,
                    is RepositoryWriteError.UnexpectedError -> ConfirmDocumentError.DocumentUpdateError
                }
            }.bind()

        val scopes = documentRepository.findScopes(command.documentId)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> ConfirmDocumentError.DocumentNotFound
                    is RepositoryReadError.UnexpectedError -> ConfirmDocumentError.ScopeReadError
                }
            }.bind()

        val grant = grantRepository.insert(
            grantedFor = updatedDocument.requestedFrom,
            grantedBy = updatedDocument.signedBy,
            grantedTo = updatedDocument.requestedTo,
            scopes = scopes
        ).mapLeft { ConfirmDocumentError.GrantCreationError }.bind()

        ConfirmDocumentResult(
            document = updatedDocument,
            grant = grant
        )
    }
}

data class ConfirmDocumentResult(
    val document: AuthorizationDocument,
    val grant: AuthorizationGrant
)

sealed class ConfirmDocumentError {
    data object DocumentNotFound : ConfirmDocumentError()
    data object DocumentReadError : ConfirmDocumentError()
    data object DocumentUpdateError : ConfirmDocumentError()
    data object ScopeReadError : ConfirmDocumentError()
    data object GrantCreationError : ConfirmDocumentError()
}
