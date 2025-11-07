package no.elhub.auth.features.documents.create

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.util.UUID

interface EndUserService {
    suspend fun findOrCreateByNin(nin: String): Either<ClientError, EndUser>
}

class ApiEndUserService(
    private val cfg: EndUserApiConfig,
    private val client: HttpClient
) : EndUserService {

    override suspend fun findOrCreateByNin(nin: String): Either<ClientError, EndUser> =
        Either.catch {
            val response = client.get("${cfg.baseUri}/persons/$nin")
            val responseBody: AuthPersonsResponse = response.body()
            EndUser(internalId = UUID.fromString(responseBody.data.id))
        }.mapLeft { ClientError.UnexpectedError }
}

sealed class ClientError {
    data object UnexpectedError : ClientError()
}

data class EndUserApiConfig(
    val baseUri: String,
)
