package no.elhub.auth.features.common

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonPrimitive
import no.elhub.devxp.jsonapi.model.JsonApiMeta
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

fun buildApiErrorResponse(status: HttpStatusCode, title: String, detail: String) =
    status to JsonApiErrorCollection(
        errors = listOf(
            JsonApiErrorObject(
                status = status.value.toString(),
                title = title,
                detail = detail
            )
        ),
        meta = JsonApiMeta(
            mapOf(
                "createdAt" to JsonPrimitive(currentTimeWithTimeZone().toString())
            )
        )
    )

fun toInternalServerApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = buildApiErrorResponse(
    status = HttpStatusCode.InternalServerError,
    title = "Internal server error",
    detail = "An internal server error occurred",
)

fun toValidationApiErrorResponse(detail: String? = null): Pair<HttpStatusCode, JsonApiErrorCollection> = buildApiErrorResponse(
    status = HttpStatusCode.BadRequest,
    title = "Validation error",
    detail = detail.orEmpty().ifEmpty({ "The requested resource could not be found" })
)

fun toNotFoundApiErrorResponse(detail: String? = null): Pair<HttpStatusCode, JsonApiErrorCollection> = buildApiErrorResponse(
    status = HttpStatusCode.NotFound,
    title = "Not found error",
    detail = detail.orEmpty().ifEmpty({ "The requested resource could not be found" })
)

fun toDeserializationApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = buildApiErrorResponse(
    status = HttpStatusCode.BadRequest,
    title = "Invalid request body",
    detail = "Request body could not be parsed or did not match the expected schema"
)

fun toBalanceSupplierNotApiAuthorizedResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = buildApiErrorResponse(
    status = HttpStatusCode.Forbidden,
    title = "Not authorized",
    detail = "Only balance suppliers are authorized to access this endpoint"
)

fun InputError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        InputError.MissingInputError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Missing input",
            detail = "Necessary information was not provided",
        )

        InputError.MalformedInputError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Invalid input",
            detail = "The provided payload did not satisfy the expected format"
        )
    }

fun QueryError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        QueryError.ResourceNotFoundError -> toNotFoundApiErrorResponse()

        QueryError.IOError -> toInternalServerApiErrorResponse()

        QueryError.NotAuthorizedError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Party not authorized",
            detail = "The party is not allowed to access this resource",
        )
    }
