package no.elhub.auth.features.businessprocesses.changeofenergysupplier

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.datasharing.Attributes
import no.elhub.auth.features.businessprocesses.datasharing.ProductsResponse
import no.elhub.auth.features.businessprocesses.datasharing.StromprisService
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.BasicAuthConfig
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApi
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.BLOCKED_FOR_SWITCHING_METERING_POINT_1
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.END_USER_ID_1
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.END_USER_ID_2
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.NON_EXISTING_METERING_POINT
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.SHARED_END_USER_ID_1
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.VALID_METERING_POINT_1
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.meteringPointsServiceHttpClient
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApi
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.CURRENT_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.INACTIVE_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.NOT_BALANCE_SUPPLIER_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.organisationsServiceHttpClient
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType.Organization
import no.elhub.auth.features.common.person.Person
import no.elhub.auth.features.common.person.PersonService
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject
import java.util.UUID

private val VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, VALID_PARTY_ID)
private val AUTHORIZED_PARTY = AuthorizationParty(id = VALID_PARTY_ID, type = Organization)
private val NOT_VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "0000")
private val NON_EXISTING_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, NOT_BALANCE_SUPPLIER_PARTY_ID)
private val INACTIVE_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, INACTIVE_PARTY_ID)
private val MATCHING_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, CURRENT_PARTY_ID)
private val END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "123456789")
private val ANOTHER_END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "987654321")
private val SHARED_END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "11223344556")

class ChangeOfEnergySupplierBusinessHandlerTest :
    FunSpec({

        extensions(MeteringPointsServiceTestContainerExtension, OrganisationsServiceTestContainerExtension)
        lateinit var meteringPointsService: MeteringPointsService
        lateinit var handler: ChangeOfEnergySupplierBusinessHandler
        lateinit var organisationsService: OrganisationsService

        val personService = mockk<PersonService>()
        val stromprisService = mockk<StromprisService>()

        beforeSpec {
            meteringPointsService = MeteringPointsApi(
                MeteringPointsApiConfig(serviceUrl = MeteringPointsServiceTestContainer.serviceUrl(), basicAuthConfig = BasicAuthConfig("user", "pass")),
                meteringPointsServiceHttpClient
            )
            organisationsService = OrganisationsApi(
                OrganisationsApiConfig(
                    serviceUrl = OrganisationsServiceTestContainer.serviceUrl(),
                    basicAuthConfig = no.elhub.auth.features.businessprocesses.structuredata.organisations.BasicAuthConfig("user", "pass")
                ),
                organisationsServiceHttpClient
            )
            handler = ChangeOfEnergySupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                personService = personService,
                organisationsService = organisationsService,
                stromprisService = stromprisService,
                validateBalanceSupplierContractName = false
            )
        }

        coEvery { personService.findOrCreateByNin(END_USER.idValue) } returns Either.Right(Person(UUID.fromString(END_USER_ID_1)))
        coEvery { personService.findOrCreateByNin(ANOTHER_END_USER.idValue) } returns Either.Right(Person(UUID.fromString(END_USER_ID_2)))
        coEvery { personService.findOrCreateByNin(SHARED_END_USER.idValue) } returns Either.Right(Person(UUID.fromString(SHARED_END_USER_ID_1)))

        test("request validation fails on missing requestedFromName") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.MissingRequestedFromName.message))
        }

        test("request validation fails when requestedFrom is not related to metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = ANOTHER_END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.RequestedFromNotMeteringPointEndUser.message))
        }

        test("request validation fails when requestedFrom has access to metering point but is not end user") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = SHARED_END_USER,
                        requestedFromName = "From",
                        requestedTo = SHARED_END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.RequestedFromNotMeteringPointEndUser.message))
        }

        test("request validation fails on invalid metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = "123",
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.InvalidMeteringPointId.message))
        }

        test("request validation fails on unexisting metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = NON_EXISTING_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.NotFound(ChangeOfEnergySupplierValidationError.MeteringPointNotFound.message))
        }

        test("request validation fails on metering point blocked for switching") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = BLOCKED_FOR_SWITCHING_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.MeteringPointBlockedForSwitching.message))
        }

        test("request validation fails on not valid redirect URI") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.InvalidRedirectURI.message))
        }

        test("request validation fails on not valid requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = NOT_VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.InvalidRequestedBy.message))
        }

        test("request validation fails on non existing requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = NON_EXISTING_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.NotFound(ChangeOfEnergySupplierValidationError.RequestedByNotFound.message))
        }

        test("request validation fails on not active requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = INACTIVE_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.NotActiveRequestedBy.message))
        }

        test("request validation fails on requested by matching current balance supplier") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = MATCHING_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.MatchingRequestedBy.message))
        }

        test("request validation fails on requested to not matching requested from") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.RequestedToRequestedFromMismatch.message))
        }

        test("request validation fails with UnexpectedError when a non-validation-specific error happens in metering points service") {
            val mockMeteringPointsService = mockk<MeteringPointsService>()
            coEvery {
                mockMeteringPointsService.getMeteringPointByIdAndElhubInternalId(
                    any(),
                    any()
                )
            } returns Either.Left(ClientError.BadRequest)
            val handlerWithMockedService = ChangeOfEnergySupplierBusinessHandler(
                meteringPointsService = mockMeteringPointsService,
                personService = personService,
                organisationsService = organisationsService,
                stromprisService = stromprisService,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handlerWithMockedService.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Unexpected(ChangeOfEnergySupplierValidationError.UnexpectedError.message))
        }

        test("request validation fails with UnexpectedError when a non-validation-specific error happens in organisations service") {
            val mockOrganisationsService = mockk<OrganisationsService>()
            coEvery {
                mockOrganisationsService.getPartyByIdAndPartyType(
                    any(),
                    any()
                )
            } returns Either.Left(ClientError.ServerError)
            val handlerWithMockedService = ChangeOfEnergySupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                personService = personService,
                organisationsService = mockOrganisationsService,
                stromprisService = stromprisService,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handlerWithMockedService.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Unexpected(ChangeOfEnergySupplierValidationError.UnexpectedError.message))
        }

        test("strompris service is not called if validateBalanceSupplierContractName is false, assuming all previous validations pass") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
            coVerify(exactly = 0) { stromprisService.getProductsByOrganizationNumber(any()) }
        }

        test("strompris service is called if validateBalanceSupplierContractName is true, assuming all previous validations pass") {
            val mockStromprisService = mockk<StromprisService>()
            coEvery {
                mockStromprisService.getProductsByOrganizationNumber(any())
            } returns Either.Right(
                ProductsResponse(
                    listOf(
                        JsonApiResponseResourceObject(
                            id = "1",
                            type = "product",
                            attributes = Attributes(
                                1,
                                "Contract"
                            )
                        )
                    )
                )
            )
            val handlerWithMockedStromprisService = ChangeOfEnergySupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                personService = personService,
                organisationsService = organisationsService,
                stromprisService = mockStromprisService,
                validateBalanceSupplierContractName = true
            )
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )
            handlerWithMockedStromprisService.validateAndReturnRequestCommand(model).shouldBeRight()
            coVerify(exactly = 1) { mockStromprisService.getProductsByOrganizationNumber(any()) }
        }

        test("request produces RequestCommand for valid input") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedFromName = "From",
                        requestedTo = END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson
            command.validTo shouldBe defaultValidTo().toTimeZoneOffsetDateTimeAtStartOfDay()
            command.meta.toMetaAttributes()["redirectURI"] shouldBe "https://example.com"
        }

        test("document produces DocumentCommand for valid input") {
            val model =
                CreateDocumentModel(
                    authorizedParty = AuthorizationParty(id = VALID_PARTY.idValue, type = Organization),
                    documentType = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                    CreateDocumentMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = END_USER,
                        requestedTo = END_USER,
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                    ),
                )

            val command = handler.validateAndReturnDocumentCommand(model).shouldBeRight()
            command.meta.toMetaAttributes()["requestedFromName"] shouldBe "From"
        }
    })
