package no.elhub.auth.v1.acceptance.support

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import no.elhub.auth.common.ApiHeaders

/** Sends API calls as a given [Caller] to the running [ProductionApp]. */
class Api(private val httpClient: HttpClient) {
    suspend fun call(
        method: HttpMethod,
        path: String,
        caller: Caller,
        body: String? = null,
        pdf: ByteArray? = null,
    ): ApiResponse {
        val response: HttpResponse = httpClient.request(path) {
            this.method = method
            authenticateAs(caller)
            when {
                body != null -> {
                    contentType(ContentType.Application.Json)
                    setBody(body.trimIndent())
                }

                pdf != null -> {
                    contentType(ContentType.Application.Pdf)
                    setBody(pdf)
                }
            }
        }
        return ApiResponse(response.status, response.bodyAsBytes(), response.contentType())
    }

    private fun HttpRequestBuilder.authenticateAs(caller: Caller) {
        if (caller is Caller.Anonymous) return
        header(HttpHeaders.Authorization, "Bearer ${caller.token}")
        when (caller) {
            is Caller.MarketEntity -> {
                caller.senderGln?.let { header(ApiHeaders.ELHUB_SENDER_GLN, it) }
                caller.onBehalfOfGln?.let { header(ApiHeaders.ELHUB_ON_BEHALF_OF_GLN, it) }
            }

            is Caller.EndUser -> caller.onBehalfOfOrganization?.let {
                header(ApiHeaders.ON_BEHALF_OF_ORGANISATION, it)
            }

            Caller.Anonymous -> Unit
        }
    }
}
