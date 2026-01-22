package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.SignatureService
import no.elhub.auth.features.documents.common.SignatureValidationError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class Handler(
    private val documentRepository: DocumentRepository,
    private val grantRepository: GrantRepository,
    private val partyService: PartyService,
    private val signatureService: SignatureService
) {
    suspend operator fun invoke(command: Command): Either<ConfirmDocumentError, Unit> = either {
        val authorizationParty = command.authorizedParty

        val document = transaction {
            documentRepository.find(command.documentId)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> ConfirmDocumentError.DocumentNotFoundError
                        is RepositoryReadError.UnexpectedError -> ConfirmDocumentError.DocumentReadError
                    }
                }.bind()
        }

        ensure(authorizationParty == document.requestedBy) {
            ConfirmDocumentError.InvalidRequestedByError
        }
        ensure(command.authorizedParty == document.requestedBy) {
            ConfirmDocumentError.InvalidRequestedByError
        }

        ensure(document.status == AuthorizationDocument.Status.Pending) {
            ConfirmDocumentError.IllegalStateError
        }

        ensure(document.validTo >= OffsetDateTime.now(ZoneOffset.UTC)) {
            ConfirmDocumentError.ExpiredError
        }

        val signatoryIdentifier = signatureService.validateSignaturesAndReturnSignatory(command.signedFile)
            .mapLeft { ConfirmDocumentError.ValidateSignaturesError(it) }
            .bind()

        val actualSignatoryParty = partyService.resolve(signatoryIdentifier)
            .mapLeft { ConfirmDocumentError.SignatoryResolutionError }
            .bind()

        val expectedSignatoryParty = document.requestedTo
        ensure(actualSignatoryParty == expectedSignatoryParty) {
            ConfirmDocumentError.SignatoryNotAllowedToSignDocument
        }

        transaction {
            val confirmedDocument = documentRepository.confirm(
                documentId = document.id,
                signedFile = command.signedFile,
                requestedFrom = document.requestedFrom,
                signatory = expectedSignatoryParty
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
                    grantedBy = expectedSignatoryParty,
                    grantedTo = confirmedDocument.requestedBy,
                    sourceType = AuthorizationGrant.SourceType.Document,
                    sourceId = confirmedDocument.id
                )

            grantRepository.insert(grantToCreate, scopeIds)
                .mapLeft { ConfirmDocumentError.GrantCreationError }.bind()
        }
    }
}

sealed class ConfirmDocumentError {
    data object DocumentNotFoundError : ConfirmDocumentError()
    data object DocumentReadError : ConfirmDocumentError()
    data object DocumentUpdateError : ConfirmDocumentError()
    data object ScopeReadError : ConfirmDocumentError()
    data object GrantCreationError : ConfirmDocumentError()
    data object SignatoryResolutionError : ConfirmDocumentError()
    data object InvalidRequestedByError : ConfirmDocumentError()
    data object IllegalStateError : ConfirmDocumentError()
    data object ExpiredError : ConfirmDocumentError()
    data object SignatoryNotAllowedToSignDocument : ConfirmDocumentError()
    data class ValidateSignaturesError(val cause: SignatureValidationError) : ConfirmDocumentError()
}
