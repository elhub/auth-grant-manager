package no.elhub.auth.v1.api.requests.request

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.requests.AuthorizationRequestSpecification
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import java.time.OffsetDateTime
import kotlin.time.Instant

class AuthorizationRequestSpecificationMapperTest : FunSpec({
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val validMeteringPointId = "707057500000000001"
    val validFrom = OffsetDateTime.parse("2026-02-01T00:00:00+01:00")
    val validTo = OffsetDateTime.parse("2027-02-01T00:00:00+01:00")

    fun meteringPointScope(allowedChanges: List<ScopeConstraint> = emptyList()) = RequestedScope(
        resourceType = "MeteringPointContract",
        appliesTo = listOf(ScopeConstraint("meteringPoint.id", listOf(validMeteringPointId))),
        allowedChanges = allowedChanges,
    )

    fun attributes(
        requestType: AuthorizationRequestType,
        requestedScopes: List<RequestedScope>? = null,
        requestedGrantValidFrom: OffsetDateTime? = null,
        requestedGrantValidTo: OffsetDateTime? = null,
    ) = CreateAuthorizationRequestAttributes(
        requestType = requestType,
        requestedScopes = requestedScopes,
        externalReference = "ref-1",
        requestedGrantValidFrom = requestedGrantValidFrom,
        requestedGrantValidTo = requestedGrantValidTo,
        redirectUri = "https://example.com/callback",
    )

    fun invalidPayload(expectedDetail: String, block: () -> Unit) {
        shouldThrow<InputError.InvalidCreateAuthorizationRequestPayload>(block).detail shouldBe expectedDetail
    }

    context("EnergySupplierFrameworkAgreement") {
        val type = AuthorizationRequestType.EnergySupplierFrameworkAgreement

        test("maps requested grant validity to its typed specification") {
            val specification = attributes(type, requestedGrantValidFrom = validFrom, requestedGrantValidTo = validTo)
                .validateAndReturnAuthorizationRequestSpecification(now)

            specification shouldBe AuthorizationRequestSpecification(
                externalReference = "ref-1",
                redirectUri = "https://example.com/callback",
                details = AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(
                    requestedGrantValidFrom = Instant.parse("2026-01-31T23:00:00Z"),
                    requestedGrantValidTo = Instant.parse("2027-01-31T23:00:00Z"),
                ),
            )
        }

        test("allows omitted requested grant validity") {
            attributes(type).validateAndReturnAuthorizationRequestSpecification(now).details shouldBe
                AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(null, null)
        }

        test("rejects requested scopes") {
            invalidPayload("requestedScopes is not supported for EnergySupplierFrameworkAgreement") {
                attributes(type, requestedScopes = listOf(meteringPointScope()))
                    .validateAndReturnAuthorizationRequestSpecification(now)
            }
        }

        test("requires requested grant validity to be in the future") {
            invalidPayload("requestedGrantValidFrom must be in the future") {
                attributes(type, requestedGrantValidFrom = OffsetDateTime.parse("2025-12-31T00:00:00Z"))
                    .validateAndReturnAuthorizationRequestSpecification(now)
            }
            invalidPayload("requestedGrantValidTo must be in the future") {
                attributes(type, requestedGrantValidTo = OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                    .validateAndReturnAuthorizationRequestSpecification(now)
            }
        }

        test("requires requestedGrantValidFrom to be before requestedGrantValidTo") {
            invalidPayload("requestedGrantValidFrom must be before requestedGrantValidTo") {
                attributes(type, requestedGrantValidFrom = validTo, requestedGrantValidTo = validFrom)
                    .validateAndReturnAuthorizationRequestSpecification(now)
            }
            invalidPayload("requestedGrantValidFrom must be before requestedGrantValidTo") {
                attributes(type, requestedGrantValidFrom = validFrom, requestedGrantValidTo = validFrom)
                    .validateAndReturnAuthorizationRequestSpecification(now)
            }
        }
    }

    context("ChangeOfEnergySupplierForOrganization") {
        val type = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization

        test("maps metering points to its typed specification") {
            attributes(type, requestedScopes = listOf(meteringPointScope()))
                .validateAndReturnAuthorizationRequestSpecification(now)
                .details shouldBe AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(
                ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create(validMeteringPointId))),
            )
        }

        test("rejects requested grant validity") {
            invalidPayload(
                "requestedGrantValidFrom and requestedGrantValidTo are not supported for $type",
            ) {
                attributes(type, listOf(meteringPointScope()), requestedGrantValidFrom = validFrom)
                    .validateAndReturnAuthorizationRequestSpecification(now)
            }
        }

        test("requires requested scopes") {
            invalidPayload("requestedScopes is required for $type") {
                attributes(type).validateAndReturnAuthorizationRequestSpecification(now)
            }
        }
    }

    context("MoveInAndChangeOfEnergySupplierForOrganization") {
        val type = AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization
        val moveInScope = meteringPointScope(listOf(ScopeConstraint("validFrom", listOf("2026-10-01"))))

        test("maps metering points and move-in date to its typed specification") {
            attributes(type, requestedScopes = listOf(moveInScope))
                .validateAndReturnAuthorizationRequestSpecification(now)
                .details shouldBe AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create(validMeteringPointId))),
                moveInDate = LocalDate(2026, 10, 1),
            )
        }

        test("rejects requested grant validity") {
            invalidPayload(
                "requestedGrantValidFrom and requestedGrantValidTo are not supported for $type",
            ) {
                attributes(type, listOf(moveInScope), requestedGrantValidTo = validTo)
                    .validateAndReturnAuthorizationRequestSpecification(now)
            }
        }
    }
})
