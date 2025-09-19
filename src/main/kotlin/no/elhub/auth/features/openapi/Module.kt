package no.elhub.auth.features.openapi

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

const val OPENAPI = "openapi"
const val SPEC_FILE = "openapi.yaml"
const val SCHEMAS_LOCATION = "schemas/"
const val SCHEMAS = "schemas"

fun Application.module() {
    routing {
        swaggerUI(path = OPENAPI, swaggerFile = SPEC_FILE)
        staticResources(SCHEMAS_LOCATION, SCHEMAS)
    }
}
