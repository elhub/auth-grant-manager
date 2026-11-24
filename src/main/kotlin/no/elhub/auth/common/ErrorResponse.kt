package no.elhub.auth.common

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseHeaderValue
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import no.elhub.auth.common.config.jsonApiContentType
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

internal suspend fun ApplicationCall.respondError(status: HttpStatusCode, body: JsonApiErrorCollection) {
    response.headers.append(HttpHeaders.Vary, HttpHeaders.Accept)
    // Send the error directly: Ktor's normal negotiation would turn a missing PDF's 404 into
    // an empty 406 if the client only accepts application/pdf, since we have no PDF error format.
    respondText(Json.encodeToString(body), preferredJsonResponseType() ?: jsonApiContentType, status)
}

internal fun ApplicationCall.preferredJsonResponseType(): ContentType? {
    val plainJson = ContentType.Application.Json
    val acceptRanges = request.headers.getAll(HttpHeaders.Accept).orEmpty()
        .flatMap { parseHeaderValue(it) }
    if (acceptRanges.isEmpty()) return jsonApiContentType

    // Both representations are unparameterized, so only the preference weight (q) may be present.
    fun weightFor(mediaRange: String): Double? = acceptRanges
        .filter { it.value.equals(mediaRange, ignoreCase = true) }
        .filter { range -> range.params.all { it.name.equals("q", ignoreCase = true) } }
        .maxOfOrNull { it.quality }

    // An exact match overrides wildcards, even when it excludes the type with q=0.
    val wildcardWeight = weightFor("application/*") ?: weightFor("*/*") ?: 0.0
    val plainJsonWeight = weightFor(plainJson.toString()) ?: wildcardWeight
    val jsonApiWeight = weightFor(jsonApiContentType.toString()) ?: wildcardWeight

    return when {
        plainJsonWeight > jsonApiWeight -> plainJson
        jsonApiWeight > 0.0 -> jsonApiContentType
        else -> null
    }
}
