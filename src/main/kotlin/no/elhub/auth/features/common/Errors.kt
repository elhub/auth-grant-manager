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

fun InputError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorObject> = when (this) {
    InputError.MissingInputError, InputError.MalformedInputError -> HttpStatusCode.BadRequest to JsonApiErrorObject(
        status = HttpStatusCode.BadRequest.toString(),
        code = "INVALID_RESOURCE_ID",
        title = "Malformed ID",
        detail = "The provided ID is not valid",
    )
}

fun QueryError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorObject> = when (this) {
    QueryError.ResourceNotFoundError -> HttpStatusCode.NotFound to JsonApiErrorObject(
        status = HttpStatusCode.NotFound.value.toString(),
        code = "NOT_FOUND",
        title = "Not Found",
        detail = "The requested resource could not be found",
    )

    QueryError.IOError -> HttpStatusCode.InternalServerError to JsonApiErrorObject(
        status = HttpStatusCode.InternalServerError.value.toString(),
        code = "INTERNAL_SERVER_ERROR",
        title = "Internal Server Error",
        detail = "An error occurred when attempted to perform the query",
    )
}
