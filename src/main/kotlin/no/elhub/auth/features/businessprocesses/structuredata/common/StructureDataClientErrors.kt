package no.elhub.auth.features.businessprocesses.structuredata.common

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

sealed class ClientError {
    data class UnexpectedError(val cause: Throwable) : ClientError()
    data object NotFound : ClientError()
    data object Unauthorized : ClientError()
    data object ServerError : ClientError()
    data object BadRequest : ClientError()
}

suspend fun mapErrorsFromServer(response: HttpResponse): ClientError {
    val errorResponse = parseError(response)
    return when (errorResponse?.status) {
        "400" -> ClientError.BadRequest
        "401" -> ClientError.Unauthorized
        "404" -> ClientError.NotFound
        "500" -> ClientError.ServerError
        else -> ClientError.UnexpectedError(ClientRequestException(response, response.bodyAsText()))
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

suspend fun parseError(response: HttpResponse): JsonApiErrorObject? {
    val text = response.bodyAsText()
    val doc = json.decodeFromString<JsonApiErrorCollection>(text)
    return doc.errors.firstOrNull()
}
