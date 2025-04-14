package no.elhub.auth.config

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import no.elhub.auth.features.errors.ApiError

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
        json(json = defaultJson)
    }
}

/*@OptIn(ExperimentalSerializationApi::class)
private val defaultJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = true
    ignoreUnknownKeys = true
}
*/
