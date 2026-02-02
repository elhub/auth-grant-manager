package no.elhub.auth.features.common.person

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                val err = getError(response)
                return if (err?.code == "invalid_national_identity_number") {
                    ClientError.InvalidNin.left()
                } else {
                    logger.error("Request to auth-persons was rejected: {}", err?.title ?: "unknown")
                    ClientError.RequestRejected.left()
                }
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
    data object InvalidNin : ClientError()
    data object RequestRejected : ClientError()
}

data class PersonApiConfig(
    val baseUri: String,
)

private data class UpstreamError(
    val code: String? = null,
    val title: String? = null,
    val detail: String? = null,
)

private suspend fun getError(response: HttpResponse): UpstreamError? {
    val bodyText = response.bodyAsText()
    return runCatching {
        Json.parseToJsonElement(bodyText)
            .jsonObject["errors"]?.jsonArray
            ?.getOrNull(0)?.jsonObject
            ?.let { o ->
                UpstreamError(
                    code = o["code"]?.jsonPrimitive?.contentOrNull,
                    title = o["title"]?.jsonPrimitive?.contentOrNull,
                    detail = o["detail"]?.jsonPrimitive?.contentOrNull
                )
            }
    }.getOrNull()
}
