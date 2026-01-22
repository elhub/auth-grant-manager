package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ProxyDocumentBusinessHandler
import no.elhub.auth.features.documents.common.SignatureService
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import org.jetbrains.exposed.sql.transactions.transaction

class Handler(
    private val businessHandler: ProxyDocumentBusinessHandler,
    private val signatureService: SignatureService,
    private val documentRepository: DocumentRepository,
    private val partyService: PartyService,
) {
    suspend operator fun invoke(model: CreateDocumentModel): Either<CreateError, AuthorizationDocument> =
        either {
            val requestedByParty =
                partyService
                    .resolve(model.meta.requestedBy)
                    .mapLeft { CreateError.RequestedPartyError }
                    .bind()

            ensure(model.authorizedParty == requestedByParty) {
                CreateError.AuthorizationError
            }

            val requestedFromParty =
                partyService
                    .resolve(model.meta.requestedFrom)
                    .mapLeft { CreateError.RequestedPartyError }
                    .bind()

            val requestedToParty =
                partyService
                    .resolve(model.meta.requestedTo)
                    .mapLeft { CreateError.RequestedPartyError }
                    .bind()

            val command =
                businessHandler
                    .validateAndReturnDocumentCommand(model)
                    .bind()

            val file =
                businessHandler
                    .generateFile(command.requestedFrom.idValue, command.meta)
                    .mapLeft { CreateError.FileGenerationError }
                    .bind()

            val signedFile = signatureService.sign(file)
                .mapLeft { CreateError.SignFileError(cause = it) }
                .bind()

            val documentProperties =
                command.meta
                    .toMetaAttributes()
                    .toDocumentProperties()

            val documentToCreate =
                AuthorizationDocument.create(
                    type = command.type,
                    file = signedFile,
                    requestedBy = requestedByParty,
                    requestedFrom = requestedFromParty,
                    requestedTo = requestedToParty,
                    properties = documentProperties,
                    validTo = command.validTo,
                )

            val savedDocument = transaction {
                documentRepository
                    .insert(documentToCreate, command.scopes)
                    .mapLeft { CreateError.PersistenceError }
                    .bind()
            }

            savedDocument
        }
}

fun Map<String, String>.toDocumentProperties() =
    this
        .map { (key, value) ->
            AuthorizationDocumentProperty(
                key = key,
                value = value,
            )
        }.toList()
