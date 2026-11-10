package no.elhub.auth.v1.requesthandlers.movein

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApi
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApiConfig
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainer
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainerExtension
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.NON_EXISTING_METERING_POINT
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.SERVER_ERROR_METERING_POINT
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.VALID_METERING_POINT_1
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.meteringPointsServiceHttpClient
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsApi
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsApiConfig
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainer
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainerExtension
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.NON_EXISTING_ORGANISATION_NUMBER
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.NOT_BALANCE_SUPPLIER_PARTY_ID
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_ORGANISATION_NAME
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_ORGANISATION_NUMBER
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_PARTY_ID
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_PARTY_NAME
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.organisationsServiceHttpClient
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestPreparationError
import kotlin.time.Clock

class MoveInAndChangeOfEnergySupplierRequestHandlerTest : FunSpec({
    extensions(OrganisationsServiceTestContainerExtension, MeteringPointsServiceTestContainerExtension)

    val requestedBy = AuthorizationAgent(VALID_PARTY_ID, AgentType.OrganizationEntity)
    val requestedPrincipal = AuthorizationAgent(VALID_ORGANISATION_NUMBER, AgentType.Organization)
    val requestedOf = AuthorizationAgent("person-1", AgentType.Person)
    val moveInDate = LocalDate(2026, 10, 1)
    val tokenProvider = object : JwtTokenProvider {
        override suspend fun getToken(): String = "token"
    }
    lateinit var handler: MoveInAndChangeOfEnergySupplierRequestHandler

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
        handler = MoveInAndChangeOfEnergySupplierRequestHandler(meteringPointsService, organisationsService)
    }

    fun details(meteringPointId: String = VALID_METERING_POINT_1) =
        AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create(meteringPointId))),
            moveInDate = moveInDate,
        )

    test("stores requested metering point metadata and the move-in date") {
        val result = handler.validateAndGetRequestTypeSpecificDetails(
            details = details(),
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
                },
            )
            put("moveInDate", moveInDate.toString())
            put("balanceSupplierName", VALID_PARTY_NAME)
            put("organizationName", VALID_ORGANISATION_NAME)
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
        val error = shouldThrow<RequestPreparationError.RequestedPrincipalLookupFailure> {
            handler.validateAndGetRequestTypeSpecificDetails(
                details = details(),
                requestedBy = requestedBy,
                requestedPrincipal = AuthorizationAgent(NON_EXISTING_ORGANISATION_NUMBER, AgentType.Organization),
                requestedOf = requestedOf,
            )
        }

        error.clientError shouldBe ClientError.NotFound
    }

    test("throws a requested-by lookup failure when the agent is not a balance supplier") {
        val error = shouldThrow<RequestPreparationError.RequestedByLookupFailure> {
            handler.validateAndGetRequestTypeSpecificDetails(
                details = details(),
                requestedBy = AuthorizationAgent(NOT_BALANCE_SUPPLIER_PARTY_ID, AgentType.OrganizationEntity),
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
            )
        }

        error.clientError shouldBe ClientError.NotFound
    }
})
