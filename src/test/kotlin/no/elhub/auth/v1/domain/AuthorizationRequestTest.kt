package no.elhub.auth.v1.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import kotlin.time.Instant

class AuthorizationRequestTest : FunSpec({
    val requestedBy = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888777", PartyType.OrganizationEntity)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val validTo = Instant.parse("2100-02-01T00:00:00Z")
    val meta = buildJsonObject { put("source", JsonPrimitive("test")) }
    val meteringPoints = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001")))
    val appliesToMeteringPoints = AuthorizationScopeConstraint(
        AuthorizationScopeConstraintKind.AppliesTo,
        AuthorizationScopeConstraintAttribute.MeteringPointId,
        meteringPoints,
    )

    fun allowedContractTypes(vararg types: MeteringPointContractType) = AuthorizationScopeConstraint(
        AuthorizationScopeConstraintKind.AllowedChanges,
        AuthorizationScopeConstraintAttribute.ContractType,
        ResourceConstraint.ContractTypes(types.toSet()),
    )

    fun create(details: AuthorizationRequestTypeDetails) = AuthorizationRequest.create(
        requestedBy = requestedBy,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        details = details,
        validTo = validTo,
        meta = meta,
        externalReference = "ref-1",
        redirectUri = "https://example.com/callback",
    )

    fun AuthorizationRequest.shouldMatchExpectedRequest(
        requestType: AuthorizationRequestType,
        requestedScopes: List<RequestedAuthorizationScope>,
        requestedGrantValidFrom: Instant? = null,
        requestedGrantValidTo: Instant? = null,
    ) {
        this shouldBe AuthorizationRequest(
            id = id,
            requestType = requestType,
            status = AuthorizationRequestStatus.Pending,
            requestedScopes = requestedScopes,
            externalReference = "ref-1",
            redirectUri = "https://example.com/callback",
            meta = meta,
            requestedGrantValidFrom = requestedGrantValidFrom,
            requestedGrantValidTo = requestedGrantValidTo,
            validFrom = validFrom,
            validTo = validTo,
            createdAt = createdAt,
            updatedAt = updatedAt,
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            approvedBy = null,
            authorizationGrants = null,
        )
        approvedBy shouldBe null
        authorizationGrants shouldBe null
    }

    test("creates ChangeOfEnergySupplierForOrganization request") {
        val request = create(AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(meteringPoints))

        request.shouldMatchExpectedRequest(
            requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
            requestedScopes = listOf(
                RequestedAuthorizationScope(
                    ResourceType.MeteringPointContract,
                    listOf(appliesToMeteringPoints, allowedContractTypes(MeteringPointContractType.EnergySupplier)),
                ),
            ),
        )
        request.requestedGrantValidFrom shouldBe null
        request.requestedGrantValidTo shouldBe null
    }

    test("creates MoveInAndChangeOfEnergySupplierForOrganization request") {
        val request = create(
            AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = meteringPoints,
                moveInDate = LocalDate(2026, 10, 1),
            ),
        )

        request.shouldMatchExpectedRequest(
            requestType = AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization,
            requestedScopes = listOf(
                RequestedAuthorizationScope(
                    ResourceType.MeteringPointContract,
                    listOf(
                        appliesToMeteringPoints,
                        allowedContractTypes(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
                        AuthorizationScopeConstraint(
                            AuthorizationScopeConstraintKind.AllowedChanges,
                            AuthorizationScopeConstraintAttribute.ValidFrom,
                            ResourceConstraint.ValidFrom(Instant.parse("2026-09-30T22:00:00Z")),
                        ),
                    ),
                ),
            ),
        )
        request.requestedGrantValidFrom shouldBe null
        request.requestedGrantValidTo shouldBe null
    }

    test("creates EnergySupplierFrameworkAgreement request with requested grant validity") {
        val grantValidFrom = Instant.parse("2100-01-01T00:00:00Z")
        val grantValidTo = Instant.parse("2101-01-01T00:00:00Z")
        val request = create(AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(grantValidFrom, grantValidTo))

        request.shouldMatchExpectedRequest(
            requestType = AuthorizationRequestType.EnergySupplierFrameworkAgreement,
            requestedScopes = listOf(
                RequestedAuthorizationScope(
                    ResourceType.MeteringPointContract,
                    listOf(allowedContractTypes(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier)),
                ),
            ),
            requestedGrantValidFrom = grantValidFrom,
            requestedGrantValidTo = grantValidTo,
        )
    }

    test("creates EnergySupplierFrameworkAgreement request without requested grant validity") {
        val request = create(AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(null, null))

        request.requestedGrantValidFrom shouldBe null
        request.requestedGrantValidTo shouldBe null
    }

    test("creates ChangeOfEnergySupplierForOrganization requests with unique IDs") {
        val details = AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(meteringPoints)

        create(details).id shouldNotBe create(details).id
    }
})
