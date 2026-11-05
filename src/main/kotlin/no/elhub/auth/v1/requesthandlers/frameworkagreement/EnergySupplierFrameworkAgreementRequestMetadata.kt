package no.elhub.auth.v1.requesthandlers.frameworkagreement

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class EnergySupplierFrameworkAgreementRequestMetadata(
    val balanceSupplierName: String,
    val organizationName: String,
)
fun EnergySupplierFrameworkAgreementRequestMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("balanceSupplierName", balanceSupplierName)
    put("organizationName", organizationName)
}
