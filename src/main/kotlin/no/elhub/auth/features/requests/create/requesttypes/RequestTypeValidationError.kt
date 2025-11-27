package no.elhub.auth.features.requests.create.requesttypes

import kotlinx.serialization.Serializable

/**
 * Shared validation error marker for all request business processes.
 * Each process can declare its own reason as a subclass.
 */
@Serializable
abstract class RequestTypeValidationError {
    abstract val code: String
    abstract val message: String
}
