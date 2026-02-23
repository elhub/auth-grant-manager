package no.elhub.auth.features.businessprocesses.ediel

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.common.mapErrorsFromServer
import org.slf4j.LoggerFactory

interface EdielService {
    suspend fun getPartyRedirect(gln: String): Either<ClientError, EdielPartyRedirectResponseDto>
}

class EdielApi(
    private val edielApiConfig: EdielApiConfig,
    private val client: HttpClient
) : EdielService {
    private val logger = LoggerFactory.getLogger(EdielService::class.java)

    override suspend fun getPartyRedirect(gln: String): Either<ClientError, EdielPartyRedirectResponseDto> =
        Either.catch {
            logger.info("Fetching party redirect from Ediel for GLN {}", gln)
            val response = client.get("${edielApiConfig.serviceUrl}/PartyRedirectUrl?gln=$gln") {
                basicAuth(
                    username = edielApiConfig.basicAuthConfig.username,
                    password = edielApiConfig.basicAuthConfig.password
                )
                header("Accept", "application/json")
            }
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                if (responseBody.isBlank() || responseBody.trim().equals("null", ignoreCase = true)) {
                    logger.warn("Ediel returned empty/null body for GLN {}", gln)
                    return ClientError.NotFound.left()
                }
                val edielResponse = runCatching {
                    json.decodeFromString<EdielPartyRedirectResponseDto>(responseBody)
                }.getOrElse { error ->
                    logger.warn("Ediel returned non-parseable redirect response for GLN {}: {}", gln, error.message)
                    return ClientError.NotFound.left()
                }
                logger.info("Successfully fetched party redirect from Ediel for GLN {}", gln)
                edielResponse
            } else if (response.status == HttpStatusCode.Unauthorized) {
                logger.error("Ediel returned 401 Unauthorized for GLN {}", gln)
                return ClientError.Unauthorized.left()
            } else {
                logger.error("Failed to fetch party redirect from Ediel for GLN {} with status {}", gln, response.status.value)
                return mapErrorsFromServer(response).left()
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch party redirect from Ediel for GLN {}: {}", gln, throwable.message)
            ClientError.UnexpectedError(throwable)
        }
}

data class EdielApiConfig(
    val serviceUrl: String,
    val basicAuthConfig: BasicAuthConfig,
    val environment: EdielEnvironment,
)

data class BasicAuthConfig(
    val username: String,
    val password: String
)

enum class EdielEnvironment {
    TEST,
    PRODUCTION
}

private val json = Json {
    ignoreUnknownKeys = true
}
