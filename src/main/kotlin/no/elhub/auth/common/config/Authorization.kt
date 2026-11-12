package no.elhub.auth.common.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.http.Headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.request.path
import kotlinx.serialization.json.Json
import no.elhub.auth.common.ApiHeaders
import no.elhub.auth.common.ConflictingGlnHeadersException
import no.elhub.auth.common.auth.AuthorizedPartyKey
import no.elhub.auth.common.auth.UpdateRequestAuthInfoPolicy
import no.elhub.auth.common.auth.resolveAuthorizedParty
import no.elhub.auth.common.auth.resolveUpdateRequestActor
import no.elhub.auth.plugin.authorization
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy

fun Application.configureAuthorization() {
    val pdpHttpClient =
        HttpClient(Apache5) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            install(Logging) {
                format = LoggingFormat.OkHttp
                level = LogLevel.INFO
            }
        }

    authorization {
        client = pdpHttpClient
        pdpUrl = environment.config.property("pdp.baseUrl").getString()

        tokenPolicy(AuthInfoPolicy) {
            buildPayload {
                AuthInfoPolicy.Request(
                    senderGln = resolveGlnHeader(request.headers, request.path(), ApiHeaders.ELHUB_SENDER_GLN, ApiHeaders.SENDER_GLN),
                    onBehalfOfGln =
                    resolveGlnHeader(
                        request.headers,
                        request.path(),
                        ApiHeaders.ELHUB_ON_BEHALF_OF_GLN,
                        ApiHeaders.ON_BEHALF_OF_GLN,
                    ),
                    onBehalfOfOrganisationId = request.headers[ApiHeaders.ON_BEHALF_OF_ORGANISATION]?.ifBlank { null },
                )
            }

            enforce { response ->
                // Resolve the AuthorizationParty from the PDP response and store it on call attributes
                // so route handlers can access it via call.authorizedParty
                val party = resolveAuthorizedParty(response.result)
                if (party != null) {
                    attributes.put(AuthorizedPartyKey, party)
                    true
                } else {
                    false
                }
            }
        }

        tokenPolicy(UpdateRequestAuthInfoPolicy) {
            buildPayload {
                AuthInfoPolicy.Request(
                    onBehalfOfOrganisationId = request.headers[ApiHeaders.ON_BEHALF_OF_ORGANISATION]?.ifBlank { null },
                )
            }

            enforce { response ->
                val party = resolveUpdateRequestActor(response.result)
                if (party != null) {
                    attributes.put(AuthorizedPartyKey, party)
                    true
                } else {
                    false
                }
            }
        }
    }
}

internal fun resolveGlnHeader(
    headers: Headers,
    path: String,
    preferred: String,
    legacy: String,
): String? {
    // Keep legacy names on v1 temporarily so existing consumers can migrate to the Elhub-* headers.
    // v0 continues to use only the original names.
    val oldValue = headers[legacy]?.ifBlank { null }
    if (!path.startsWith("/access/v1/")) return oldValue

    val newValue = headers[preferred]?.ifBlank { null }
    if (newValue != null && oldValue != null && newValue != oldValue) {
        throw ConflictingGlnHeadersException(preferred, legacy)
    }
    return newValue ?: oldValue
}
