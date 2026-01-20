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

fun buildErrorResponse(status: HttpStatusCode, code: String, title: String, detail: String) =
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

fun toDeserializationErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = buildErrorResponse(
    status = HttpStatusCode.BadRequest,
    code = "invalid_request_body",
    title = "Invalid Request body",
    detail = "Request body could not be parsed or did not match the expected schema"
)

fun toForbiddenBalanceSupplierOnlyResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = buildErrorResponse(
    status = HttpStatusCode.Forbidden,
    code = "forbidden_request",
    title = "Forbidden Request",
    detail = "You do not have permission to create an authorization request"
)

fun InputError.toInputErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        InputError.MissingInputError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "missing_input",
            title = "Missing input",
            detail = "Necessary information was not provided",
        )

        InputError.MalformedInputError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_input",
            title = "Invalid Input",
            detail = "The provided payload did not satisfy the expected format"
        )
    }

fun QueryError.toQueryErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        QueryError.ResourceNotFoundError -> buildErrorResponse(
            status = HttpStatusCode.NotFound,
            code = "not_found",
            title = "Not Found",
            detail = "The requested resource could not be found",
        )

        QueryError.IOError -> buildErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_error",
            title = "Internal Server Error",
            detail = "An error occurred when attempted to perform the query",
        )

        QueryError.NotAuthorizedError -> buildErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "not_authorized",
            title = "Party Not Authorized",
            detail = "The party is not allowed to access this resource",
        )
    }
