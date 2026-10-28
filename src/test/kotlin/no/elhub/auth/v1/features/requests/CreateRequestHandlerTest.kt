package no.elhub.auth.v1.features.requests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.requests.AuthorizationRequestSpecification
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.ChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.features.requests.creation.EnergySupplierFrameworkAgreementRequestHandler
import no.elhub.auth.v1.features.requests.creation.MoveInAndChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.features.requests.creation.RequestTypeSpecificDetails
import no.elhub.auth.v1.repositories.RequestRepository
import kotlin.time.Instant

class CreateRequestHandlerTest : FunSpec({
    val requestedBy = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888777", PartyType.OrganizationEntity)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val validTo = Instant.parse("2100-02-01T00:00:00Z")
    val meta = buildJsonObject { put("source", JsonPrimitive("test")) }
    val meteringPoints = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001")))

    lateinit var supplierChangeHandler: ChangeOfEnergySupplierRequestHandler
    lateinit var moveInHandler: MoveInAndChangeOfEnergySupplierRequestHandler
    lateinit var frameworkAgreementHandler: EnergySupplierFrameworkAgreementRequestHandler
    lateinit var repository: RequestRepository
    lateinit var inserted: CapturingSlot<AuthorizationRequest>

    beforeTest {
        supplierChangeHandler = mockk()
        moveInHandler = mockk()
        frameworkAgreementHandler = mockk()
        repository = mockk()
        inserted = slot()
        coEvery { repository.insert(capture(inserted)) } answers { inserted.captured }
    }

    suspend fun create(details: AuthorizationRequestTypeDetails) =
        CreateRequestHandler(supplierChangeHandler, moveInHandler, frameworkAgreementHandler, repository)(
            specification = AuthorizationRequestSpecification("ref-1", "https://example.com/callback", details),
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
        )

    fun shouldInsertRequestFor(details: AuthorizationRequestTypeDetails, requestType: AuthorizationRequestType) {
        val expected = AuthorizationRequest.create(
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            details = details,
            validTo = validTo,
            meta = meta,
            externalReference = "ref-1",
            redirectUri = "https://example.com/callback",
        )
        val captured = inserted.captured
        captured.requestType shouldBe requestType
        captured shouldBe expected.copy(
            id = captured.id,
            validFrom = captured.validFrom,
            createdAt = captured.createdAt,
            updatedAt = captured.updatedAt,
        )
        coVerify(exactly = 1) { repository.insert(any()) }
    }

    test("ChangeOfEnergySupplierForOrganization uses its handler and inserts the created request") {
        val details = AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(meteringPoints)
        coEvery {
            supplierChangeHandler.validateAndGetRequestTypeSpecificDetails(details, requestedBy, requestedPrincipal, requestedOf)
        } returns RequestTypeSpecificDetails(validTo, meta)

        create(details) shouldBe inserted.captured

        shouldInsertRequestFor(details, AuthorizationRequestType.ChangeOfEnergySupplierForOrganization)
        coVerify(exactly = 0) { moveInHandler.validateAndGetRequestTypeSpecificDetails(any(), any(), any(), any()) }
        coVerify(exactly = 0) {
            frameworkAgreementHandler.validateAndGetRequestTypeSpecificDetails(any(), any(), any(), any())
        }
    }

    test("MoveInAndChangeOfEnergySupplierForOrganization uses its handler and inserts the created request") {
        val details = AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = meteringPoints,
            moveInDate = LocalDate(2026, 10, 1),
        )
        coEvery {
            moveInHandler.validateAndGetRequestTypeSpecificDetails(details, requestedBy, requestedPrincipal, requestedOf)
        } returns RequestTypeSpecificDetails(validTo, meta)

        create(details) shouldBe inserted.captured

        shouldInsertRequestFor(details, AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization)
        coVerify(exactly = 0) { supplierChangeHandler.validateAndGetRequestTypeSpecificDetails(any(), any(), any(), any()) }
        coVerify(exactly = 0) {
            frameworkAgreementHandler.validateAndGetRequestTypeSpecificDetails(any(), any(), any(), any())
        }
    }

    test("EnergySupplierFrameworkAgreement uses its handler and inserts the created request") {
        val details = AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(
            requestedGrantValidFrom = Instant.parse("2100-01-01T00:00:00Z"),
            requestedGrantValidTo = Instant.parse("2101-01-01T00:00:00Z"),
        )
        coEvery {
            frameworkAgreementHandler.validateAndGetRequestTypeSpecificDetails(
                details,
                requestedBy,
                requestedPrincipal,
                requestedOf,
            )
        } returns RequestTypeSpecificDetails(validTo, meta)

        create(details) shouldBe inserted.captured

        shouldInsertRequestFor(details, AuthorizationRequestType.EnergySupplierFrameworkAgreement)
        coVerify(exactly = 0) { supplierChangeHandler.validateAndGetRequestTypeSpecificDetails(any(), any(), any(), any()) }
        coVerify(exactly = 0) { moveInHandler.validateAndGetRequestTypeSpecificDetails(any(), any(), any(), any()) }
    }
})
