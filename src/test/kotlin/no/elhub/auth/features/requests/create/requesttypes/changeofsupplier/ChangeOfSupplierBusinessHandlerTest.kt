package no.elhub.auth.features.requests.create.requesttypes.changeofsupplier

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierValidationError
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.BasicAuthConfig
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApi
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.ANOTHER_END_USER_ID
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.END_USER_ID
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.SHARED_END_USER_ID
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.VALID_METERING_POINT
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.meteringPointsServiceHttpClient
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApi
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.organisationsServiceHttpClient
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.person.Person
import no.elhub.auth.features.common.person.PersonService
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import java.util.UUID

class ChangeOfSupplierBusinessHandlerTest :
    FunSpec({
        extensions(MeteringPointsServiceTestContainerExtension, OrganisationsServiceTestContainerExtension)
        lateinit var meteringPointsService: MeteringPointsApi
        lateinit var organisationsService: OrganisationsService
        lateinit var handler: ChangeOfSupplierBusinessHandler
        val personService = mockk<PersonService>()

        beforeSpec {
            meteringPointsService = MeteringPointsApi(
                MeteringPointsApiConfig(
                    serviceUrl = MeteringPointsServiceTestContainer.serviceUrl(),
                    basicAuthConfig = BasicAuthConfig("user", "pass")
                ),
                meteringPointsServiceHttpClient
            )
            organisationsService = OrganisationsApi(
                OrganisationsApiConfig(
                    serviceUrl = OrganisationsServiceTestContainer.serviceUrl(),
                    basicAuthConfig = no.elhub.auth.features.businessprocesses.structuredata.organisations.BasicAuthConfig("user", "pass")
                ),
                organisationsServiceHttpClient
            )
            handler = ChangeOfSupplierBusinessHandler(meteringPointsService, personService, organisationsService)
        }

        val authorizedParty = AuthorizationParty(resourceId = "987654321", type = PartyType.Organization)
        val validParty = PartyIdentifier(PartyIdentifierType.OrganizationNumber, VALID_PARTY_ID)
        val endUser = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678902")
        val anotherEndUser = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "20987654321")
        val sharedEndUser = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "10987654321")

        coEvery { personService.findOrCreateByNin("12345678902") } returns Either.Right(Person(UUID.fromString(END_USER_ID)))
        coEvery { personService.findOrCreateByNin("20987654321") } returns Either.Right(Person(UUID.fromString(ANOTHER_END_USER_ID)))
        coEvery { personService.findOrCreateByNin("10987654321") } returns Either.Right(Person(UUID.fromString(SHARED_END_USER_ID)))

        test("returns validation error when requestedFromName is blank") {
            val model =
                CreateRequestModel(
                    authorizedParty = authorizedParty,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = validParty,
                        requestedFrom = endUser,
                        requestedFromName = "",
                        requestedTo = endUser,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "Some address",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            val result = handler.validateAndReturnRequestCommand(model)

            result.shouldBeLeft(ChangeOfSupplierValidationError.MissingRequestedFromName)
        }

        test("returns validation error when requestedFrom is not related to the metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = authorizedParty,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = validParty,
                        requestedFrom = anotherEndUser,
                        requestedFromName = "Supplier AS",
                        requestedTo = anotherEndUser,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "Some address",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            val result = handler.validateAndReturnRequestCommand(model)

            result.shouldBeLeft(ChangeOfSupplierValidationError.RequestedFromNotMeteringPointEndUser)
        }

        test("returns validation error when requestedFrom is user who has access to the metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = authorizedParty,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = validParty,
                        requestedFrom = sharedEndUser,
                        requestedFromName = "Supplier AS",
                        requestedTo = sharedEndUser,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "Some address",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            val result = handler.validateAndReturnRequestCommand(model)

            result.shouldBeLeft(ChangeOfSupplierValidationError.RequestedFromNotMeteringPointEndUser)
        }

        test("returns validation error when redirectURI fails in validation") {
            val model =
                CreateRequestModel(
                    authorizedParty = authorizedParty,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = validParty,
                        requestedFrom = endUser,
                        requestedFromName = "Supplier AS",
                        requestedTo = endUser,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "Some address",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "example.com",
                    ),
                )

            val result = handler.validateAndReturnRequestCommand(model)

            result.shouldBeLeft(ChangeOfSupplierValidationError.InvalidRedirectURI)
        }

        test("builds RequestCommand for valid input") {
            val model =
                CreateRequestModel(
                    authorizedParty = authorizedParty,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = validParty,
                        requestedFrom = endUser,
                        requestedFromName = "Supplier AS",
                        requestedTo = endUser,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "Some address",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson
            command.requestedBy shouldBe model.meta.requestedBy
            command.requestedFrom shouldBe model.meta.requestedFrom
            command.requestedTo shouldBe model.meta.requestedTo

            val metaAttributes = command.meta.toMetaAttributes()
            metaAttributes["requestedFromName"] shouldBe "Supplier AS"
            metaAttributes["requestedForMeteringPointId"] shouldBe VALID_METERING_POINT
            metaAttributes["requestedForMeteringPointAddress"] shouldBe "Some address"
            metaAttributes["balanceSupplierContractName"] shouldBe "Contract"
            metaAttributes["redirectURI"] shouldBe "https://example.com"
        }
    })
