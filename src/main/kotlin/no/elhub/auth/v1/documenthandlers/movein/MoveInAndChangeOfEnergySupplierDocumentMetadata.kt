package no.elhub.auth.v1.documenthandlers.movein

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v1.common.meteringpoints.RequestedMeteringPointMetadata
import no.elhub.auth.v1.common.meteringpoints.toJsonArray

data class MoveInAndChangeOfEnergySupplierDocumentMetadata(
    val requestedMeteringPoints: List<RequestedMeteringPointMetadata>,
    val validFrom: LocalDate,
)

fun MoveInAndChangeOfEnergySupplierDocumentMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("requestedMeteringPoints", requestedMeteringPoints.toJsonArray())
    put("validFrom", validFrom.toString())
}
