package no.elhub.auth.common.documents.pdf

import kotlinx.datetime.LocalDate

enum class PdfLanguage(val code: String) {
    NB("nb"),
    NN("nn"),
    EN("en");

    fun toPdfLanguage() = when (this) {
        NB -> "nb-NO"
        NN -> "nn-NO"
        EN -> "en-US"
    }

    companion object {
        val DEFAULT = NB
    }
}

sealed interface AuthorizationDocumentPdfContent {
    val language: PdfLanguage

    sealed class ChangeOfBalanceSupplier : AuthorizationDocumentPdfContent {
        abstract val meteringPointAddress: String
        abstract val meteringPointId: String
        abstract val meterNumber: String
        abstract val balanceSupplierName: String
    }

    sealed class MoveInAndChangeOfBalanceSupplier : ChangeOfBalanceSupplier() {
        abstract val moveInDate: LocalDate?
    }

    data class ChangeOfBalanceSupplierForPerson(
        override val language: PdfLanguage,
        val customerName: String,
        override val meteringPointAddress: String,
        override val meteringPointId: String,
        override val meterNumber: String,
        override val balanceSupplierName: String,
        val balanceSupplierContractName: String,
    ) : ChangeOfBalanceSupplier()

    data class MoveInAndChangeOfBalanceSupplierForPerson(
        override val language: PdfLanguage,
        val customerName: String,
        override val meteringPointAddress: String,
        override val meteringPointId: String,
        override val meterNumber: String,
        override val balanceSupplierName: String,
        val balanceSupplierContractName: String,
        override val moveInDate: LocalDate?,
    ) : MoveInAndChangeOfBalanceSupplier()

    data class ChangeOfBalanceSupplierForOrganisation(
        override val language: PdfLanguage,
        val organizationName: String,
        val organizationNumber: String,
        override val meteringPointAddress: String,
        override val meteringPointId: String,
        override val meterNumber: String,
        override val balanceSupplierName: String,
        val agreementReference: String?,
    ) : ChangeOfBalanceSupplier()

    data class MoveInAndChangeOfBalanceSupplierForOrganisation(
        override val language: PdfLanguage,
        val organizationName: String,
        val organizationNumber: String,
        override val meteringPointAddress: String,
        override val meteringPointId: String,
        override val meterNumber: String,
        override val balanceSupplierName: String,
        val agreementReference: String?,
        override val moveInDate: LocalDate?,
    ) : MoveInAndChangeOfBalanceSupplier()

    data class FrameworkAgreement(
        override val language: PdfLanguage,
        val organizationName: String,
        val organizationNumber: String,
        val balanceSupplierName: String,
        val agreementReference: String,
        val startDate: LocalDate,
        val endDate: LocalDate?,
    ) : AuthorizationDocumentPdfContent
}

interface PdfGenerator {
    fun generate(content: AuthorizationDocumentPdfContent): ByteArray
}

interface PdfSigner {
    suspend fun sign(fileByteArray: ByteArray): ByteArray
}

interface PdfSignatureValidator {
    fun validateSignaturesAndReturnSignatory(file: ByteArray, originalFile: ByteArray): PdfSignatory
}

data class PdfSignatory(val nationalIdentityNumber: String)

class PdfGenerationException(cause: Throwable) : RuntimeException("Failed to generate PDF", cause)

sealed class PdfSigningException(message: String) : RuntimeException(message) {
    data object SigningDataGenerationError : PdfSigningException("Failed to prepare PDF signing data")
    data object AddSignatureToSignatureError : PdfSigningException("Failed to add signature to PDF")
    data object SignatureFetchingError : PdfSigningException("Failed to fetch PDF signature")
}

sealed class PdfValidationException(message: String) : RuntimeException(message) {
    data object MissingElhubSignature : PdfValidationException("Missing Elhub signature")
    data object InvalidElhubSignature : PdfValidationException("Invalid Elhub signature")
    data object ElhubSigningCertNotTrusted : PdfValidationException("Elhub signing certificate is not trusted")
    data object ElhubSignatureModifiedAfterSigning : PdfValidationException("PDF was modified after Elhub signed it")
    data object MissingBankIdSignature : PdfValidationException("Missing BankID signature")
    data object InvalidBankIdSignature : PdfValidationException("Invalid BankID signature")
    data object MissingTrustedTimestamp : PdfValidationException("Missing trusted timestamp")
    data object BankIdSigningCertNotValidAtTimestamp : PdfValidationException("BankID signing certificate was not valid at signing time")
    data object BankIdSigningCertNotFromExpectedRoot : PdfValidationException("BankID signing certificate is not trusted")
    data object BankIdCertificateRevoked : PdfValidationException("BankID certificate was revoked")
    data object BankIdSignatureNotPadesLT : PdfValidationException("BankID signature is not PAdES-LT")
    data object MissingNationalId : PdfValidationException("Missing national identity number")
    data object OriginalDocumentMismatch : PdfValidationException("Signed PDF differs from the original PDF")
}
