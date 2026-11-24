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
import io.ktor.server.request.path
import kotlinx.serialization.json.Json

/** The media type required by the JSON:API specification. */
val jsonApiContentType: ContentType = ContentType.parse("application/vnd.api+json")

internal val ApplicationCall.isV1Request: Boolean
    get() = request.path().startsWith("/access/v1/")

internal class NoAcceptableJsonResponse : Exception()

internal fun ApplicationCall.requireAcceptableJsonResponse() {
    if (acceptedJsonTypes().isEmpty()) throw NoAcceptableJsonResponse()
}

internal fun ApplicationCall.acceptedJsonTypes(): List<ContentTypeWithQuality> {
    val ranges = request.headers.getAll(HttpHeaders.Accept).orEmpty().flatMap { parseHeaderValue(it) }
    if (ranges.isEmpty()) return listOf(ContentTypeWithQuality(jsonApiContentType))

    return listOf(jsonApiContentType, ContentType.Application.Json).mapNotNull { type ->
        // The most specific matching range supplies the weight, including an explicit q=0.
        val match = sequenceOf(type.toString(), "application/*", "*/*").firstNotNullOfOrNull { name ->
            ranges.firstOrNull { range ->
                range.value.equals(name, ignoreCase = true) && range.params.all { param ->
                    param.name.equals("q", ignoreCase = true) ||
                        (name == jsonApiContentType.toString() && param.name.equals("profile", ignoreCase = true))
                }
            }
        }
        match?.quality?.takeIf { it > 0.0 }?.let { ContentTypeWithQuality(type, it) }
    }
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
            val acceptedJson = call.acceptedJsonTypes().ifEmpty {
                if ((call.response.status()?.value ?: 200) >= 400) {
                    listOf(ContentTypeWithQuality(jsonApiContentType))
                } else {
                    throw NoAcceptableJsonResponse()
                }
            }
            call.response.headers.append(HttpHeaders.Vary, HttpHeaders.Accept)
            acceptedJson
        }
        json(json = defaultJson)
        register(
            jsonApiContentType,
            KotlinxSerializationConverter(defaultJson)
        )
    }
}
