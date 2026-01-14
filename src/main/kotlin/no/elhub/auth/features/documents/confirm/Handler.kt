package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val documentRepository: DocumentRepository,
    private val partyService: PartyService,
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(command: Command): Either<ConfirmDocumentError, Unit> = either {
        val document = documentRepository.find(command.documentId)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> ConfirmDocumentError.DocumentNotFoundError
                    is RepositoryReadError.UnexpectedError -> ConfirmDocumentError.DocumentReadError
                }
            }.bind()

        val requestedBy = partyService.resolve(command.requestedByIdentifier)
            .mapLeft { ConfirmDocumentError.RequestedByResolutionError }
            .bind()

        ensure(requestedBy == document.requestedBy) {
            ConfirmDocumentError.InvalidRequestedByError
        }

        ensure(document.status == AuthorizationDocument.Status.Pending) {
            ConfirmDocumentError.IllegalStateError
        }

        // TODO: Implement validation of the signed file and find the signatory
        val signatory = document.requestedTo

        val confirmedDocument = documentRepository.confirm(
            documentId = document.id,
            signedFile = command.signedFile,
            requestedFrom = document.requestedFrom,
            signatory = document.requestedTo
        )
            .mapLeft { error ->
                when (error) {
                    is RepositoryWriteError.NotFoundError -> ConfirmDocumentError.DocumentNotFoundError

                    is RepositoryWriteError.ConflictError,
                    is RepositoryWriteError.UnexpectedError -> ConfirmDocumentError.DocumentUpdateError
                }
            }.bind()

        val scopeIds = documentRepository.findScopeIds(confirmedDocument.id)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> ConfirmDocumentError.DocumentNotFoundError
                    is RepositoryReadError.UnexpectedError -> ConfirmDocumentError.ScopeReadError
                }
            }.bind()

        val grantToCreate =
            AuthorizationGrant.create(
                grantedFor = confirmedDocument.requestedFrom,
                grantedBy = signatory,
                grantedTo = confirmedDocument.requestedBy,
                sourceType = AuthorizationGrant.SourceType.Document,
                sourceId = confirmedDocument.id
            )

        grantRepository.insert(grantToCreate, scopeIds)
            .mapLeft { ConfirmDocumentError.GrantCreationError }.bind()
    }
}

sealed class ConfirmDocumentError {
    data object DocumentNotFoundError : ConfirmDocumentError()
    data object DocumentReadError : ConfirmDocumentError()
    data object DocumentUpdateError : ConfirmDocumentError()
    data object ScopeReadError : ConfirmDocumentError()
    data object GrantCreationError : ConfirmDocumentError()
    data object RequestedByResolutionError : ConfirmDocumentError()
    data object InvalidRequestedByError : ConfirmDocumentError()
    data object IllegalStateError : ConfirmDocumentError()
}
