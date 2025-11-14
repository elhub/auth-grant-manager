package no.elhub.auth.features.documents.create

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.util.UUID

interface PersonService {
    suspend fun findOrCreateByNin(nin: String): Either<ClientError, Person>
}

class ApiPersonService(
    private val cfg: PersonApiConfig,
    private val client: HttpClient
) : PersonService {

    private val logger = LoggerFactory.getLogger(PersonService::class.java)

    override suspend fun findOrCreateByNin(nin: String): Either<ClientError, Person> =
        Either.catch {
            val response = client.get("${cfg.baseUri}/persons/$nin")

            if (!response.status.isSuccess()) {
                throw ClientRequestException(response, response.bodyAsText())
            }
            val responseBody: AuthPersonsResponse = response.body()
            Person(internalId = UUID.fromString(responseBody.data.id))
        }.mapLeft { throwable ->
            logger.error("Failed to fetch person by NIN: {}, \n Error message: {}", nin, throwable.message)
            ClientError.UnexpectedError(throwable)
        }
}

sealed class ClientError {
    data class UnexpectedError(val cause: Throwable) : ClientError()
}

data class PersonApiConfig(
    val baseUri: String,
)
