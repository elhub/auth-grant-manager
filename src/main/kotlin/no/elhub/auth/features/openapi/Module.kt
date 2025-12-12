package no.elhub.auth.features.openapi

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

const val API_PATH_OPENAPI = "access/v0/openapi"
const val FILEPATH_OPENAPI_SPEC = "static/openapi.yaml"
const val API_PATH_SCHEMAS = "$API_PATH_OPENAPI/schemas"
const val FILEPATH_SCHEMAS = "static/schemas"

fun Application.module() {
    routing {
        swaggerUI(path = API_PATH_OPENAPI, swaggerFile = FILEPATH_OPENAPI_SPEC)
        staticResources(API_PATH_SCHEMAS, FILEPATH_SCHEMAS)
    }
}
