package no.elhub.auth.common.config

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import no.elhub.auth.common.ApiHeaders

private const val V1_PATH_PREFIX = "/access/v1/"

internal data class AuthorizationHeaders(val senderGln: String?, val onBehalfOfGln: String?)

private val AuthorizationHeadersKey = AttributeKey<AuthorizationHeaders>("AuthorizationHeaders")

internal val ApplicationCall.authorizationHeaders: AuthorizationHeaders
    get() = attributes[AuthorizationHeadersKey]

val ResolveAuthorizationHeaders = createApplicationPlugin(name = "ResolveAuthorizationHeaders") {
    onCall { call ->
        val headers = call.request.headers
        val isV1 = call.request.path().startsWith(V1_PATH_PREFIX)
        call.attributes.put(
            AuthorizationHeadersKey,
            AuthorizationHeaders(
                senderGln = (if (isV1) headers[ApiHeaders.ELHUB_SENDER_GLN]?.ifBlank { null } else null)
                    ?: headers[ApiHeaders.SENDER_GLN]?.ifBlank { null },
                onBehalfOfGln = (if (isV1) headers[ApiHeaders.ELHUB_ON_BEHALF_OF_GLN]?.ifBlank { null } else null)
                    ?: headers[ApiHeaders.ON_BEHALF_OF_GLN]?.ifBlank { null },
            ),
        )
    }
}
