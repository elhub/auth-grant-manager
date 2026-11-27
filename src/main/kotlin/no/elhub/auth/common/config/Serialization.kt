package no.elhub.auth.common.config

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.parseHeaderValue
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentTypeWithQuality
import kotlinx.serialization.json.Json

/** The media type required by the JSON:API specification. */
val jsonApiContentType: ContentType = ContentType.parse("application/vnd.api+json")

internal class NoAcceptableJsonResponse : Exception()

internal fun ApplicationCall.requireAcceptableJsonResponse() {
    if (acceptedJsonType() == null) throw NoAcceptableJsonResponse()
}

internal fun ApplicationCall.acceptedJsonType(): ContentType? {
    val acceptHeaderValues = request.headers.getAll(HttpHeaders.Accept).orEmpty().flatMap { parseHeaderValue(it) }
    if (acceptHeaderValues.isEmpty()) return jsonApiContentType

    fun accepts(mediaRange: String): Boolean = acceptHeaderValues.any { it.value.equals(mediaRange, ignoreCase = true) }

    if (accepts(jsonApiContentType.toString()) || accepts("application/*") || accepts("*/*")) {
        return jsonApiContentType
    }

    val plainJson = ContentType.Application.Json
    return plainJson.takeIf { accepts(it.toString()) }
}

/** Configure the serialization plugin for JSON
 *
 * Uses the Ktor ContentNegotiation plugin
 */
fun Application.configureSerialization() {
    val defaultJson = Json {
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
        allowSpecialFloatingPointValues = true
        allowStructuredMapKeys = true
        prettyPrint = false
        useArrayPolymorphism = true
        ignoreUnknownKeys = true
    }
    install(ContentNegotiation) {
        accept { call, accepted ->
            if (!call.isV1Request) return@accept accepted
            val contentType = call.acceptedJsonType() ?: run {
                if ((call.response.status()?.value ?: 200) >= 400) {
                    jsonApiContentType
                } else {
                    throw NoAcceptableJsonResponse()
                }
            }
            call.response.headers.append(HttpHeaders.Vary, HttpHeaders.Accept)
            listOf(ContentTypeWithQuality(contentType))
        }
        json(json = defaultJson)
        register(
            jsonApiContentType,
            KotlinxSerializationConverter(defaultJson)
        )
    }
}
