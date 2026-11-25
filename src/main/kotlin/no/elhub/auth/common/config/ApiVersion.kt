package no.elhub.auth.common.config

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path

internal val ApplicationCall.isV1Request: Boolean
    get() = request.path().startsWith("/access/v1/")
