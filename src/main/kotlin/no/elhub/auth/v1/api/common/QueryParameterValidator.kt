package no.elhub.auth.v1.api.common

import io.ktor.server.application.ApplicationCall
import no.elhub.auth.v1.api.common.errors.InputError

fun ApplicationCall.validateQueryParameters(vararg supported: String) {
    request.queryParameters.names().firstOrNull { it !in supported }?.let { parameter ->
        throw InputError.InvalidQueryParameterError("Unsupported query parameter '$parameter'.")
    }
}
