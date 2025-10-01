package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import org.apache.commons.lang3.NotImplementedException

fun interface DocumentValidationService {
    fun validate(pdfBytes: ByteArray)
}

fun ByteArray.hasValidSignature(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByUs(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByEndUser(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByDesiredSignatory(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.matchesDocumentRequest(): Either<DocumentValidationError, Boolean> =
    throw NotImplementedException()

fun ByteArray.getEndUserNin(): Either<DocumentValidationError, String> = throw NotImplementedException()

sealed class DocumentValidationError {
    data object InvalidSignatureError : DocumentValidationError()
}
