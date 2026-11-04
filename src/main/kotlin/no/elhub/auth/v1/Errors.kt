package no.elhub.auth.v1

abstract class Errors(
    open val detail: String? = null,
) : RuntimeException(detail)
