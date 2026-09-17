package no.elhub.auth.features.documents.create.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.jsonObjectOf

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

        meta.toMetaAttributes() shouldBe jsonObjectOf(
            "language" to SupportedLanguage.DEFAULT.code,
            "balanceSupplierName" to "Balance Supplier",
            "balanceSupplierContractName" to "Contract Name",
            "requestedForMeteringPointId" to "Meter123",
            "requestedForMeterNumber" to "123456789",
            "requestedForMeteringPointAddress" to "Address 1",
            "requestedFromName" to "Requester",
        )
    }
})
