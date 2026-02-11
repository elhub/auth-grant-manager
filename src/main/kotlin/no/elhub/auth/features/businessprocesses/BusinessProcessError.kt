package no.elhub.auth.features.businessprocesses

sealed interface BusinessProcessError {
    val kind: Kind
    val detail: String

    enum class Kind {
        VALIDATION,
        UNEXPECTED_ERROR,
    }

    data class Validation(
        override val detail: String,
    ) : BusinessProcessError {
        override val kind = Kind.VALIDATION
    }

    data class Unexpected(
        override val detail: String,
    ) : BusinessProcessError {
        override val kind = Kind.UNEXPECTED_ERROR
    }
}
