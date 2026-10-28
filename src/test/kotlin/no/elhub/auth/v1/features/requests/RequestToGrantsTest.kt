package no.elhub.auth.v1.features.requests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import kotlin.time.Instant

// Requests are built with AuthorizationRequest.create, so these tests also pin the scope shape toGrants relies on.
class RequestToGrantsTest : FunSpec({
    val requestedBy = AuthorizationParty("7080005053246", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888777", PartyType.Organization)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val approvedBy = requestedOf
    val firstMeteringPoint = MeteringPointId.create("707057500000000001")
    val secondMeteringPoint = MeteringPointId.create("707057500000000002")

    // 11:00 in Oslo (CET, UTC+1). Start of today in Oslo is 2026-10-25T23:00:00Z.
    val now = Instant.parse("2026-10-26T10:00:00Z")
    val startOfToday = Instant.parse("2026-10-25T23:00:00Z")
    // 2028-10-26 is still summer time in Oslo (UTC+2), so 23:59:59 Oslo time is 21:59:59Z.
    val endOfTodayInTwoYears = Instant.parse("2028-10-26T21:59:59Z")

    fun request(details: AuthorizationRequestTypeDetails) = AuthorizationRequest.create(
        requestedBy = requestedBy,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        details = details,
        validTo = Instant.parse("2026-11-23T10:00:00Z"),
        meta = JsonObject(emptyMap()),
        externalReference = null,
        redirectUri = null,
    )

    fun appliesTo(meteringPointId: MeteringPointId) = AuthorizationScopeConstraint(
        constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
        attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
        value = ResourceConstraint.MeteringPoints(setOf(meteringPointId)),
    )

    fun allowedContractTypes(vararg types: MeteringPointContractType) = AuthorizationScopeConstraint(
        constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
        attribute = AuthorizationScopeConstraintAttribute.ContractType,
        value = ResourceConstraint.ContractTypes(types.toSet()),
    )

    fun writeMeteringPointContract(vararg constraints: AuthorizationScopeConstraint) = listOf(
        GrantedAuthorizationScope(
            capability = PermissionCapability.Write,
            resourceType = ResourceType.MeteringPointContract,
            constraints = constraints.toList(),
        ),
    )

    fun AuthorizationGrant.shouldBeIssuedFrom(request: AuthorizationRequest) {
        status shouldBe AuthorizationGrantStatus.Active
        sourceType shouldBe AuthorizationGrantSourceType.Request
        sourceId shouldBe request.id
        principal shouldBe requestedPrincipal
        grantedBy shouldBe requestedOf
        grantedTo shouldBe requestedBy
        approvedBy shouldBe requestedOf
    }

    context("ChangeOfEnergySupplierForOrganization") {
        val request = request(
            AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(
                ResourceConstraint.MeteringPoints(setOf(firstMeteringPoint, secondMeteringPoint)),
            ),
        )

        test("creates one consumable EnergySupplier grant per metering point, valid from today until end of day in two years") {
            val grants = request.toGrants(approvedBy, now)

            grants.forEach { grant ->
                grant.shouldBeIssuedFrom(request)
                grant.type shouldBe AuthorizationGrantType.Consumable
                grant.validFrom shouldBe startOfToday
                grant.validTo shouldBe endOfTodayInTwoYears
            }
            grants.map { it.scopes } shouldContainExactlyInAnyOrder listOf(
                writeMeteringPointContract(
                    appliesTo(firstMeteringPoint),
                    allowedContractTypes(MeteringPointContractType.EnergySupplier),
                ),
                writeMeteringPointContract(
                    appliesTo(secondMeteringPoint),
                    allowedContractTypes(MeteringPointContractType.EnergySupplier),
                ),
            )
        }
    }

    context("MoveInAndChangeOfEnergySupplierForOrganization") {
        val request = request(
            AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(
                ResourceConstraint.MeteringPoints(setOf(firstMeteringPoint, secondMeteringPoint)),
                LocalDate(2026, 11, 1),
            ),
        )
        val moveInDate = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
            value = ResourceConstraint.ValidFrom(Instant.parse("2026-10-31T23:00:00Z")),
        )

        test("creates one consumable EndUser and EnergySupplier grant per metering point with the move-in date") {
            val grants = request.toGrants(approvedBy, now)

            grants.forEach { grant ->
                grant.shouldBeIssuedFrom(request)
                grant.type shouldBe AuthorizationGrantType.Consumable
                grant.validFrom shouldBe startOfToday
                grant.validTo shouldBe endOfTodayInTwoYears
            }
            grants.map { it.scopes } shouldContainExactlyInAnyOrder listOf(
                writeMeteringPointContract(
                    appliesTo(firstMeteringPoint),
                    allowedContractTypes(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
                    moveInDate,
                ),
                writeMeteringPointContract(
                    appliesTo(secondMeteringPoint),
                    allowedContractTypes(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
                    moveInDate,
                ),
            )
        }
    }

    context("EnergySupplierFrameworkAgreement") {
        fun frameworkRequest(validFrom: Instant?, validTo: Instant?) = request(
            AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(validFrom, validTo),
        )

        test("creates one persistent EndUser and EnergySupplier grant covering all metering points") {
            val request = frameworkRequest(null, null)

            val grant = request.toGrants(approvedBy, now).single()

            grant.shouldBeIssuedFrom(request)
            grant.type shouldBe AuthorizationGrantType.Persistent
            grant.scopes shouldBe writeMeteringPointContract(
                allowedContractTypes(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
            )
        }

        val past = Instant.parse("2026-10-01T00:00:00Z")
        val earlierToday = Instant.parse("2026-10-26T05:00:00Z")
        val laterToday = Instant.parse("2026-10-26T15:00:00Z")
        val future = Instant.parse("2026-12-01T00:00:00Z")
        val laterFuture = Instant.parse("2027-12-01T00:00:00Z")

        listOf(
            Triple(null, null, startOfToday to null),
            Triple(null, future, startOfToday to future),
            Triple(past, null, startOfToday to null),
            Triple(past, future, startOfToday to future),
            Triple(startOfToday, null, startOfToday to null),
            Triple(earlierToday, null, earlierToday to null),
            Triple(laterToday, null, laterToday to null),
            Triple(future, null, future to null),
            Triple(future, laterFuture, future to laterFuture),
            Triple(future, future, future to future),
            Triple(null, laterToday, startOfToday to laterToday),
        ).forEach { (requestedFrom, requestedTo, expected) ->
            test("requested $requestedFrom..$requestedTo gives grant ${expected.first}..${expected.second}") {
                val grant = frameworkRequest(requestedFrom, requestedTo).toGrants(approvedBy, now).single()

                grant.validFrom shouldBe expected.first
                grant.validTo shouldBe expected.second
            }
        }

        listOf(
            null to past,
            past to past,
            null to earlierToday,
            null to now,
            laterFuture to future,
        ).forEach { (requestedFrom, requestedTo) ->
            test("rejects requested $requestedFrom..$requestedTo") {
                shouldThrow<UpdateRequestError.InvalidGrantValidity> {
                    frameworkRequest(requestedFrom, requestedTo).toGrants(approvedBy, now)
                }
            }
        }
    }
})
