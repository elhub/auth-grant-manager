package no.elhub.auth.v1.requesthandlers.frameworkagreement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
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
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestPreparationError

class EnergySupplierFrameworkAgreementRequestHandlerTest : FunSpec({
    extensions(OrganisationsServiceTestContainerExtension)

    val requestedBy = AuthorizationAgent(VALID_PARTY_ID, AgentType.OrganizationEntity)
    val requestedPrincipal = AuthorizationAgent(VALID_ORGANISATION_NUMBER, AgentType.Organization)
    val requestedOf = AuthorizationAgent("person-1", AgentType.Person)
    val tokenProvider = object : JwtTokenProvider {
        override suspend fun getToken(): String = "token"
    }
    lateinit var handler: EnergySupplierFrameworkAgreementRequestHandler
    beforeSpec {
        val organisationsService = OrganisationsApi(
            OrganisationsApiConfig(OrganisationsServiceTestContainer.serviceUrl()),
            organisationsServiceHttpClient,
            tokenProvider,
        )
        handler = EnergySupplierFrameworkAgreementRequestHandler(organisationsService)
    }

    fun details() = AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(null, null)

    test("stores requested-by and requested-principal names in metadata") {
        val result = handler.validateAndGetRequestTypeSpecificDetails(
            details = details(),
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
        )

        result.meta shouldBe buildJsonObject {
            put("balanceSupplierName", VALID_PARTY_NAME)
            put("organizationName", VALID_ORGANISATION_NAME)
        }
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

    test("throws a requested-principal name lookup failure when the organisation cannot be found") {
        val error = shouldThrow<RequestPreparationError.RequestedPrincipalNameLookupFailure> {
            handler.validateAndGetRequestTypeSpecificDetails(
                details = details(),
                requestedBy = requestedBy,
                requestedPrincipal = AuthorizationAgent(NON_EXISTING_ORGANISATION_NUMBER, AgentType.Organization),
                requestedOf = requestedOf,
            )
        }

        error.clientError shouldBe ClientError.NotFound
    }
})
