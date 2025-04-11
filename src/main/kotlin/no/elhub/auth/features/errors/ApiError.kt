package no.elhub.auth.features.errors

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed class ApiError {
    abstract val status: Int
    abstract val title: String
    abstract val detail: String

    @Serializable
    data class BadRequest(
        @EncodeDefault
        override val status: Int = HttpStatusCode.Companion.BadRequest.value,
        @EncodeDefault
        override val title: String = "Bad Request",
        override val detail: String,
    ) : ApiError()

    data class Unauhthorized(
        @EncodeDefault
        override val status: Int = HttpStatusCode.Companion.Unauthorized.value,
        @EncodeDefault
        override val title: String = "Unauthorized",
        override val detail: String,
    ) : ApiError()

    data class Forbidden(
        @EncodeDefault
        override val status: Int = HttpStatusCode.Companion.Forbidden.value,
        @EncodeDefault
        override val title: String = "Forbidden",
        override val detail: String,
    ) : ApiError()

    data class NotFound(
        @EncodeDefault
        override val status: Int = HttpStatusCode.Companion.NotFound.value,
        @EncodeDefault
        override val title: String = "Not Found",
        override val detail: String,
    ) : ApiError()

    data class InternalServerError(
        @EncodeDefault
        override val status: Int = HttpStatusCode.Companion.BadRequest.value,
        @EncodeDefault
        override val title: String = "Internal Server Error",
        override val detail: String,
    ) : ApiError()
}
