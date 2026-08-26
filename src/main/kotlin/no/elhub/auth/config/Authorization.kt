package no.elhub.auth.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json
import no.elhub.auth.features.common.ApiHeaders
import no.elhub.auth.features.common.auth.AuthorizedPartyKey
import no.elhub.auth.features.common.auth.resolveAuthorizedParty
import no.elhub.auth.plugin.authorization
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy

fun Application.configureAuthorization() {
    val pdpHttpClient = HttpClient(Apache5) {
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
                }
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
                    senderGln = request.headers[ApiHeaders.SENDER_GLN]?.ifBlank { null },
                    onBehalfOfGln = request.headers[ApiHeaders.ON_BEHALF_OF_GLN]?.ifBlank { null },
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
    }
}
