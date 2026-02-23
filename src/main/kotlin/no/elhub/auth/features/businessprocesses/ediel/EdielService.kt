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
            logger.info("Fetching party redirect from EDIEL for GLN {}", gln)
            val response = client.get("${edielApiConfig.serviceUrl}/PartyRedirectUrl/?gln=$gln") {
                basicAuth(
                    username = edielApiConfig.basicAuthConfig.username,
                    password = edielApiConfig.basicAuthConfig.password
                )
                header("Accept", "application/json")
            }
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                if (responseBody.isBlank() || responseBody.trim().equals("null", ignoreCase = true)) {
                    logger.warn("EDIEL returned empty/null body for GLN {}", gln)
                    return ClientError.NotFound.left()
                }
                val edielResponse = runCatching {
                    json.decodeFromString<EdielPartyRedirectResponseDto>(responseBody)
                }.getOrElse { error ->
                    logger.warn("EDIEL returned non-parseable redirect response for GLN {}: {}", gln, error.message)
                    return ClientError.NotFound.left()
                }
                logger.info("Successfully fetched party redirect from EDIEL for GLN {}", gln)
                edielResponse
            } else if (response.status == HttpStatusCode.Unauthorized) {
                logger.error("Failed to fetch party redirect from EDIEL for GLN {} with status {}", gln, response.status.value)
                return ClientError.Unauthorized.left()
            } else {
                logger.error("Failed to fetch party redirect from EDIEL for GLN {} with status {}", gln, response.status.value)
                return mapErrorsFromServer(response).left()
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch party redirect from EDIEL for GLN {}: {}", gln, throwable.message)
            ClientError.UnexpectedError(throwable)
        }
}

data class EdielApiConfig(
    val serviceUrl: String,
    val basicAuthConfig: BasicAuthConfig,
)

data class BasicAuthConfig(
    val username: String,
    val password: String
)

private val json = Json {
    ignoreUnknownKeys = true
}
