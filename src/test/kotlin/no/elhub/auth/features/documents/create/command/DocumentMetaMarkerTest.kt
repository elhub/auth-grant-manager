package no.elhub.auth.features.documents.create.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.ChangeOfEnergySupplierBusinessMeta

class DocumentMetaMarkerTest : FunSpec({

    test("ChangeOfEnergySupplierBusinessMeta produces the expected attribute map") {
        val meta = ChangeOfEnergySupplierBusinessMeta(
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            requestedForMeteringPointId = "Meter123",
            requestedForMeteringPointAddress = "Address 1",
            requestedFromName = "Requester",
        )

        meta.toMetaAttributes() shouldBe mapOf(
            "balanceSupplierName" to "Balance Supplier",
            "balanceSupplierContractName" to "Contract Name",
            "requestedForMeteringPointId" to "Meter123",
            "requestedForMeteringPointAddress" to "Address 1",
            "requestedFromName" to "Requester",
        )
    }
})
