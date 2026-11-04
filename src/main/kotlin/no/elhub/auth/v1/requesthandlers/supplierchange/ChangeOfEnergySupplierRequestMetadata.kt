package no.elhub.auth.v1.requesthandlers.supplierchange

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.v1.common.meteringpoints.RequestedMeteringPointMetadata
import no.elhub.auth.v1.common.meteringpoints.toJsonArray

data class ChangeOfEnergySupplierRequestMetadata(
    val requestedMeteringPoints: List<RequestedMeteringPointMetadata>,
)

fun ChangeOfEnergySupplierRequestMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("requestedMeteringPoints", requestedMeteringPoints.toJsonArray())
}
