package no.elhub.auth.features.documents.create.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.MoveInAndChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.common.AuthorizationMetadataKeys
import no.elhub.auth.features.filegenerator.SupportedLanguage

class DocumentMetaMarkerTest : FunSpec({

    test("maps change-of-supplier document metadata") {
        val documentMeta: DocumentMetaMarker = ChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.DEFAULT,
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            requestedForMeteringPointId = "Meter123",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Address 1",
            requestedFromName = "Requester",
        )

        documentMeta.toMetaAttributes() shouldBe mapOf(
            AuthorizationMetadataKeys.LANGUAGE to JsonPrimitive(SupportedLanguage.DEFAULT.code),
            AuthorizationMetadataKeys.BALANCE_SUPPLIER_NAME to JsonPrimitive("Balance Supplier"),
            AuthorizationMetadataKeys.BALANCE_SUPPLIER_CONTRACT_NAME to JsonPrimitive("Contract Name"),
            AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ID to JsonPrimitive("Meter123"),
            AuthorizationMetadataKeys.REQUESTED_FOR_METER_NUMBER to JsonPrimitive("123456789"),
            AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ADDRESS to JsonPrimitive("Address 1"),
            AuthorizationMetadataKeys.REQUESTED_FROM_NAME to JsonPrimitive("Requester"),
        )
    }

    test("excludes request-only redirect URI from change-of-supplier document metadata") {
        val documentMeta: DocumentMetaMarker = ChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.DEFAULT,
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            requestedForMeteringPointId = "Meter123",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Address 1",
            requestedFromName = "Requester",
            redirectURI = "https://example.com/callback",
        )

        documentMeta.toMetaAttributes() shouldNotContainKey AuthorizationMetadataKeys.REDIRECT_URI
    }

    test("excludes request-only redirect URI from move-in document metadata") {
        val documentMeta: DocumentMetaMarker = MoveInAndChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.DEFAULT,
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            requestedForMeteringPointId = "Meter123",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Address 1",
            requestedFromName = "Requester",
            moveInDate = LocalDate(2026, 9, 15),
            redirectURI = "https://example.com/callback",
        )

        documentMeta.toMetaAttributes() shouldNotContainKey AuthorizationMetadataKeys.REDIRECT_URI
    }
})
