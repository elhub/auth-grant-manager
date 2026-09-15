package no.elhub.auth.features.documents.create.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.filegenerator.SupportedLanguage

class DocumentMetaMarkerTest : FunSpec({

    test("ChangeOfBalanceSupplierBusinessMeta produces the expected attribute map") {
        val meta = ChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.DEFAULT,
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            requestedForMeteringPointId = "Meter123",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Address 1",
            requestedFromName = "Requester",
        )

        meta.toMetaAttributes() shouldBe mapOf(
            "language" to JsonPrimitive(SupportedLanguage.DEFAULT.code),
            "balanceSupplierName" to JsonPrimitive("Balance Supplier"),
            "balanceSupplierContractName" to JsonPrimitive("Contract Name"),
            "requestedForMeteringPointId" to JsonPrimitive("Meter123"),
            "requestedForMeterNumber" to JsonPrimitive("123456789"),
            "requestedForMeteringPointAddress" to JsonPrimitive("Address 1"),
            "requestedFromName" to JsonPrimitive("Requester"),
        )
    }
})
