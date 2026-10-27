package no.elhub.auth.v1.api.documents.request

import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.common.todayOslo
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.DomainError
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

fun toDocumentSpecification(
    documentType: AuthorizationDocumentType,
    requestedScopes: List<RequestedScope>? = null,
    requestedGrantValidFrom: OffsetDateTime? = null,
    requestedGrantValidTo: OffsetDateTime? = null,
    now: Instant = Clock.System.now(),
): DocumentSpecification {
    if (documentType != AuthorizationDocumentType.EnergySupplierFrameworkAgreement) {
        if (requestedGrantValidFrom != null || requestedGrantValidTo != null) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedGrantValidFrom and requestedGrantValidTo are not supported for $documentType",
            )
        }
        if (requestedScopes == null) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload("requestedScopes is required for $documentType")
        }
    }

    return when (documentType) {
        AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization ->
            requireNotNull(requestedScopes).toSupplierChangeSpecification()

        AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization ->
            requireNotNull(requestedScopes).toMoveInSpecification()

        AuthorizationDocumentType.EnergySupplierFrameworkAgreement -> {
            if (requestedScopes != null) {
                throw InputError.InvalidCreateAuthorizationDocumentPayload(
                    "requestedScopes is not supported for $documentType",
                )
            }
            val validFrom = requestedGrantValidFrom?.toInstant()?.toKotlinInstant()
            val validTo = requestedGrantValidTo?.toInstant()?.toKotlinInstant()
            if (validFrom != null && validFrom <= now) {
                throw InputError.InvalidCreateAuthorizationDocumentPayload("requestedGrantValidFrom must be in the future")
            }
            if (validTo != null && validTo <= now) {
                throw InputError.InvalidCreateAuthorizationDocumentPayload("requestedGrantValidTo must be in the future")
            }
            if (validFrom != null && validTo != null && validFrom >= validTo) {
                throw InputError.InvalidCreateAuthorizationDocumentPayload(
                    "requestedGrantValidFrom must be before requestedGrantValidTo",
                )
            }
            DocumentSpecification.EnergySupplierFrameworkAgreement(validFrom, validTo)
        }
    }
}

private fun List<RequestedScope>.toSupplierChangeSpecification(): DocumentSpecification.ChangeOfEnergySupplierForOrganization {
    if (size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes must contain exactly one scope for this document type",
        )
    }

    val scope = single()
    if (scope.resourceType != ResourceType.MeteringPointContract.name) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].resourceType must be '${ResourceType.MeteringPointContract.name}'",
        )
    }

    if (scope.allowedChanges.isNotEmpty()) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].allowedChanges is not supported for ChangeOfEnergySupplierForOrganization",
        )
    }

    val meteringPointConstraints = scope.appliesTo.filter {
        it.attribute == AuthorizationScopeConstraintAttribute.MeteringPointId.apiName
    }
    if (meteringPointConstraints.size != 1 || scope.appliesTo.size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo must contain exactly one meteringPoint.id constraint",
        )
    }

    val meteringPointIds = try {
        meteringPointConstraints.single().value.mapTo(mutableSetOf(), MeteringPointId::create)
    } catch (_: DomainError.InvalidMeteringPointId) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo meteringPoint.id values must be valid 18-digit metering-point IDs",
        )
    }

    if (meteringPointIds.isEmpty()) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo meteringPoint.id must contain at least one metering point",
        )
    }

    return DocumentSpecification.ChangeOfEnergySupplierForOrganization(
        meteringPointIds = ResourceConstraint.MeteringPoints(meteringPointIds),
    )
}

private fun List<RequestedScope>.toMoveInSpecification(): DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization {
    if (size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes must contain exactly one scope for this document type",
        )
    }

    val scope = single()
    if (scope.resourceType != ResourceType.MeteringPointContract.name) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].resourceType must be '${ResourceType.MeteringPointContract.name}'",
        )
    }

    val meteringPointConstraints = scope.appliesTo.filter {
        it.attribute == AuthorizationScopeConstraintAttribute.MeteringPointId.apiName
    }
    if (meteringPointConstraints.size != 1 || scope.appliesTo.size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo must contain exactly one meteringPoint.id constraint",
        )
    }

    val meteringPointIds = try {
        meteringPointConstraints.single().value.mapTo(mutableSetOf(), MeteringPointId::create)
    } catch (_: DomainError.InvalidMeteringPointId) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo meteringPoint.id values must be valid 18-digit metering-point IDs",
        )
    }

    if (meteringPointIds.isEmpty()) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo meteringPoint.id must contain at least one metering point",
        )
    }

    val validFromConstraints = scope.allowedChanges.filter {
        it.attribute == AuthorizationScopeConstraintAttribute.ValidFrom.apiName
    }
    if (validFromConstraints.size != 1 || scope.allowedChanges.size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].allowedChanges must contain exactly one validFrom constraint",
        )
    }

    val validFromValues = validFromConstraints.single().value
    if (validFromValues.size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].allowedChanges validFrom must contain exactly one date",
        )
    }

    val validFromValue = validFromValues.single()
    val validFrom = try {
        LocalDate.parse(validFromValue)
    } catch (_: IllegalArgumentException) {
        try {
            todayOslo(Instant.parse(validFromValue))
        } catch (_: IllegalArgumentException) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes[0].allowedChanges validFrom must be an ISO date or timestamp",
            )
        }
    }

    return DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization(
        meteringPointIds = ResourceConstraint.MeteringPoints(meteringPointIds),
        validFrom = validFrom,
    )
}
