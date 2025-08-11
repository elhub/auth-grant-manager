package no.elhub.auth.features.errors

import io.ktor.http.HttpStatusCode
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

internal fun mapErrorToResponse(err: DomainError): JsonApiErrorObject = when (err) {
    is DomainError.ApiError -> handleApiError(err)
    is DomainError.RepositoryError -> handleRepositoryError(err)
}

fun handleApiError(err: DomainError.ApiError): JsonApiErrorObject = when (err) {
    DomainError.ApiError.AuthorizationIdIsMalformed -> JsonApiErrorObject(
        status = HttpStatusCode.BadRequest.value.toString(),
        code = "INVALID_RESOURCE_ID",
        title = "Malformed ID",
        detail = "The provided ID is not valid"
    )

    is DomainError.ApiError.AuthorizationPayloadInvalid -> JsonApiErrorObject(
        status = HttpStatusCode.BadRequest.value.toString(),
        code = "INVALID_PAYLOAD",
        title = "Payload not valid",
        detail = "Authorization request contains extra, unknown, or missing fields"
    )

    DomainError.ApiError.AuthorizationRequestTypeIsInvalid -> JsonApiErrorObject(
        status = HttpStatusCode.BadRequest.value.toString(),
        code = "INVALID_REQUEST_TYPE",
        title = "Invalid request type",
        detail = "The request type should be ChangeOfSupplierConfirmation"
    )
}

fun handleRepositoryError(err: DomainError.RepositoryError): JsonApiErrorObject {
    when (err) {
        DomainError.RepositoryError.AuthorizationNotCreated -> return JsonApiErrorObject(
            status = HttpStatusCode.InternalServerError.value.toString(),
            code = "NOT_CREATED",
            title = "Creation failed",
            detail = "Could not create authorization request"
        )

        DomainError.RepositoryError.AuthorizationNotFound -> return JsonApiErrorObject(
            status = HttpStatusCode.NotFound.value.toString(),
            code = "NOT_FOUND",
            title = "Authorization not found",
            detail = "The authorization was not found"
        )

        is DomainError.RepositoryError.Unexpected -> return JsonApiErrorObject(
            status = HttpStatusCode.InternalServerError.value.toString(),
            code = "INTERNAL_SERVER_ERROR",
            title = "Unexpected error",
            detail = err.exception.localizedMessage ?: "Unexpected error occurred"
        )
    }
}
