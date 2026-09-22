package no.elhub.auth.v0.features.documents.create

import arrow.core.Either
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.documents.pdf.PdfGenerationException
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.v0.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.v0.features.businessprocesses.moveinandchangeofbalancesupplier.domain.MoveInAndChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.v0.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.v0.features.filegenerator.SupportedLanguage

interface FileGenerator {
    fun generate(
        documentMeta: DocumentMetaMarker,
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray>
}

class V0FileGeneratorAdapter(private val pdfGenerator: PdfGenerator) : FileGenerator {
    override fun generate(documentMeta: DocumentMetaMarker): Either<DocumentGenerationError.ContentGenerationError, ByteArray> =
        Either.catch { pdfGenerator.generate(documentMeta.toPdfContent()) }
            .mapLeft { DocumentGenerationError.ContentGenerationError }
}

private fun DocumentMetaMarker.toPdfContent(): AuthorizationDocumentPdfContent =
    when (this) {
        is ChangeOfBalanceSupplierBusinessMeta -> AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier(
            language = (language ?: SupportedLanguage.DEFAULT).toCommonPdfLanguage(),
            customerName = requestedFromName,
            meteringPointAddress = requestedForMeteringPointAddress,
            meteringPointId = requestedForMeteringPointId,
            meterNumber = requestedForMeterNumber,
            balanceSupplierName = balanceSupplierName,
            balanceSupplierContractName = balanceSupplierContractName,
        )

        is MoveInAndChangeOfBalanceSupplierBusinessMeta -> AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier(
            language = (language ?: SupportedLanguage.DEFAULT).toCommonPdfLanguage(),
            customerName = requestedFromName,
            meteringPointAddress = requestedForMeteringPointAddress,
            meteringPointId = requestedForMeteringPointId,
            meterNumber = requestedForMeterNumber,
            balanceSupplierName = balanceSupplierName,
            balanceSupplierContractName = balanceSupplierContractName,
            moveInDate = moveInDate,
        )

        else -> throw PdfGenerationException(IllegalArgumentException("Unsupported authorization document metadata"))
    }

class DocumentGenerationError {
    data object ContentGenerationError
}
