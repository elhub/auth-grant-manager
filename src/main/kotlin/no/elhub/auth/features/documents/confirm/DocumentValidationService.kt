package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import no.elhub.auth.features.documents.AuthorizationDocument
import org.apache.commons.lang3.NotImplementedException

fun interface DocumentValidationService {
    fun validate(pdfBytes: ByteArray)
}

fun ByteArray.hasValidSignature(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByUs(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByPerson(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByDesiredSignatory(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.matchesDocumentRequest(document: AuthorizationDocument): Either<DocumentValidationError, Boolean> =
    throw NotImplementedException()

fun ByteArray.getPersonNin(): Either<DocumentValidationError, String> = throw NotImplementedException()

sealed class DocumentValidationError {
    data object InvalidSignatureError : DocumentValidationError()
}
