package no.elhub.auth.features.common

import io.ktor.server.routing.Route
import io.ktor.server.routing.application

private const val FEATURE_TOGGLE_ENDPOINT = "featureToggle.enableEndpoints"

/**
 * Guards a subtree of routes behind the `featureToggle.isEndpointsEnabled` config boolean.
 * Defaults to disabled when the value is missing or unrecognised.
 */
fun Route.shouldRegisterEndpoint(
    block: Route.() -> Unit
) {
    if (isEnabled()) {
        block()
        return
    }
}

private fun Route.isEnabled(): Boolean =
    application.environment.config
        .propertyOrNull(FEATURE_TOGGLE_ENDPOINT)
        ?.getString()
        ?.toBooleanStrict() ?: false
