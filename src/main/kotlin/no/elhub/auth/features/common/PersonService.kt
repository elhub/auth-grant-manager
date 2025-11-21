package no.elhub.auth.features.common

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
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
            val response = client.post("${cfg.baseUri}/persons") {
                contentType(ContentType.Application.Json)
                setBody(
                    PersonRequest(
                        data = JsonApiRequestResourceObject(
                            type = "Person",
                            attributes = PersonRequestAttributes(
                                nationalIdentityNumber = nin
                            )
                        )
                    )
                )
            }

            if (!response.status.isSuccess()) {
                throw ClientRequestException(response, response.bodyAsText())
            }
            val responseBody: PersonsResponse = response.body()
            Person(internalId = UUID.fromString(responseBody.data.id))
        }.mapLeft { throwable ->
            logger.error("Failed to fetch person: {}", throwable.message)
            ClientError.UnexpectedError(throwable)
        }
}

sealed class ClientError {
    data class UnexpectedError(val cause: Throwable) : ClientError()
}

data class PersonApiConfig(
    val baseUri: String,
)
