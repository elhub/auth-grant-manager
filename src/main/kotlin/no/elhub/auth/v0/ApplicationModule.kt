package no.elhub.auth.v0

import io.ktor.server.application.Application
import no.elhub.auth.common.module as commonModule

/** Compatibility entrypoint for existing v0 test and deployment references. */
fun Application.module() = commonModule()
