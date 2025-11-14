package no.elhub.auth.features.documents.create.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DocumentMetaMarkerTest : FunSpec({

    test("ChangeOfSupplierDocumentMeta produces the expected attribute map") {
        val meta = ChangeOfSupplierDocumentMeta(
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            meteringPointId = "Meter123",
            meteringPointAddress = "Address 1",
            requestedFromName = "Requester"
        )

        meta.toMetaAttributes() shouldBe mapOf(
            "balanceSupplierName" to "Balance Supplier",
            "balanceSupplierContractName" to "Contract Name",
            "meteringPointId" to "Meter123",
            "meteringPointAddress" to "Address 1",
            "requestedFromName" to "Requester"
        )
    }
})
