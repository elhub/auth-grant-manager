package no.elhub.auth.v1.documenthandlers.supplierchange

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.v1.common.meteringpoints.RequestedMeteringPointMetadata
import no.elhub.auth.v1.common.meteringpoints.toJsonArray

data class ChangeOfEnergySupplierDocumentMetadata(
    val requestedMeteringPoints: List<RequestedMeteringPointMetadata>,
)

fun ChangeOfEnergySupplierDocumentMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("requestedMeteringPoints", requestedMeteringPoints.toJsonArray())
}
