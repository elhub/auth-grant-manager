package no.elhub.auth.features.businessprocesses.structuredata.organisations

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject
import org.slf4j.LoggerFactory

interface OrganisationsService {
    suspend fun getPartyByIdAndPartyType(
        partyId: String,
        partyType: String
    ): Either<ClientError, PartyResponse>
}

class OrganisationsApi(
    private val organisationsApiConfig: OrganisationsApiConfig,
    private val client: HttpClient
) : OrganisationsService {
    private val logger = LoggerFactory.getLogger(OrganisationsService::class.java)

    override suspend fun getPartyByIdAndPartyType(partyId: String, partyType: String): Either<ClientError, PartyResponse> =
        Either.catch {
            val response = client.get("${organisationsApiConfig.serviceUrl}/parties/$partyId?partyType=$partyType")
            if (response.status.isSuccess()) {
                val responseBody: PartyResponse = response.body()
                responseBody
            } else {
                logger.error("Failed to fetch party with status: ${response.status.value}")
                val errorResponse = parseError(response)
                return when (errorResponse?.status) {
                    "404" -> ClientError.NotFound.left()
                    "401" -> ClientError.Unauthorized.left()
                    "500" -> ClientError.ServerError.left()
                    else -> ClientError.UnexpectedError(ClientRequestException(response, response.bodyAsText())).left()
                }
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch party: {}", throwable.message)
            ClientError.UnexpectedError(throwable)
        }
}

data class OrganisationsApiConfig(
    val serviceUrl: String,
    val basicAuthConfig: BasicAuthConfig
)

data class BasicAuthConfig(
    val username: String,
    val password: String
)

sealed class ClientError {
    data class UnexpectedError(val cause: Throwable) : ClientError()
    data object NotFound : ClientError()
    data object Unauthorized : ClientError()
    data object ServerError : ClientError()
}

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private suspend fun parseError(response: HttpResponse): JsonApiErrorObject? {
    val text = response.bodyAsText()
    val doc = json.decodeFromString<JsonApiErrorCollection>(text)
    return doc.errors.firstOrNull()
}
