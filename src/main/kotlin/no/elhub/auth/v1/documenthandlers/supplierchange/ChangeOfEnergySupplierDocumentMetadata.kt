package no.elhub.auth.v1.documenthandlers.supplierchange

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v1.common.meteringpoints.RequestedMeteringPointMetadata
import no.elhub.auth.v1.common.meteringpoints.toJsonArray

data class ChangeOfEnergySupplierDocumentMetadata(
    val requestedMeteringPoints: List<RequestedMeteringPointMetadata>,
    val balanceSupplierName: String
)

fun ChangeOfEnergySupplierDocumentMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("requestedMeteringPoints", requestedMeteringPoints.toJsonArray())
    put("balanceSupplierName", balanceSupplierName)
}
