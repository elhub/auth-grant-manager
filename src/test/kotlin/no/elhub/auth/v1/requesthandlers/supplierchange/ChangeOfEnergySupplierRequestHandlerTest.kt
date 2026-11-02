package no.elhub.auth.v1.requesthandlers.supplierchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
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
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestPreparationError
import kotlin.time.Clock

class ChangeOfEnergySupplierRequestHandlerTest : FunSpec({
    extensions(OrganisationsServiceTestContainerExtension, MeteringPointsServiceTestContainerExtension)

    val requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty(VALID_ORGANISATION_NUMBER, PartyType.Organization)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val tokenProvider = object : JwtTokenProvider {
        override suspend fun getToken(): String = "token"
    }
    lateinit var handler: ChangeOfEnergySupplierRequestHandler

    beforeSpec {
        val organisationsService = OrganisationsApi(
            OrganisationsApiConfig(OrganisationsServiceTestContainer.serviceUrl()),
            organisationsServiceHttpClient,
            tokenProvider,
        )
        val meteringPointsService = MeteringPointsApi(
            MeteringPointsApiConfig(MeteringPointsServiceTestContainer.serviceUrl()),
            meteringPointsServiceHttpClient,
            tokenProvider,
        )
        handler = ChangeOfEnergySupplierRequestHandler(meteringPointsService, organisationsService)
    }

    fun details(vararg ids: String) = AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(
        ResourceConstraint.MeteringPoints(ids.map { MeteringPointId.create(it) }.toSet()),
    )

    test("stores metadata for every requested metering point in a stable order") {
        val result = handler.validateAndGetRequestTypeSpecificDetails(
            details = details(VALID_METERING_POINT_2, VALID_METERING_POINT_1),
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
        )

        result.meta shouldBe buildJsonObject {
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
                        },
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
                        },
                    )
                },
            )
        }
        (result.validTo > Clock.System.now()) shouldBe true
    }

    test("throws when a requested metering point cannot be found") {
        shouldThrow<RequestPreparationError.RequestedMeteringPointsNotFound> {
            handler.validateAndGetRequestTypeSpecificDetails(
                details = details(NON_EXISTING_METERING_POINT),
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
            )
        }
    }

    test("throws a service failure for other metering-points service errors") {
        val error = shouldThrow<RequestPreparationError.MeteringPointsServiceFailure> {
            handler.validateAndGetRequestTypeSpecificDetails(
                details = details(SERVER_ERROR_METERING_POINT),
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
            )
        }

        error.clientError shouldBe ClientError.ServerError
    }

    test("throws an organisation lookup failure when the organisation cannot be found") {
        val error = shouldThrow<RequestPreparationError.OrganisationLookupFailure> {
            handler.validateAndGetRequestTypeSpecificDetails(
                details = details(VALID_METERING_POINT_1),
                requestedBy = requestedBy,
                requestedPrincipal = AuthorizationParty(NON_EXISTING_ORGANISATION_NUMBER, PartyType.Organization),
                requestedOf = requestedOf,
            )
        }

        error.clientError shouldBe ClientError.NotFound
    }
})
