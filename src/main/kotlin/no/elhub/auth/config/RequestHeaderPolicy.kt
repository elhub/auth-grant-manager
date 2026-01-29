package no.elhub.auth.config

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respond
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

val HeaderPolicy = createApplicationPlugin(
    name = "HeaderPolicy",
) {
    onCall { call ->
        val userAgent = call.request.headers[HttpHeaders.UserAgent]
        if (userAgent.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                JsonApiErrorCollection(
                    listOf(MissingUserAgentHeder)
                )
            )
            return@onCall
        }
    }
}

val MissingUserAgentHeder: JsonApiErrorObject =
    JsonApiErrorObject(
        status = "400",
        title = "Bad request",
        detail = "Missing User-Agent header"
    )
