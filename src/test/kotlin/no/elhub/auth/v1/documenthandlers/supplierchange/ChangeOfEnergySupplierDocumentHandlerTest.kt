package no.elhub.auth.v1.documenthandlers.supplierchange

import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.structuredata.meteringpoints.AccountingPoint
import no.elhub.auth.common.structuredata.meteringpoints.Attributes
import no.elhub.auth.common.structuredata.meteringpoints.Meter
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointAddress
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointResponse
import no.elhub.auth.common.structuredata.meteringpoints.Relationships
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships
import kotlin.time.Clock

class ChangeOfEnergySupplierDocumentHandlerTest : FunSpec({
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

    test("prepares supplier-change document details") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPoints(listOf("707057500000000001"), "org-1")
        } returns Either.Right(listOf(meteringPointResponse("707057500000000001", "meter-1")))

        val result = ChangeOfEnergySupplierDocumentHandler(service).prepare(
            specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(meteringPointIds),
            context = context,
        )

        val expectedMeta = buildJsonObject {
            put(
                "requestedMeteringPoints",
                kotlinx.serialization.json.buildJsonArray {
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
        }
        result.map { it.meta } shouldBe Either.Right(expectedMeta)
        val details = result.fold(
            ifLeft = { error("Expected metering point details, got $it") },
            ifRight = { it },
        )
        val pdfContent = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation>()
        pdfContent.language shouldBe no.elhub.auth.common.documents.pdf.PdfLanguage.EN
        pdfContent.meteringPointId shouldBe "707057500000000001"
        pdfContent.meterNumber shouldBe "meter-1"
        (details.validTo > Clock.System.now()) shouldBe true
    }

    test("returns a lookup error when metering point data cannot be fetched") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPoints(any(), any())
        } returns Either.Left(ClientError.NotFound)

        val result = ChangeOfEnergySupplierDocumentHandler(service).prepare(
            specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(meteringPointIds),
            context = context,
        )

        result shouldBe Either.Left(DocumentPreparationError.RequestedMeteringPointsNotFound)
    }

    test("preserves other metering-points service errors") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPoints(any(), any())
        } returns Either.Left(ClientError.ServerError)

        val result = ChangeOfEnergySupplierDocumentHandler(service).prepare(
            specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(meteringPointIds),
            context = context,
        )

        result shouldBe Either.Left(
            DocumentPreparationError.MeteringPointsServiceFailure(ClientError.ServerError),
        )
    }

    test("stores metadata for every requested metering point in a stable order") {
        val service = mockk<MeteringPointsService>()
        coEvery {
            service.getMeteringPoints(listOf("707057500000000001", "707057500000000002"), "org-1")
        } returns Either.Right(
            listOf(
                meteringPointResponse("707057500000000001", "meter-707057500000000001"),
                meteringPointResponse("707057500000000002", "meter-707057500000000002"),
            ),
        )
        val requestedIds = ResourceConstraint.MeteringPoints(
            setOf(
                MeteringPointId.create("707057500000000002"),
                MeteringPointId.create("707057500000000001"),
            ),
        )

        val result = ChangeOfEnergySupplierDocumentHandler(service).prepare(
            specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(requestedIds),
            context = context,
        )

        val expectedMeta = buildJsonObject {
            put(
                "requestedMeteringPoints",
                kotlinx.serialization.json.buildJsonArray {
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
                            put("meterNumber", "meter-707057500000000001")
                        }
                    )
                    add(
                        buildJsonObject {
                            put("id", "707057500000000002")
                            put(
                                "address",
                                buildJsonObject {
                                    put("streetName", "Example Street")
                                    put("houseNumber", "3")
                                    put("postalCode", "0000")
                                    put("city", "Example City")
                                },
                            )
                            put("meterNumber", "meter-707057500000000002")
                        }
                    )
                }
            )
        }

        result.map { it.meta } shouldBe Either.Right(expectedMeta)
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
