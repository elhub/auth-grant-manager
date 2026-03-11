package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.common.party.PartyIdentifier

interface SignatureService {
    suspend fun sign(fileByteArray: ByteArray): Either<SignatureSigningError, ByteArray>
    fun validateSignaturesAndReturnSignatory(
        file: ByteArray,
        originalFile: ByteArray
    ): Either<SignatureValidationError, PartyIdentifier>
}

sealed class SignatureSigningError {
    data object SigningDataGenerationError : SignatureSigningError()
    data object AddSignatureToSignatureError : SignatureSigningError()
    data object SignatureFetchingError : SignatureSigningError()
}

sealed class SignatureValidationError {
    data object MissingElhubSignature : SignatureValidationError()
    data object InvalidElhubSignature : SignatureValidationError()
    data object ElhubSigningCertNotTrusted : SignatureValidationError()
    data object ElhubSignatureModifiedAfterSigning : SignatureValidationError()
    data object MissingBankIdSignature : SignatureValidationError()
    data object InvalidBankIdSignature : SignatureValidationError()
    data object MissingBankIdTrustedTimestamp : SignatureValidationError()
    data object BankIdSigningCertNotValidAtTimestamp : SignatureValidationError()
    data object BankIdSigningCertNotFromExpectedRoot : SignatureValidationError()
    data object BankIdCertificateRevoked : SignatureValidationError()
    data object BankIdSignatureNotPadesLT : SignatureValidationError()
    data object MissingNationalId : SignatureValidationError()
    data object OriginalDocumentMismatch : SignatureValidationError()
}
