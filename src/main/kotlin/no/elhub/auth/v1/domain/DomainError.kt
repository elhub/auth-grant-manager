package no.elhub.auth.v1.domain

import no.elhub.auth.v1.Errors

sealed class DomainError(override val detail: String) : Errors(detail) {
    class InvalidExternalAgentIdentifier(value: String) :
        DomainError("Invalid external agent identifier: '$value'")

    class InvalidMeteringPointId(value: String) :
        DomainError("Invalid metering-point ID: '$value'. Expected exactly 18 digits")

    class InvalidScopeConstraint(detail: String) : DomainError(detail)

    class UnsupportedScopeConstraintAttribute(attribute: String) :
        DomainError("Unsupported scope constraint attribute: $attribute")
}
