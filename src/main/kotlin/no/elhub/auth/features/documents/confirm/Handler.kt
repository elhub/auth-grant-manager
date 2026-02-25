package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.SignatureService
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.collections.component1
import kotlin.collections.component2

class Handler(
    private val businessHandler: DocumentBusinessHandler,
    private val documentRepository: DocumentRepository,
    private val grantRepository: GrantRepository,
    private val partyService: PartyService,
    private val signatureService: SignatureService,
    private val grantPropertiesRepository: GrantPropertiesRepository
) {

    private val log = LoggerFactory.getLogger(Handler::class.java)

    suspend operator fun invoke(command: Command): Either<ConfirmError, Unit> = either {
        val authorizationParty = command.authorizedParty

        val document = transaction {
            documentRepository.find(command.documentId)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> ConfirmError.DocumentNotFoundError
                        is RepositoryReadError.UnexpectedError -> ConfirmError.DocumentReadError
                    }
                }.bind()
        }

        ensure(authorizationParty == document.requestedBy) {
            ConfirmError.InvalidRequestedByError
        }

        ensure(document.status == AuthorizationDocument.Status.Pending) {
            ConfirmError.IllegalStateError
        }

        ensure(document.validTo >= OffsetDateTime.now(ZoneOffset.UTC)) {
            ConfirmError.ExpiredError
        }

        val signatoryIdentifier = signatureService.validateSignaturesAndReturnSignatory(command.signedFile, document.file)
            .mapLeft {
                log.error("Validate signature error occurred: {}", it)
                ConfirmError.ValidateSignaturesError(it)
            }
            .bind()

        val actualSignatoryParty = partyService.resolve(signatoryIdentifier)
            .mapLeft { ConfirmError.SignatoryResolutionError }
            .bind()

        val expectedSignatoryParty = document.requestedTo
        ensure(actualSignatoryParty == expectedSignatoryParty) {
            ConfirmError.SignatoryNotAllowedToSignDocument
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
                        is RepositoryWriteError.NotFoundError -> ConfirmError.DocumentNotFoundError

                        is RepositoryWriteError.ConflictError,
                        is RepositoryWriteError.UnexpectedError -> ConfirmError.DocumentUpdateError
                    }
                }.bind()

            val scopeIds = documentRepository.findScopeIds(command.documentId)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> ConfirmError.DocumentNotFoundError
                        is RepositoryReadError.UnexpectedError -> ConfirmError.ScopeReadError
                    }
                }.bind()

            val grantToCreate =
                AuthorizationGrant.create(
                    grantedFor = confirmedDocument.requestedFrom,
                    grantedBy = expectedSignatoryParty,
                    grantedTo = confirmedDocument.requestedBy,
                    sourceType = AuthorizationGrant.SourceType.Document,
                    sourceId = confirmedDocument.id,
                    scopeIds = scopeIds
                )

            val createdGrant = grantRepository.insert(grantToCreate, scopeIds)
                .mapLeft { ConfirmError.GrantCreationError }.bind()

            val createGrantProperties = businessHandler.getCreateGrantProperties(confirmedDocument)
            val grantMetaProperties = createGrantProperties.meta.map { (key, value) ->
                AuthorizationGrantProperty(
                    grantId = createdGrant.id,
                    key = key,
                    value = value
                )
            }

            grantPropertiesRepository.insert(grantMetaProperties)
        }
    }
}
