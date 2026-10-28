package no.elhub.auth.v1.requesthandlers.supplierchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.structuredata.meteringpoints.AccountingPoint
import no.elhub.auth.common.structuredata.meteringpoints.Attributes
import no.elhub.auth.common.structuredata.meteringpoints.Meter
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointAddress
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointResponse
import no.elhub.auth.common.structuredata.meteringpoints.Relationships
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsLookupException
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestPreparationError
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships
import kotlin.time.Clock

class ChangeOfEnergySupplierRequestHandlerTest : FunSpec({
    val requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("org-1", PartyType.Organization)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)

    fun details(vararg ids: String) = AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(
        ResourceConstraint.MeteringPoints(ids.map { MeteringPointId.create(it) }.toSet()),
    )

    test("stores metadata for every requested metering point in a stable order") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPointsForOrganisation(listOf("707057500000000001", "707057500000000002"), "org-1")
        } returns listOf(
            meteringPointResponse("707057500000000001", "meter-1"),
            meteringPointResponse("707057500000000002", "meter-2"),
        )

        val result = ChangeOfEnergySupplierRequestHandler(service).validateAndGetRequestTypeSpecificDetails(
            details = details("707057500000000002", "707057500000000001"),
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
        )

        result.meta shouldBe buildJsonObject {
            put(
                "requestedMeteringPoints",
                buildJsonArray {
                    add(expectedMeteringPoint("707057500000000001", "meter-1"))
                    add(expectedMeteringPoint("707057500000000002", "meter-2"))
                },
            )
        }
        (result.validTo > Clock.System.now()) shouldBe true
    }

    test("throws when a requested metering point cannot be found") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPointsForOrganisation(any(), any())
        } throws MeteringPointsLookupException(ClientError.NotFound)

        shouldThrow<RequestPreparationError.RequestedMeteringPointsNotFound> {
            ChangeOfEnergySupplierRequestHandler(service).validateAndGetRequestTypeSpecificDetails(
                details = details("707057500000000001"),
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
            )
        }
    }

    test("throws a service failure for other metering-points service errors") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPointsForOrganisation(any(), any())
        } throws MeteringPointsLookupException(ClientError.ServerError)

        val error = shouldThrow<RequestPreparationError.MeteringPointsServiceFailure> {
            ChangeOfEnergySupplierRequestHandler(service).validateAndGetRequestTypeSpecificDetails(
                details = details("707057500000000001"),
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
            )
        }

        error.clientError shouldBe ClientError.ServerError
    }
})

private fun expectedMeteringPoint(id: String, meterNumber: String): JsonObject = buildJsonObject {
    put("id", id)
    put(
        "address",
        buildJsonObject {
            put("streetName", "Example Street")
            put("houseNumber", "3")
            put("postalCode", "0000")
            put("city", "Example City")
        },
    )
    put("meterNumber", meterNumber)
}

private fun meteringPointResponse(id: String, meterNumber: String): MeteringPointResponse =
    JsonApiResponse.SingleDocumentWithRelationships(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "MeteringPoint",
            id = id,
            attributes = Attributes(
                accountingPoint = AccountingPoint(
                    address = MeteringPointAddress(
                        streetName = "Example Street",
                        houseNumber = "3",
                        postalCode = "0000",
                        city = "Example City",
                    ),
                    meter = Meter(1.0, meterNumber),
                ),
            ),
            relationships = Relationships(),
            links = null,
            meta = JsonObject(emptyMap()),
        ),
        links = JsonApiLinks.ResourceObjectLink(""),
        meta = JsonObject(emptyMap()),
    )
