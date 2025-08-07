package no.elhub.auth.presentation.model.errors

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(ApiErrorSerializer::class)
sealed class ApiError {
    abstract val status: Int

    @Serializable
    data class BadRequest(
        override val status: Int = HttpStatusCode.Companion.BadRequest.value,
        val title: String = "Bad Request",
        val detail: String,
    ) : ApiError()

    @Serializable
    data class Unauthorized(
        override val status: Int = HttpStatusCode.Companion.Unauthorized.value,
        val title: String = "Unauthorized",
        val detail: String,
    ) : ApiError()

    @Serializable
    data class Forbidden(
        override val status: Int = HttpStatusCode.Companion.Forbidden.value,
        val title: String = "Forbidden",
        val detail: String,
    ) : ApiError()

    @Serializable
    data class NotFound(
        override val status: Int = HttpStatusCode.Companion.NotFound.value,
        val title: String = "Not Found",
        val detail: String,
    ) : ApiError()

    @Serializable
    data class InternalServerError(
        override val status: Int = HttpStatusCode.Companion.InternalServerError.value,
        val title: String = "Internal Server Error",
        val detail: String,
    ) : ApiError()
}

/** Custom serializer for [ApiError] to handle polymorphic serialization
 * of its subclasses. This is required, as kotlinx serialization does not
 * support polymorphic serialization out of the box.
 */
object ApiErrorSerializer : JsonContentPolymorphicSerializer<ApiError>(
    ApiError::class,
) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ApiError> {
        val json = element.jsonObject
        val type = json.getValue("type").jsonPrimitive.content
        return when (type) {
            "no.elhub.auth.features.errors.ApiError.BadRequest" -> ApiError.BadRequest.serializer()
            "no.elhub.auth.features.errors.ApiError.Unauthorized" -> ApiError.Unauthorized.serializer()
            "no.elhub.auth.features.errors.ApiError.Forbidden" -> ApiError.Forbidden.serializer()
            "no.elhub.auth.features.errors.ApiError.NotFound" -> ApiError.NotFound.serializer()
            "no.elhub.auth.features.errors.ApiError.InternalServerError" -> ApiError.InternalServerError.serializer()
            else -> throw IllegalArgumentException("$type is not a supported ApiError type.")
        }
    }
}
