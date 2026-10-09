package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v1.InputError
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.features.documents.create.dto.JsonApiCreateAuthorizationDocumentRequest
import kotlin.time.Instant

class CreateAuthorizationDocumentPayloadValidator {
    fun validate(
        request: JsonApiCreateAuthorizationDocumentRequest,
    ): RequestedAuthorizationScope {
        val documentType = request.data.attributes.documentType
        val scopes = request.data.attributes.requestedScopes
        if (scopes.isEmpty()) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes must contain at least one scope",
            )
        }

        if (scopes.size != 1) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "Only one requested scope is currently supported for this document type",
            )
        }

        val scope = scopes.single()
        if (scope.resourceType != "MeteringPointContract") {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes.resourceType must be MeteringPointContract",
            )
        }

        val meteringPointConstraints = scope.appliesTo.filter { it.attribute == "meteringPoint.id" }
        if (meteringPointConstraints.size != scope.appliesTo.size) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes.appliesTo contains an unsupported attribute",
            )
        }

        val meteringPointIds = try {
            meteringPointConstraints.flatMap { it.value }.mapTo(mutableSetOf(), MeteringPointId::create)
        } catch (_: InputError.InvalidMeteringPointId) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes.appliesTo.meteringPoint.id must contain only valid 18-digit metering-point IDs",
            )
        }

        if (meteringPointIds.isEmpty()) {
            throw InputError.InvalidCreateAuthorizationDocumentPayload(
                "requestedScopes.appliesTo must contain at least one metering point",
            )
        }

        val appliesTo = ResourceConstraint.MeteringPoints(meteringPointIds)

        val constraints = mutableListOf(
            AuthorizationScopeConstraint(
                constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                value = appliesTo,
            ),
        )
        when (documentType) {
            AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization ->
                Unit

            AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization -> {
                val validFromConstraints = scope.allowedChanges.filter { it.attribute == "validFrom" }
                if (validFromConstraints.size != scope.allowedChanges.size ||
                    validFromConstraints.flatMap { it.value }.size != 1
                ) {
                    throw InputError.InvalidCreateAuthorizationDocumentPayload(
                        "requestedScopes.allowedChanges.validFrom must contain exactly one date-time",
                    )
                }

                val validFrom = try {
                    Instant.parse(validFromConstraints.single().value.single())
                } catch (_: IllegalArgumentException) {
                    throw InputError.InvalidCreateAuthorizationDocumentPayload(
                        "requestedScopes.allowedChanges.validFrom must contain a valid date-time",
                    )
                }

                constraints += AuthorizationScopeConstraint(
                    constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                    attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
                    value = ResourceConstraint.ValidFrom(validFrom),
                )
            }
        }

        return RequestedAuthorizationScope(
            resourceType = ResourceType.MeteringPointContract,
            constraints = constraints,
        )
    }
}
