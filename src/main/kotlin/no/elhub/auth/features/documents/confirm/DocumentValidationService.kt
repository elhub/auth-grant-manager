package no.elhub.auth.features.documents.confirm

import arrow.core.Either
import no.elhub.auth.features.documents.AuthorizationDocument
import org.apache.commons.lang3.NotImplementedException

interface DocumentValidationService {
    fun validate(pdfBytes: ByteArray)
}

// TODO: Implement validation of PDF
fun ByteArray.hasValidSignature(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByUs(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByEndUser(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.isSignedByDesiredSignatory(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

fun ByteArray.matchesDocumentRequest(document: AuthorizationDocument): Either<DocumentValidationError, Boolean> =
    throw NotImplementedException()

fun ByteArray.getEndUserNin(): Either<DocumentValidationError, String> = throw NotImplementedException()

fun ByteArray.isConformant(): Either<DocumentValidationError, Boolean> = throw NotImplementedException()

sealed class DocumentValidationError {
    data object InvalidSignatureError : DocumentValidationError()
}
