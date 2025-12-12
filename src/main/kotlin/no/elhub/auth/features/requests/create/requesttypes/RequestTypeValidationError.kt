package no.elhub.auth.features.requests.create.requesttypes

/**
 * Shared validation error marker for all request business processes.
 * Each process can declare its own reason as a subclass.
 */
interface RequestTypeValidationError {
    val code: String
    val message: String
}
