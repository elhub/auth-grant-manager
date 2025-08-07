package no.elhub.auth.features.errors

import io.ktor.http.HttpStatusCode
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

internal fun mapErrorToResponse(err: DomainError): JsonApiErrorObject {
    return when(err) {

        DomainError.ApiError.AuthorizationIdIsMalformed ->  JsonApiErrorObject(
            status = HttpStatusCode.BadRequest.value.toString(),
            title = "Malformed ID",
            detail = "The provided ID is not valid"
        )

        DomainError.ApiError.AuthorizationIdIsMissing ->  JsonApiErrorObject(
            status = HttpStatusCode.BadRequest.value.toString(),
            title = "Missing ID",
            detail = "Authorization ID is required"
        )

        is DomainError.ApiError.AuthorizationPayloadInvalid -> JsonApiErrorObject(
            status = HttpStatusCode.BadRequest.value.toString(),
            title = "Payload not valid",
            detail = "Authorization request contains extra, unknown, or missing fields."
        )

        DomainError.RepositoryError.AuthorizationNotCreated -> JsonApiErrorObject(
            status = HttpStatusCode.InternalServerError.value.toString(),
            title =  "Creation failed",
            detail = "Could not create authorization request"
        )

        DomainError.RepositoryError.AuthorizationNotFound -> JsonApiErrorObject(
            status = HttpStatusCode.NotFound.value.toString(),
            title = "Authorization not found",
            detail = "The requested authorization was not found"
        )

        is DomainError.RepositoryError.Unexpected -> JsonApiErrorObject(
            status = HttpStatusCode.InternalServerError.value.toString(),
            title = "Unexpected error",
            detail = err.exception.localizedMessage ?: "Unexpected error occurred."
        )
    }
}
