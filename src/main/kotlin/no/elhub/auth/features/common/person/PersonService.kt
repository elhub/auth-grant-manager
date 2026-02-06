package no.elhub.auth.features.common.person

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
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
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID

interface PersonService {
    suspend fun findOrCreateByNin(nin: String): Either<ClientError, Person>
}

class ApiPersonService(
    private val cfg: PersonApiConfig,
    private val client: HttpClient
) : PersonService {

    private val logger = LoggerFactory.getLogger(PersonService::class.java)

    private companion object {
        const val TRACE_HEADER = "Elhub-Trace-Id"
        const val CALL_ID_MDC_KEY = "traceId"
    }

    override suspend fun findOrCreateByNin(nin: String): Either<ClientError, Person> {
        val traceId = MDC.get(CALL_ID_MDC_KEY)
        val traceHeader = traceIdIsValid(traceId).getOrElse {
            logger.error("The required request header to auth-persons is missing! ")
            return ClientError.MissingHeader.left()
        }

        return Either.catch {
            val response = client.post("${cfg.baseUri}/persons") {
                headers[TRACE_HEADER] = traceHeader.toString()
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
                val parsed = parseError(response)
                return if (parsed?.code == "invalid_national_identity_number") {
                    ClientError.InvalidNin.left()
                } else {
                    logger.error("Request to auth-persons was rejected: {}", parsed?.title ?: "unknown")
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
}

sealed class ClientError {
    data class UnexpectedError(val cause: Throwable) : ClientError()
    data object InvalidNin : ClientError()
    data object RequestRejected : ClientError()
    data object MissingHeader : ClientError()
}

data class PersonApiConfig(
    val baseUri: String,
)

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private suspend fun parseError(response: HttpResponse): JsonApiErrorObject? {
    val text = response.bodyAsText()
    val doc = json.decodeFromString<JsonApiErrorCollection>(text)
    return doc.errors.firstOrNull()
}

private fun traceIdIsValid(traceId: String?): Either<ClientError, UUID> =
    if (traceId.isNullOrBlank()) {
        ClientError.MissingHeader.left()
    } else {
        try {
            UUID.fromString(traceId).right()
        } catch (_: IllegalArgumentException) {
            ClientError.MissingHeader.left()
        }
    }
