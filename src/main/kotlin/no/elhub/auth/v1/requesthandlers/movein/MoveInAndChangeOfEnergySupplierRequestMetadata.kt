package no.elhub.auth.v1.requesthandlers.movein

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v1.common.meteringpoints.RequestedMeteringPointMetadata
import no.elhub.auth.v1.common.meteringpoints.toJsonArray

data class MoveInAndChangeOfEnergySupplierRequestMetadata(
    val requestedMeteringPoints: List<RequestedMeteringPointMetadata>,
    val moveInDate: LocalDate,
    val balanceSupplierName: String,
    val organizationName: String,
)

fun MoveInAndChangeOfEnergySupplierRequestMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("requestedMeteringPoints", requestedMeteringPoints.toJsonArray())
    put("moveInDate", moveInDate.toString())
    put("balanceSupplierName", balanceSupplierName)
    put("organizationName", organizationName)
}
