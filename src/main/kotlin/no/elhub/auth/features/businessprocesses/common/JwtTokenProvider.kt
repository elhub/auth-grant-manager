package no.elhub.auth.features.businessprocesses.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

interface JwtTokenProvider {
    suspend fun getToken(): String
}

class JwtTokenProviderImpl(
    private val httpClient: HttpClient,
    private val authConfig: AuthConfig,
) : JwtTokenProvider {
    private var cachedToken: String? = null
    private var expirationInstant: Instant? = null

    override suspend fun getToken(): String {
        val now = Instant.now()
        if (cachedToken != null && expirationInstant != null && now.isBefore(expirationInstant)) {
            return cachedToken!!
        }
        val response = httpClient.post(authConfig.tokenUrl) {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "client_credentials")
                        append("client_id", authConfig.clientId)
                        append("client_secret", authConfig.clientSecret)
                    }
                )
            )
        }
        val tokenResponse: TokenResponse = response.body()
        cachedToken = tokenResponse.accessToken
        expirationInstant = now.plusSeconds(tokenResponse.expiresIn - 20)
        return cachedToken!!
    }
}

data class AuthConfig(
    val clientId: String,
    val clientSecret: String,
    val tokenUrl: String
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long
)
