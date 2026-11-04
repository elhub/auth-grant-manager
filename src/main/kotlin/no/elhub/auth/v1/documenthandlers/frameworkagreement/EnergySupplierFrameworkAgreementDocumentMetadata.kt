package no.elhub.auth.v1.documenthandlers.frameworkagreement

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class EnergySupplierFrameworkAgreementDocumentMetadata(
    val balanceSupplierName: String
)
fun EnergySupplierFrameworkAgreementDocumentMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("balanceSupplierName", balanceSupplierName)
}
