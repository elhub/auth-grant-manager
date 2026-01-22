package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ProxyDocumentBusinessHandler
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import org.jetbrains.exposed.sql.transactions.transaction

class Handler(
    private val businessHandler: ProxyDocumentBusinessHandler,
    private val signingService: SigningService,
    private val documentRepository: DocumentRepository,
    private val partyService: PartyService,
) {
    suspend operator fun invoke(model: CreateDocumentModel): Either<CreateDocumentError, AuthorizationDocument> =
        either {
            val requestedByParty =
                partyService
                    .resolve(model.meta.requestedBy)
                    .mapLeft { CreateDocumentError.RequestedByPartyError }
                    .bind()

            ensure(model.authorizedParty == requestedByParty) {
                CreateDocumentError.AuthorizationError
            }

            val requestedFromParty =
                partyService
                    .resolve(model.meta.requestedFrom)
                    .mapLeft { CreateDocumentError.RequestedFromPartyError }
                    .bind()

            val requestedToParty =
                partyService
                    .resolve(model.meta.requestedTo)
                    .mapLeft { CreateDocumentError.RequestedToPartyError }
                    .bind()

            val command =
                businessHandler
                    .validateAndReturnDocumentCommand(model)
                    .bind()

            val file =
                businessHandler
                    .generateFile(command.requestedFrom.idValue, command.meta)
                    .mapLeft { CreateDocumentError.FileGenerationError }
                    .bind()

            val signedFile = signingService.sign(file)
                .mapLeft { CreateDocumentError.SignFileError(cause = it) }
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
                    .mapLeft { CreateDocumentError.PersistenceError }
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
