package no.elhub.auth.common.config

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.http.content.HttpStatusCodeContent
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.responseType
import kotlinx.serialization.json.Json
import no.elhub.auth.common.preferredJsonResponseType
import no.elhub.auth.v0.features.common.dto.PaginatedCollectionResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiResponse

/** The media type required by the JSON:API specification. */
val jsonApiContentType: ContentType = ContentType.parse("application/vnd.api+json")

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
    val converter = KotlinxSerializationConverter(defaultJson)
    // Handle v1 documents before Ktor's general-purpose content negotiation.
    install(
        createApplicationPlugin("V1JsonResponses") {
            onCallRespond { call, body ->
                if (!call.request.path().startsWith("/access/v1/")) return@onCallRespond
                if (body !is JsonApiResponse.Document<*> && body !is PaginatedCollectionResponse<*> && body !is JsonApiErrorCollection) {
                    return@onCallRespond
                }
                val typeInfo = call.response.responseType ?: return@onCallRespond
                val contentType = call.preferredJsonResponseType() ?: jsonApiContentType.takeIf { body is JsonApiErrorCollection }
                call.response.headers.append(HttpHeaders.Vary, HttpHeaders.Accept)
                transformBody {
                    if (contentType == null) {
                        HttpStatusCodeContent(HttpStatusCode.NotAcceptable)
                    } else {
                        converter.serialize(contentType, Charsets.UTF_8, typeInfo, body)
                    }
                }
            }
        }
    )
    install(ContentNegotiation) {
        json(json = defaultJson)
        register(
            jsonApiContentType,
            converter
        )
    }
}
