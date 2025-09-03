package no.elhub.auth.features.common

import io.ktor.http.HttpStatusCode
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

abstract class Error

sealed class InputError : Error() {
    data object MissingInputError : InputError()
    data object MalformedInputError : InputError()
}

sealed class CommandError : Error() {
    data object ResourceAlreadyExistsError : CommandError()
    data object ResourceNotFoundError : CommandError()
    data object IOError : CommandError()
}

sealed class QueryError : Error() {
    data object ResourceNotFoundError : QueryError()
    data object IOError : QueryError()
}

abstract class RepositoryError : Error()

sealed class RepositoryWriteError : RepositoryError() {
    data object ConflictError : RepositoryWriteError()
    data object NotFoundError : RepositoryWriteError()
    data object UnexpectedError : RepositoryWriteError()
}

sealed class RepositoryReadError : RepositoryError() {
    data object NotFoundError : RepositoryReadError()
    data object UnexpectedError : RepositoryReadError()
}

fun InputError.toApiErrorResponse(): JsonApiErrorObject = when (this) {
    InputError.MissingInputError -> JsonApiErrorObject(
        status = HttpStatusCode.BadRequest.value.toString(),
        code = "INVALID_RESOURCE_ID",
        title = "Malformed ID",
        detail = "The provided ID is not valid"
    )

    is InputError.MalformedInputError -> JsonApiErrorObject(
        status = HttpStatusCode.BadRequest.value.toString(),
        code = "INVALID_PAYLOAD",
        title = "Payload not valid",
        detail = "Authorization request contains extra, unknown, or missing fields"
    )
}
