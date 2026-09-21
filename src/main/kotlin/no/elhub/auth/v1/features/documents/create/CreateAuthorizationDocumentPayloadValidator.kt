package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.features.documents.create.dto.JsonApiCreateAuthorizationDocumentRequest

class CreateAuthorizationDocumentPayloadValidator {
    fun validate(
        request: JsonApiCreateAuthorizationDocumentRequest,
    ): RequestedScope {
        val documentType = request.data.attributes.documentType
        val scope = request.data.attributes.requestedScope

        val meteringPointIds = try {
            scope.appliesTo.meteringPointIds.mapTo(mutableSetOf(), MeteringPointId::create)
        } catch (_: Errors.InvalidMeteringPointId) {
            throw Errors.InvalidCreateAuthorizationDocumentPayload(
                "requestedScope.appliesTo.meteringPointIds must contain only valid 18-digit metering-point IDs",
            )
        }

        if (meteringPointIds.isEmpty()) {
            throw Errors.InvalidCreateAuthorizationDocumentPayload(
                "requestedScope.appliesTo.meteringPointIds must contain at least one metering point",
            )
        }

        val appliesTo = ResourceConstraint.MeteringPoints(meteringPointIds)

        val requestedScope = when (documentType) {
            AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization ->
                RequestedScope.ChangeOfEnergySupplierForOrganization(
                    meteringPointIds = appliesTo,
                )

            AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization -> {
                val allowedChanges = scope.allowedChanges
                    ?: throw Errors.InvalidCreateAuthorizationDocumentPayload(
                        "requestedScope.allowedChanges is required for MoveInAndChangeOfEnergySupplierForOrganization",
                    )

                if (allowedChanges.validFrom.size != 1) {
                    throw Errors.InvalidCreateAuthorizationDocumentPayload(
                        "requestedScope.allowedChanges.validFrom must contain exactly one date",
                    )
                }

                RequestedScope.MoveInAndChangeOfEnergySupplierForOrganization(
                    meteringPointIds = appliesTo,
                    validFrom = allowedChanges.validFrom.single(),
                )
            }
        }

        return requestedScope
    }
}
