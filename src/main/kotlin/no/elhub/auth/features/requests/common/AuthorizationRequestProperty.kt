package no.elhub.auth.features.requests.common

import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger(AuthorizationRequestProperty::class.java)

data class AuthorizationRequestProperty(
    val requestId: UUID,
    val key: String,
    val value: String
)

sealed class PropertyError {
    data class NotFound(val key: String) : PropertyError()
}

fun List<AuthorizationRequestProperty>.requireProperty(key: String): String =
    firstOrNull { it.key == key }?.value
        ?: run {
            logger.error("Required property '$key' not found for AuthorizationRequest")
            error("Required property '$key' not found for AuthorizationRequest")
        }
