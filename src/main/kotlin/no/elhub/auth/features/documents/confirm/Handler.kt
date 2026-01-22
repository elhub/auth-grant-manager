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
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class Handler(
    private val documentRepository: DocumentRepository,
    private val partyService: PartyService,
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(command: Command): Either<ConfirmError, Unit> = either {
        val requestedBy = partyService.resolve(command.requestedByIdentifier)
            .mapLeft { ConfirmError.RequestedByResolutionError }
            .bind()

        transaction {
            val document = documentRepository.find(command.documentId)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> ConfirmError.DocumentNotFoundError
                        is RepositoryReadError.UnexpectedError -> ConfirmError.DocumentReadError
                    }
                }.bind()

            ensure(requestedBy == document.requestedBy) {
                ConfirmError.InvalidRequestedByError
            }

            ensure(document.status == AuthorizationDocument.Status.Pending) {
                ConfirmError.IllegalStateError
            }

            ensure(document.validTo >= OffsetDateTime.now(ZoneOffset.UTC)) {
                ConfirmError.ExpiredError
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
                        is RepositoryWriteError.NotFoundError -> ConfirmError.DocumentNotFoundError

                        is RepositoryWriteError.ConflictError,
                        is RepositoryWriteError.UnexpectedError -> ConfirmError.DocumentUpdateError
                    }
                }.bind()

            val scopeIds = documentRepository.findScopeIds(confirmedDocument.id)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> ConfirmError.DocumentNotFoundError
                        is RepositoryReadError.UnexpectedError -> ConfirmError.ScopeReadError
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
                .mapLeft { ConfirmError.GrantCreationError }.bind()
        }
    }
}
