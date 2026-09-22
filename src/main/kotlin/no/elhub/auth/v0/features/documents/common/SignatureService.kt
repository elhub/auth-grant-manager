package no.elhub.auth.v0.features.documents.common

import arrow.core.Either
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.common.documents.pdf.PdfSigningException
import no.elhub.auth.common.documents.pdf.PdfValidationException
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType

interface SignatureService {
    suspend fun sign(fileByteArray: ByteArray): Either<SignatureSigningError, ByteArray>
    fun validateSignaturesAndReturnSignatory(
        file: ByteArray,
        originalFile: ByteArray
    ): Either<SignatureValidationError, PartyIdentifier>
}

class V0SignatureServiceAdapter(
    private val pdfSigner: PdfSigner,
    private val pdfSignatureValidator: PdfSignatureValidator,
) : SignatureService {
    override suspend fun sign(fileByteArray: ByteArray): Either<SignatureSigningError, ByteArray> =
        try {
            Either.Right(pdfSigner.sign(fileByteArray))
        } catch (error: PdfSigningException) {
            Either.Left(error.toV0Error())
        }

    override fun validateSignaturesAndReturnSignatory(
        file: ByteArray,
        originalFile: ByteArray
    ): Either<SignatureValidationError, PartyIdentifier> =
        try {
            val signatory = pdfSignatureValidator.validateSignaturesAndReturnSignatory(file, originalFile)
            Either.Right(
                PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = signatory.nationalIdentityNumber
                )
            )
        } catch (error: PdfValidationException) {
            Either.Left(error.toV0Error())
        }
}

private fun PdfSigningException.toV0Error() = when (this) {
    PdfSigningException.SigningDataGenerationError -> SignatureSigningError.SigningDataGenerationError
    PdfSigningException.AddSignatureToSignatureError -> SignatureSigningError.AddSignatureToSignatureError
    PdfSigningException.SignatureFetchingError -> SignatureSigningError.SignatureFetchingError
}

private fun PdfValidationException.toV0Error() = when (this) {
    PdfValidationException.MissingElhubSignature -> SignatureValidationError.MissingElhubSignature
    PdfValidationException.InvalidElhubSignature -> SignatureValidationError.InvalidElhubSignature
    PdfValidationException.ElhubSigningCertNotTrusted -> SignatureValidationError.ElhubSigningCertNotTrusted
    PdfValidationException.ElhubSignatureModifiedAfterSigning -> SignatureValidationError.ElhubSignatureModifiedAfterSigning
    PdfValidationException.MissingBankIdSignature -> SignatureValidationError.MissingBankIdSignature
    PdfValidationException.InvalidBankIdSignature -> SignatureValidationError.InvalidBankIdSignature
    PdfValidationException.MissingTrustedTimestamp -> SignatureValidationError.MissingBankIdTrustedTimestamp
    PdfValidationException.BankIdSigningCertNotValidAtTimestamp -> SignatureValidationError.BankIdSigningCertNotValidAtTimestamp
    PdfValidationException.BankIdSigningCertNotFromExpectedRoot -> SignatureValidationError.BankIdSigningCertNotFromExpectedRoot
    PdfValidationException.BankIdCertificateRevoked -> SignatureValidationError.BankIdCertificateRevoked
    PdfValidationException.BankIdSignatureNotPadesLT -> SignatureValidationError.BankIdSignatureNotPadesLT
    PdfValidationException.MissingNationalId -> SignatureValidationError.MissingNationalId
    PdfValidationException.OriginalDocumentMismatch -> SignatureValidationError.OriginalDocumentMismatch
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
