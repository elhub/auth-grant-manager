package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ProxyDocumentBusinessHandler
import no.elhub.auth.features.documents.create.model.CreateDocumentModel

class Handler(
    private val businessHandler: ProxyDocumentBusinessHandler,
    private val signingService: SigningService,
    private val documentRepository: DocumentRepository,
    private val partyService: PartyService,
) {
    suspend operator fun invoke(model: CreateDocumentModel): Either<CreateDocumentError, AuthorizationDocument> {
        val command =
            businessHandler
                .validateAndReturnDocumentCommand(model)
                .getOrElse { return it.left() }

        val requestedFromParty =
            partyService
                .resolve(command.requestedFrom)
                .getOrElse { return CreateDocumentError.RequestedFromPartyError.left() }

        val requestedByParty =
            partyService
                .resolve(command.requestedBy)
                .getOrElse { return CreateDocumentError.RequestedByPartyError.left() }

        val requestedToParty =
            partyService
                .resolve(command.requestedTo)
                .getOrElse { return CreateDocumentError.RequestedToPartyError.left() }

        val file =
            businessHandler
                .generateFile(command.requestedFrom.idValue, command.meta)
                .getOrElse { return CreateDocumentError.FileGenerationError.left() }

        val signedFile = signingService.sign(file)
            .getOrElse { return CreateDocumentError.SignFileError(cause = it).left() }

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
            )

        val savedDocument =
            documentRepository
                .insert(documentToCreate, command.scopes)
                .getOrElse { return CreateDocumentError.PersistenceError.left() }

        return savedDocument.right()
    }
}

sealed class CreateDocumentError {
    data object FileGenerationError : CreateDocumentError()

    data class SignFileError(val cause: FileSigningError) : CreateDocumentError()

    data object PersistenceError : CreateDocumentError()

    data object RequestedFromPartyError : CreateDocumentError()

    data object RequestedByPartyError : CreateDocumentError()

    data object RequestedToPartyError : CreateDocumentError()

    // To be used by value streams in during the business validation process. Auth Grant will return this message back to the API consumer
    data class BusinessValidationError(val message: String) : CreateDocumentError()
}

fun Map<String, String>.toDocumentProperties() =
    this
        .map { (key, value) ->
            AuthorizationDocumentProperty(
                key = key,
                value = value,
            )
        }.toList()
