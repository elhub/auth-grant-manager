package no.elhub.auth.v1.requesthandlers.frameworkagreement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
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
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestPreparationError

class EnergySupplierFrameworkAgreementRequestHandlerTest : FunSpec({
    extensions(OrganisationsServiceTestContainerExtension)

    val requestedBy = AuthorizationParty(VALID_PARTY_ID, PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty(VALID_ORGANISATION_NUMBER, PartyType.Organization)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
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

    test("throws a requested-by lookup failure when the party is not a balance supplier") {
        val error = shouldThrow<RequestPreparationError.RequestedByLookupFailure> {
            handler.validateAndGetRequestTypeSpecificDetails(
                details = details(),
                requestedBy = AuthorizationParty(NOT_BALANCE_SUPPLIER_PARTY_ID, PartyType.OrganizationEntity),
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
                requestedPrincipal = AuthorizationParty(NON_EXISTING_ORGANISATION_NUMBER, PartyType.Organization),
                requestedOf = requestedOf,
            )
        }

        error.clientError shouldBe ClientError.NotFound
    }
})
