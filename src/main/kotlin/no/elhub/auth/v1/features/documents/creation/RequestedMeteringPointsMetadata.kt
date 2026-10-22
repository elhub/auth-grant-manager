package no.elhub.auth.v1.features.documents.creation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointAddress
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointResponse

data class RequestedMeteringPointMetadata(
    val id: String,
    val address: MeteringPointAddress?,
    val meterNumber: String?,
)

data class RequestedMeteringPointsMetadata(
    val requestedMeteringPoints: List<RequestedMeteringPointMetadata>,
)

fun List<MeteringPointResponse>.toRequestedMeteringPointsMetadata() = RequestedMeteringPointsMetadata(
    requestedMeteringPoints = map { meteringPoint ->
        RequestedMeteringPointMetadata(
            id = meteringPoint.data.id,
            address = meteringPoint.data.attributes?.accountingPoint?.address,
            meterNumber = meteringPoint.data.attributes?.accountingPoint?.meter?.meterNumber,
        )
    },
)

fun RequestedMeteringPointsMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put(
        "requestedMeteringPoints",
        JsonArray(
            requestedMeteringPoints.map { point ->
                buildJsonObject {
                    put("id", point.id)
                    point.address?.let { put("address", it.toJsonObject()) }
                    point.meterNumber?.let { put("meterNumber", it) }
                }
            },
        ),
    )
}

private fun MeteringPointAddress.toJsonObject(): JsonObject = buildJsonObject {
    streetName?.let { put("streetName", it) }
    houseNumber?.let { put("houseNumber", it) }
    put("postalCode", postalCode)
    city?.let { put("city", it) }
}
