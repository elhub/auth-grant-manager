package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.ConfirmWithGrantError
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.SignatureService
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import org.slf4j.LoggerFactory

class Handler(
    private val businessHandler: DocumentBusinessHandler,
    private val documentRepository: DocumentRepository,
    private val partyService: PartyService,
    private val signatureService: SignatureService,
) {

    private val log = LoggerFactory.getLogger(Handler::class.java)

    suspend operator fun invoke(command: Command): Either<ConfirmError, Unit> = either {
        val authorizationParty = command.authorizedParty

        val document = documentRepository.find(command.documentId)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> ConfirmError.DocumentNotFoundError
                    is RepositoryReadError.UnexpectedError -> ConfirmError.DocumentReadError
                }
            }.bind()

        ensure(authorizationParty == document.requestedBy) {
            ConfirmError.InvalidRequestedByError
        }

        ensure(document.status == AuthorizationDocument.Status.Pending) {
            ConfirmError.IllegalStateError("AuthorizationDocument must be in 'Pending' status to confirm.")
        }

        ensure(document.validTo >= currentTimeUtc()) {
            ConfirmError.ExpiredError
        }

        val signatoryIdentifier =
            signatureService.validateSignaturesAndReturnSignatory(command.signedFile, document.file)
                .mapLeft {
                    log.info("event=authorization_document_confirm_validation_error error=$it")
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

        val scopeIds = documentRepository.findScopeIds(command.documentId)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> ConfirmError.DocumentNotFoundError
                    is RepositoryReadError.UnexpectedError -> ConfirmError.ScopeReadError
                }
            }.bind()

        val grantProperties = businessHandler.getCreateGrantProperties(document)

        val grantToCreate = AuthorizationGrant.create(
            grantedFor = document.requestedFrom,
            grantedBy = expectedSignatoryParty,
            grantedTo = document.requestedBy,
            sourceType = AuthorizationGrant.SourceType.Document,
            sourceId = document.id,
            scopeIds = scopeIds,
            validFrom = grantProperties.validFrom.toTimeZoneOffsetDateTimeAtStartOfDay(),
            validTo = grantProperties.validTo.toTimeZoneOffsetDateTimeAtStartOfDay()
        )

        val grantMetaProperties = grantProperties.meta.map { (key, value) ->
            AuthorizationGrantProperty(
                grantId = grantToCreate.id,
                key = key,
                value = value
            )
        }

        val confirmedDocument = documentRepository.confirmWithGrant(
            documentId = document.id,
            signedFile = command.signedFile,
            signatory = expectedSignatoryParty,
            grant = grantToCreate,
            grantProperties = grantMetaProperties
        ).mapLeft { error ->
            when (error) {
                ConfirmWithGrantError.DocumentError.NotFound -> ConfirmError.DocumentNotFoundError

                ConfirmWithGrantError.DocumentError.Conflict,
                ConfirmWithGrantError.DocumentError.Unexpected -> ConfirmError.DocumentUpdateError

                ConfirmWithGrantError.GrantError -> ConfirmError.GrantCreationError
            }
        }.bind()

        log.info(
            "event=authorization_grant_created grantId={} sourceType={} sourceId={}",
            grantToCreate.id,
            AuthorizationGrant.SourceType.Document,
            confirmedDocument.id
        )
    }
}
