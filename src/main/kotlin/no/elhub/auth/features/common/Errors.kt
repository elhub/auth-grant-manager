package no.elhub.auth.features.common

import io.ktor.http.HttpStatusCode
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

interface Error

sealed class InputError : Error {
    data object MissingInputError : InputError()
    data object MalformedInputError : InputError()
}

sealed class CommandError : Error {
    data object ResourceNotFoundError : CommandError()
    data object IOError : CommandError()
}

sealed class QueryError : Error {
    data object ResourceNotFoundError : QueryError()
    data object IOError : QueryError()
    data object NotAuthorizedError : QueryError()
}

abstract class RepositoryError : Error

sealed class RepositoryWriteError : RepositoryError() {
    data object ConflictError : RepositoryWriteError()
    data object NotFoundError : RepositoryWriteError()
    data object UnexpectedError : RepositoryWriteError()
}

sealed class RepositoryReadError : RepositoryError() {
    data object NotFoundError : RepositoryReadError()
    data object UnexpectedError : RepositoryReadError()
}

fun buildApiErrorResponse(status: HttpStatusCode, code: String, title: String, detail: String) =
    status to JsonApiErrorCollection(
        listOf(
            JsonApiErrorObject(
                status = status.value.toString(),
                code = code,
                title = title,
                detail = detail
            )
        )
    )

fun toDeserializationApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = buildApiErrorResponse(
    status = HttpStatusCode.BadRequest,
    code = "invalid_request_body",
    title = "Invalid request body",
    detail = "Request body could not be parsed or did not match the expected schema"
)

fun toBalanceSupplierNotApiAuthorizedResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = buildApiErrorResponse(
    status = HttpStatusCode.Forbidden,
    code = "not_authorized",
    title="Not authorized",
    detail = "Only balance suppliers are authorized to access this endpoint"
)

fun InputError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        InputError.MissingInputError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "missing_input",
            title = "Missing input",
            detail = "Necessary information was not provided",
        )

        InputError.MalformedInputError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_input",
            title = "Invalid input",
            detail = "The provided payload did not satisfy the expected format"
        )
    }

fun QueryError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        QueryError.ResourceNotFoundError -> buildApiErrorResponse(
            status = HttpStatusCode.NotFound,
            code = "not_found",
            title = "Not found",
            detail = "The requested resource could not be found",
        )

        QueryError.IOError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_error",
            title = "Internal server error",
            detail = "An error occurred when attempted to perform the query",
        )

        QueryError.NotAuthorizedError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "not_authorized",
            title = "Party not authorized",
            detail = "The party is not allowed to access this resource",
        )
    }
