package no.elhub.auth.v0.features.documents.common

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.common.documents.pdf.PdfSignatory
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.common.documents.pdf.PdfSigningException
import no.elhub.auth.common.documents.pdf.PdfValidationException
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType

class V0SignatureServiceAdapterTest : FunSpec({
    test("maps a successful PDF signature to a v0 result") {
        val expected = byteArrayOf(1, 2, 3)
        val adapter = V0SignatureServiceAdapter(
            pdfSigner = object : PdfSigner {
                override suspend fun sign(fileByteArray: ByteArray) = expected
            },
            pdfSignatureValidator = unusedValidator(),
        )

        adapter.sign(byteArrayOf(4)).shouldBeRight() shouldBe expected
    }

    test("maps all PDF signing exceptions to v0 errors") {
        val cases = listOf(
            PdfSigningException.SigningDataGenerationError to SignatureSigningError.SigningDataGenerationError,
            PdfSigningException.AddSignatureToSignatureError to SignatureSigningError.AddSignatureToSignatureError,
            PdfSigningException.SignatureFetchingError to SignatureSigningError.SignatureFetchingError,
        )

        cases.forEach { (exception, expected) ->
            val adapter = V0SignatureServiceAdapter(
                pdfSigner = throwingSigner(exception),
                pdfSignatureValidator = unusedValidator(),
            )

            adapter.sign(byteArrayOf()).shouldBeLeft() shouldBe expected
        }
    }

    test("maps a successful PDF signatory to a v0 party identifier") {
        val adapter = V0SignatureServiceAdapter(
            pdfSigner = unusedSigner(),
            pdfSignatureValidator = object : PdfSignatureValidator {
                override fun validateSignaturesAndReturnSignatory(file: ByteArray, originalFile: ByteArray) =
                    PdfSignatory("01827535970")
            },
        )

        adapter.validateSignaturesAndReturnSignatory(byteArrayOf(1), byteArrayOf(2)).shouldBeRight() shouldBe
            PartyIdentifier(
                idType = PartyIdentifierType.NationalIdentityNumber,
                idValue = "01827535970",
            )
    }

    test("maps all PDF validation exceptions to v0 errors") {
        val cases = listOf(
            PdfValidationException.MissingElhubSignature to SignatureValidationError.MissingElhubSignature,
            PdfValidationException.InvalidElhubSignature to SignatureValidationError.InvalidElhubSignature,
            PdfValidationException.ElhubSigningCertNotTrusted to SignatureValidationError.ElhubSigningCertNotTrusted,
            PdfValidationException.ElhubSignatureModifiedAfterSigning to SignatureValidationError.ElhubSignatureModifiedAfterSigning,
            PdfValidationException.MissingBankIdSignature to SignatureValidationError.MissingBankIdSignature,
            PdfValidationException.InvalidBankIdSignature to SignatureValidationError.InvalidBankIdSignature,
            PdfValidationException.MissingTrustedTimestamp to SignatureValidationError.MissingBankIdTrustedTimestamp,
            PdfValidationException.BankIdSigningCertNotValidAtTimestamp to SignatureValidationError.BankIdSigningCertNotValidAtTimestamp,
            PdfValidationException.BankIdSigningCertNotFromExpectedRoot to SignatureValidationError.BankIdSigningCertNotFromExpectedRoot,
            PdfValidationException.BankIdCertificateRevoked to SignatureValidationError.BankIdCertificateRevoked,
            PdfValidationException.BankIdSignatureNotPadesLT to SignatureValidationError.BankIdSignatureNotPadesLT,
            PdfValidationException.MissingNationalId to SignatureValidationError.MissingNationalId,
            PdfValidationException.OriginalDocumentMismatch to SignatureValidationError.OriginalDocumentMismatch,
        )

        cases.forEach { (exception, expected) ->
            val adapter = V0SignatureServiceAdapter(
                pdfSigner = unusedSigner(),
                pdfSignatureValidator = throwingValidator(exception),
            )

            adapter.validateSignaturesAndReturnSignatory(byteArrayOf(), byteArrayOf()).shouldBeLeft() shouldBe expected
        }
    }
})

private fun unusedSigner() = object : PdfSigner {
    override suspend fun sign(fileByteArray: ByteArray): ByteArray = error("not used")
}

private fun throwingSigner(exception: PdfSigningException) = object : PdfSigner {
    override suspend fun sign(fileByteArray: ByteArray): ByteArray = throw exception
}

private fun unusedValidator() = object : PdfSignatureValidator {
    override fun validateSignaturesAndReturnSignatory(file: ByteArray, originalFile: ByteArray): PdfSignatory =
        error("not used")
}

private fun throwingValidator(exception: PdfValidationException) = object : PdfSignatureValidator {
    override fun validateSignaturesAndReturnSignatory(file: ByteArray, originalFile: ByteArray): PdfSignatory =
        throw exception
}
