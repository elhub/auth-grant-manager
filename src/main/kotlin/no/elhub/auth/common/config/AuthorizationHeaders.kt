package no.elhub.auth.common.config

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import no.elhub.auth.common.ApiHeaders

internal data class AuthorizationHeaders(val senderGln: String?, val onBehalfOfGln: String?)

private val AuthorizationHeadersKey = AttributeKey<AuthorizationHeaders>("AuthorizationHeaders")

internal val ApplicationCall.authorizationHeaders: AuthorizationHeaders
    get() = attributes[AuthorizationHeadersKey]

val ResolveAuthorizationHeaders = createApplicationPlugin(name = "ResolveAuthorizationHeaders") {
    onCall { call ->
        val headers = call.request.headers
        val isV1 = call.isV1
        call.attributes.put(
            AuthorizationHeadersKey,
            AuthorizationHeaders(
                senderGln = headers[if (isV1) ApiHeaders.ELHUB_SENDER_GLN else ApiHeaders.SENDER_GLN]?.ifBlank { null },
                onBehalfOfGln = headers[if (isV1) ApiHeaders.ELHUB_ON_BEHALF_OF_GLN else ApiHeaders.ON_BEHALF_OF_GLN]?.ifBlank { null },
            ),
        )
    }
}
