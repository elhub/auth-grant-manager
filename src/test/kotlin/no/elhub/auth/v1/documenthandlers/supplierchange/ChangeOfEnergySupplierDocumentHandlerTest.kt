package no.elhub.auth.v1.documenthandlers.supplierchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.documents.pdf.PdfLanguage
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApi
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApiConfig
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainer
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainerExtension
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.NON_EXISTING_METERING_POINT
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.SERVER_ERROR_METERING_POINT
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.VALID_METERING_POINT_1
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.VALID_METERING_POINT_2
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.meteringPointsServiceHttpClient
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsApi
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsApiConfig
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainer
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainerExtension
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.NON_EXISTING_ORGANISATION_NUMBER
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_ORGANISATION_NUMBER
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.organisationsServiceHttpClient
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import kotlin.time.Clock

class ChangeOfEnergySupplierDocumentHandlerTest : FunSpec({
    extensions(OrganisationsServiceTestContainerExtension, MeteringPointsServiceTestContainerExtension)

    val context = DocumentCreationContext(
        requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity),
        requestedPrincipal = AuthorizationParty(VALID_ORGANISATION_NUMBER, PartyType.Organization),
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
        language = DocumentLanguage.EN,
        externalReference = "reference-1",
    )
    val tokenProvider = object : JwtTokenProvider {
        override suspend fun getToken(): String = "token"
    }
    lateinit var handler: ChangeOfEnergySupplierDocumentHandler

    beforeSpec {
        val organisationsService = OrganisationsApi(
            organisationsApiConfig = OrganisationsApiConfig(
                serviceUrl = OrganisationsServiceTestContainer.serviceUrl(),
            ),
            client = organisationsServiceHttpClient,
            tokenProvider = tokenProvider,
        )
        val meteringPointsService = MeteringPointsApi(
            meteringPointsApiConfig = MeteringPointsApiConfig(
                serviceUrl = MeteringPointsServiceTestContainer.serviceUrl(),
            ),
            client = meteringPointsServiceHttpClient,
            tokenProvider = tokenProvider,
        )
        handler = ChangeOfEnergySupplierDocumentHandler(meteringPointsService, organisationsService)
    }

    val specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(
        ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create(VALID_METERING_POINT_1))),
    )

    test("prepares supplier-change document details using the resolved Elhub internal ID") {
        val details = handler.prepare(specification, context)

        details.meta shouldBe buildJsonObject {
            put(
                "requestedMeteringPoints",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", VALID_METERING_POINT_1)
                            put(
                                "address",
                                buildJsonObject {
                                    put("streetName", "Example Street")
                                    put("houseNumber", "3")
                                    put("postalCode", "0000")
                                    put("city", "Example City")
                                },
                            )
                            put("meterNumber", "30030856")
                        }
                    )
                }
            )
        }
        val pdfContent = details.pdfContent
            .shouldBeInstanceOf<AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation>()
        pdfContent.language shouldBe PdfLanguage.EN
        pdfContent.meteringPointId shouldBe VALID_METERING_POINT_1
        pdfContent.meterNumber shouldBe "30030856"
        (details.validTo > Clock.System.now()) shouldBe true
    }

    test("throws when a requested metering point cannot be found") {
        shouldThrow<DocumentPreparationError.RequestedMeteringPointsNotFound> {
            handler.prepare(
                DocumentSpecification.ChangeOfEnergySupplierForOrganization(
                    ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create(NON_EXISTING_METERING_POINT))),
                ),
                context,
            )
        }
    }

    test("throws a service failure for other metering-points service errors") {
        val error = shouldThrow<DocumentPreparationError.MeteringPointsServiceFailure> {
            handler.prepare(
                DocumentSpecification.ChangeOfEnergySupplierForOrganization(
                    ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create(SERVER_ERROR_METERING_POINT))),
                ),
                context,
            )
        }

        error.clientError shouldBe ClientError.ServerError
    }

    test("stores metadata for every requested metering point in a stable order") {
        val details = handler.prepare(
            DocumentSpecification.ChangeOfEnergySupplierForOrganization(
                ResourceConstraint.MeteringPoints(
                    setOf(
                        MeteringPointId.create(VALID_METERING_POINT_2),
                        MeteringPointId.create(VALID_METERING_POINT_1),
                    ),
                ),
            ),
            context,
        )

        details.meta shouldBe buildJsonObject {
            put(
                "requestedMeteringPoints",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", VALID_METERING_POINT_1)
                            put(
                                "address",
                                buildJsonObject {
                                    put("streetName", "Example Street")
                                    put("houseNumber", "3")
                                    put("postalCode", "0000")
                                    put("city", "Example City")
                                },
                            )
                            put("meterNumber", "30030856")
                        }
                    )
                    add(
                        buildJsonObject {
                            put("id", VALID_METERING_POINT_2)
                            put(
                                "address",
                                buildJsonObject {
                                    put("streetName", "Example Avenue")
                                    put("houseNumber", "4")
                                    put("postalCode", "0001")
                                    put("city", "Example City")
                                },
                            )
                            put("meterNumber", "30030857")
                        }
                    )
                }
            )
        }
    }

    test("throws an organisation lookup failure when the organisation cannot be found") {
        val error = shouldThrow<DocumentPreparationError.OrganisationLookupFailure> {
            handler.prepare(
                specification,
                context.copy(
                    requestedPrincipal = AuthorizationParty(NON_EXISTING_ORGANISATION_NUMBER, PartyType.Organization),
                ),
            )
        }

        error.clientError shouldBe ClientError.NotFound
    }
})
