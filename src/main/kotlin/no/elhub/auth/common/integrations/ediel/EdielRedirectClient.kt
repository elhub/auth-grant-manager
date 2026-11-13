package no.elhub.auth.common.integrations.ediel

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface EdielRedirectClient {
    suspend fun getPartyRedirectUri(gln: String): String?
}

class HttpEdielRedirectClient(
    private val config: EdielConfig,
    private val httpClient: HttpClient,
) : EdielRedirectClient {
    override suspend fun getPartyRedirectUri(gln: String): String? {
        val response = try {
            httpClient.get("${config.serviceUrl.trimEnd('/')}/PartyRedirectUrl") {
                parameter("gln", gln)
                basicAuth(config.username, config.password)
                accept(ContentType.Application.Json)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw EdielRedirectRequestException("Ediel redirect lookup failed", cause)
        }

        if (response.status == HttpStatusCode.NotFound) {
            throw EdielPartyRedirectNotFoundException()
        }
        if (!response.status.isSuccess()) {
            throw EdielRedirectRequestException("Ediel returned HTTP ${response.status.value}")
        }

        val body = response.bodyAsText()
        if (body.isBlank() || body.trim().equals("null", ignoreCase = true)) {
            throw EdielPartyRedirectNotFoundException()
        }

        val responseBody = try {
            json.decodeFromString<EdielPartyRedirectResponse>(body)
        } catch (cause: SerializationException) {
            throw EdielRedirectRequestException("Ediel returned an invalid redirect response", cause)
        }

        return responseBody.redirectUriFor(config.environment)
    }
}

class EdielPartyRedirectNotFoundException : Exception("No Ediel redirect URL was found for the party")

class EdielRedirectRequestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class EdielPartyRedirectResponse(
    @SerialName("RedirectUrls")
    val redirectUrls: EdielRedirectUrls,
)

@Serializable
private data class EdielRedirectUrls(
    @SerialName("Production")
    val production: String? = null,
    @SerialName("Test")
    val test: String? = null,
)

private fun EdielPartyRedirectResponse.redirectUriFor(environment: EdielEnvironment): String? =
    when (environment) {
        EdielEnvironment.PRODUCTION -> redirectUrls.production
        EdielEnvironment.TEST -> redirectUrls.test
    }

private val json = Json { ignoreUnknownKeys = true }
