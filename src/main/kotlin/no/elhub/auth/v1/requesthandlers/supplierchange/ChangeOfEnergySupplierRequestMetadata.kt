package no.elhub.auth.v1.requesthandlers.supplierchange

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v1.common.meteringpoints.RequestedMeteringPointMetadata
import no.elhub.auth.v1.common.meteringpoints.toJsonArray

data class ChangeOfEnergySupplierRequestMetadata(
    val requestedMeteringPoints: List<RequestedMeteringPointMetadata>,
    val balanceSupplierName: String,
    val organizationName: String,
)

fun ChangeOfEnergySupplierRequestMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("requestedMeteringPoints", requestedMeteringPoints.toJsonArray())
    put("balanceSupplierName", balanceSupplierName)
    put("organizationName", organizationName)
}
