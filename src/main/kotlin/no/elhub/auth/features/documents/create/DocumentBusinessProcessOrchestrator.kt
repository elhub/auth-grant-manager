package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.changeofsupplier.toChangeOfSupplierBusinessModel
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.toDocumentCommand
import no.elhub.auth.features.documents.create.model.CreateDocumentModel

class DocumentBusinessProcessOrchestrator(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
    private val fileGenerator: FileGenerator,
) {
    fun handle(
        type: AuthorizationDocument.Type,
        model: CreateDocumentModel,
    ): Either<CreateDocumentError, DocumentBusinessResult> =
        when (type) {
            AuthorizationDocument.Type.ChangeOfSupplierConfirmation -> processChangeOfSupplier(model)
        }

    private fun processChangeOfSupplier(model: CreateDocumentModel): Either<CreateDocumentError, DocumentBusinessResult> {
        val businessCommand =
            changeOfSupplierHandler.handle(model.toChangeOfSupplierBusinessModel()).getOrElse {
                return CreateDocumentError.MappingError.left()
            }

        val command: DocumentCommand = businessCommand.toDocumentCommand()

        val file =
            fileGenerator
                .generate(
                    signerNin = command.requestedTo.idValue,
                    documentMeta = command.meta,
                ).getOrElse {
                    return CreateDocumentError.FileGenerationError.left()
                }

        return DocumentBusinessResult(command = command, file = file).right()
    }
}

data class DocumentBusinessResult(
    val command: DocumentCommand,
    val file: ByteArray,
)
