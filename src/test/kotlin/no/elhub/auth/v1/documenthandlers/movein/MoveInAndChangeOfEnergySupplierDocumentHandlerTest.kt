package no.elhub.auth.v1.documenthandlers.movein

import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.structuredata.meteringpoints.AccountingPoint
import no.elhub.auth.common.structuredata.meteringpoints.Attributes
import no.elhub.auth.common.structuredata.meteringpoints.Meter
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointAddress
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointResponse
import no.elhub.auth.common.structuredata.meteringpoints.Relationships
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

class MoveInAndChangeOfEnergySupplierDocumentHandlerTest : FunSpec({
    val context = DocumentCreationContext(
        requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity),
        requestedPrincipal = AuthorizationParty("org-1", PartyType.Organization),
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
        language = DocumentLanguage.EN,
        externalReference = "reference-1",
    )
    val meteringPointIds = ResourceConstraint.MeteringPoints(
        setOf(MeteringPointId.create("707057500000000001")),
    )

    test("prepares move-in document details including the move-in date") {
        val validFrom = LocalDate(2026, 10, 1)
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPoints(listOf("707057500000000001"), "org-1")
        } returns Either.Right(listOf(meteringPointResponse("707057500000000001", "meter-1")))

        val result = MoveInAndChangeOfEnergySupplierDocumentHandler(service).prepare(
            specification = DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = meteringPointIds,
                validFrom = validFrom,
            ),
            context = context,
        )

        val expectedMeta = buildJsonObject {
            put(
                "requestedMeteringPoints",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "707057500000000001")
                            put(
                                "address",
                                buildJsonObject {
                                    put("streetName", "Example Street")
                                    put("houseNumber", "3")
                                    put("postalCode", "0000")
                                    put("city", "Example City")
                                },
                            )
                            put("meterNumber", "meter-1")
                        }
                    )
                }
            )
            put("validFrom", "2026-10-01")
        }
        result.map { it.meta } shouldBe Either.Right(expectedMeta)
        val details = result.fold(
            ifLeft = { error("Expected metering point details, got $it") },
            ifRight = { it },
        )
        val pdfContent = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation>()
        pdfContent.language shouldBe no.elhub.auth.common.documents.pdf.PdfLanguage.EN
        pdfContent.meteringPointId shouldBe "707057500000000001"
        pdfContent.meterNumber shouldBe "meter-1"
        pdfContent.moveInDate shouldBe validFrom
        (details.validTo > Clock.System.now()) shouldBe true
    }

    test("returns a lookup error when metering point data cannot be fetched") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPoints(any(), any())
        } returns Either.Left(ClientError.NotFound)

        val result = MoveInAndChangeOfEnergySupplierDocumentHandler(service).prepare(
            specification = DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = meteringPointIds,
                validFrom = LocalDate(2026, 10, 1),
            ),
            context = context,
        )

        result shouldBe Either.Left(DocumentPreparationError.RequestedMeteringPointsNotFound)
    }
})

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
