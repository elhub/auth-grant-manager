package no.elhub.auth.v1.api.requests.request

import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.common.toOsloDateOrNull
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.DomainError
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.domain.requests.AuthorizationRequestSpecification
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Instant

fun CreateAuthorizationRequestAttributes.validateAndReturnAuthorizationRequestSpecification(
    now: Instant = now(),
): AuthorizationRequestSpecification =
    AuthorizationRequestSpecification(
        externalReference = externalReference,
        redirectUri = redirectUri?.let(::requireValidRedirectUri),
        details = validateAndReturnTypeDetails(now),
    )

private fun requireValidRedirectUri(value: String): String {
    val trimmed = value.trim()
    val uri = try {
        URI(trimmed)
    } catch (_: URISyntaxException) {
        invalid("redirectUri must be a valid URI")
    }
    if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) {
        invalid("redirectUri must use the https scheme")
    }
    if (uri.host.isNullOrBlank()) {
        invalid("redirectUri must contain a host")
    }
    if (uri.rawUserInfo != null) {
        invalid("redirectUri must not contain user info")
    }
    if (uri.rawFragment != null) {
        invalid("redirectUri must not contain a fragment")
    }
    return trimmed
}

private fun CreateAuthorizationRequestAttributes.validateAndReturnTypeDetails(
    now: Instant,
): AuthorizationRequestTypeDetails =
    when (requestType) {
        AuthorizationRequestType.ChangeOfEnergySupplierForOrganization -> {
            rejectRequestedGrantValidity()
            val scope = requireRequestedScopes()
                .requireSingleScope()
                .requireResourceType(ResourceType.MeteringPointContract)
                .rejectAllowedChanges()

            AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(
                meteringPointIds = scope.requireMeteringPointIds(),
            )
        }

        AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization -> {
            rejectRequestedGrantValidity()
            val scope = requireRequestedScopes()
                .requireSingleScope()
                .requireResourceType(ResourceType.MeteringPointContract)

            AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = scope.requireMeteringPointIds(),
                moveInDate = scope.requireMoveInDate(),
            )
        }

        AuthorizationRequestType.EnergySupplierFrameworkAgreement -> {
            rejectRequestedScopes()
            requireRequestedGrantValidityInFuture(now)
            requireRequestedGrantValidFromBeforeValidTo()

            AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(
                requestedGrantValidFrom = requestedGrantValidFrom,
                requestedGrantValidTo = requestedGrantValidTo,
            )
        }
    }

private fun CreateAuthorizationRequestAttributes.requireRequestedScopes(): List<RequestedScope> =
    requestedScopes ?: invalid("requestedScopes is required for $requestType")

private fun CreateAuthorizationRequestAttributes.rejectRequestedScopes() {
    if (requestedScopes != null) {
        invalid("requestedScopes is not supported for $requestType")
    }
}

private fun CreateAuthorizationRequestAttributes.rejectRequestedGrantValidity() {
    if (requestedGrantValidFrom != null || requestedGrantValidTo != null) {
        invalid("requestedGrantValidFrom and requestedGrantValidTo are not supported for $requestType")
    }
}

private fun CreateAuthorizationRequestAttributes.requireRequestedGrantValidityInFuture(now: Instant) {
    if (requestedGrantValidFrom != null && requestedGrantValidFrom <= now) {
        invalid("requestedGrantValidFrom must be in the future")
    }
    if (requestedGrantValidTo != null && requestedGrantValidTo <= now) {
        invalid("requestedGrantValidTo must be in the future")
    }
}

private fun CreateAuthorizationRequestAttributes.requireRequestedGrantValidFromBeforeValidTo() {
    if (requestedGrantValidFrom != null &&
        requestedGrantValidTo != null &&
        requestedGrantValidFrom >= requestedGrantValidTo
    ) {
        invalid("requestedGrantValidFrom must be before requestedGrantValidTo")
    }
}

private fun List<RequestedScope>.requireSingleScope(): RequestedScope =
    singleOrNull() ?: invalid("requestedScopes must contain exactly one scope")

private fun RequestedScope.requireResourceType(expected: ResourceType): RequestedScope = apply {
    if (resourceType != expected.name) {
        invalid("requestedScopes[0].resourceType must be '${expected.name}'")
    }
}

private fun RequestedScope.rejectAllowedChanges(): RequestedScope = apply {
    if (allowedChanges.isNotEmpty()) {
        invalid("requestedScopes[0].allowedChanges is not supported for this request type")
    }
}

private fun RequestedScope.requireMeteringPointIds(): ResourceConstraint.MeteringPoints {
    val constraint = appliesTo.singleOrNull()
        ?.takeIf { it.attribute == AuthorizationScopeConstraintAttribute.MeteringPointId.apiName }
        ?: invalid("requestedScopes[0].appliesTo must contain exactly one meteringPoint.id constraint")
    if (constraint.value.isEmpty()) {
        invalid("requestedScopes[0].appliesTo meteringPoint.id must contain at least one metering point")
    }
    val meteringPointIds = constraint.value.mapTo(mutableSetOf()) {
        it.toMeteringPointIdOrNull()
            ?: invalid("requestedScopes[0].appliesTo meteringPoint.id values must be valid 18-digit metering-point IDs")
    }
    return ResourceConstraint.MeteringPoints(meteringPointIds)
}

private fun RequestedScope.requireMoveInDate(): LocalDate {
    val constraint = allowedChanges.singleOrNull()
        ?.takeIf { it.attribute == AuthorizationScopeConstraintAttribute.ValidFrom.apiName }
        ?: invalid("requestedScopes[0].allowedChanges must contain exactly one validFrom constraint")
    val value = constraint.value.singleOrNull()
        ?: invalid("requestedScopes[0].allowedChanges validFrom must contain exactly one date")
    return value.toOsloDateOrNull()
        ?: invalid("requestedScopes[0].allowedChanges validFrom must be an ISO date or timestamp")
}

private fun String.toMeteringPointIdOrNull(): MeteringPointId? =
    try {
        MeteringPointId.create(this)
    } catch (_: DomainError.InvalidMeteringPointId) {
        null
    }

private fun invalid(detail: String): Nothing =
    throw InputError.InvalidCreateAuthorizationRequestPayload(detail)
