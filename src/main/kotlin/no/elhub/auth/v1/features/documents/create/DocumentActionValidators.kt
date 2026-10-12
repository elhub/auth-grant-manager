package no.elhub.auth.v1.features.documents.create

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.v1.InputError
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.features.documents.create.dto.RequestedScope
import kotlin.time.Instant

class ChangeOfEnergySupplierDocumentActionValidator {
    fun validate(scopes: List<RequestedScope>): DocumentAction.ChangeOfEnergySupplierForOrganization {
        val scope = scopes.requireSingleScope()
        scope.validateResourceType()
        if (scope.allowedChanges.isNotEmpty()) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes[0].allowedChanges is not supported for ChangeOfEnergySupplierForOrganization",
            )
        }

        return DocumentAction.ChangeOfEnergySupplierForOrganization(
            meteringPointIds = scope.toMeteringPointConstraint(),
        )
    }
}

class MoveInAndChangeOfEnergySupplierDocumentActionValidator {
    fun validate(scopes: List<RequestedScope>): DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization {
        val scope = scopes.requireSingleScope()
        scope.validateResourceType()
        val meteringPoints = scope.toMeteringPointConstraint()
        val validFrom = scope.validFrom()

        return DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = meteringPoints,
            validFrom = validFrom,
        )
    }
}

private fun List<RequestedScope>.requireSingleScope(): RequestedScope {
    if (size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes must contain exactly one scope for this document type",
        )
    }
    return single()
}

private fun RequestedScope.validateResourceType() {
    if (resourceType != ResourceType.MeteringPointContract.name) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].resourceType must be '${ResourceType.MeteringPointContract.name}'",
        )
    }
}

private fun RequestedScope.validFrom(): LocalDate {
    val validFromConstraints = allowedChanges.filter {
        it.attribute == AuthorizationScopeConstraintAttribute.ValidFrom.apiName
    }
    val contractTypeConstraints = allowedChanges.filter {
        it.attribute == AuthorizationScopeConstraintAttribute.ContractType.apiName
    }
    if (validFromConstraints.size != 1 || allowedChanges.size != 2 || contractTypeConstraints.size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].allowedChanges must contain exactly one type and one validFrom constraint",
        )
    }

    val contractTypes = contractTypeConstraints.single().value
    val expectedContractTypes = setOf("EnergySupplier", "EndUser")
    if (contractTypes.toSet() != expectedContractTypes || contractTypes.size != expectedContractTypes.size) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].allowedChanges type must contain exactly [EndUser, EnergySupplier]",
        )
    }

    val values = validFromConstraints.single().value
    if (values.size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].allowedChanges validFrom must contain exactly one date",
        )
    }
    return try {
        LocalDate.parse(values.single())
    } catch (_: IllegalArgumentException) {
        try {
            Instant.parse(values.single()).toLocalDateTime(TimeZone.of("Europe/Oslo")).date
        } catch (_: IllegalArgumentException) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes[0].allowedChanges validFrom must be an ISO date or timestamp",
            )
        }
    }
}

private fun RequestedScope.toMeteringPointConstraint(): ResourceConstraint.MeteringPoints {
    val meteringPointConstraints = appliesTo.filter {
        it.attribute == AuthorizationScopeConstraintAttribute.MeteringPointId.apiName
    }
    if (meteringPointConstraints.size != 1 || appliesTo.size != 1) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo must contain exactly one meteringPoint.id constraint",
        )
    }

    val meteringPointIds = try {
        meteringPointConstraints.single().value.mapTo(mutableSetOf(), MeteringPointId::create)
    } catch (_: InputError.InvalidMeteringPointId) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo meteringPoint.id values must be valid 18-digit metering-point IDs",
        )
    }

    if (meteringPointIds.isEmpty()) {
        throw InputError.InvalidCreateAuthorizationDocumentPayload(
            "requestedScopes[0].appliesTo meteringPoint.id must contain at least one metering point",
        )
    }

    return ResourceConstraint.MeteringPoints(meteringPointIds)
}
