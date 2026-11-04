package no.elhub.auth.v1.common.meteringpoints

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.documents.pdf.MeteringPointPdfContent
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointAddress
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointResponse

data class RequestedMeteringPointMetadata(
    val id: String,
    val address: MeteringPointAddress?,
    val meterNumber: String?,
)

fun RequestedMeteringPointMetadata.toPdfContent(): MeteringPointPdfContent =
    MeteringPointPdfContent(
        address = address?.toPdfAddress()?.takeIf { it.isNotBlank() } ?: "Unknown",
        id = id,
        meterNumber = meterNumber ?: "Unknown",
    )

private fun MeteringPointAddress.toPdfAddress(): String {
    val streetAddress = listOfNotNull(streetName, houseNumber).joinToString(" ")
    val locality = listOfNotNull(postalCode, city).joinToString(" ")
    return listOf(streetAddress, locality)
        .filter { it.isNotBlank() }
        .joinToString(", ")
}

fun List<MeteringPointResponse>.toRequestedMeteringPointMetadata(): List<RequestedMeteringPointMetadata> =
    map { meteringPoint ->
        RequestedMeteringPointMetadata(
            id = meteringPoint.data.id,
            address = meteringPoint.data.attributes?.accountingPoint?.address,
            meterNumber = meteringPoint.data.attributes?.accountingPoint?.meter?.meterNumber,
        )
    }

fun List<RequestedMeteringPointMetadata>.toJsonArray(): JsonArray = JsonArray(map { it.toJsonObject() })

private fun RequestedMeteringPointMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id)
    address?.let { put("address", it.toJsonObject()) }
    meterNumber?.let { put("meterNumber", it) }
}

private fun MeteringPointAddress.toJsonObject(): JsonObject = buildJsonObject {
    streetName?.let { put("streetName", it) }
    houseNumber?.let { put("houseNumber", it) }
    put("postalCode", postalCode)
    city?.let { put("city", it) }
}
