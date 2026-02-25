package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.datasharing.Attributes
import no.elhub.auth.features.businessprocesses.datasharing.ProductsResponse
import no.elhub.auth.features.businessprocesses.datasharing.StromprisService
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApi
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.END_USER_ID_1
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.END_USER_ID_2
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.NON_EXISTING_METERING_POINT
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.SHARED_END_USER_ID_1
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsServiceTestData.VALID_METERING_POINT_1
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.meteringPointsServiceHttpClient
import no.elhub.auth.features.businessprocesses.structuredata.organisations.BasicAuthConfig
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApi
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.INACTIVE_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.NOT_BALANCE_SUPPLIER_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.organisationsServiceHttpClient
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.person.Person
import no.elhub.auth.features.common.person.PersonService
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.model.today
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject
import java.util.UUID

private val VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, VALID_PARTY_ID)
private val NOT_VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "0000")
private val NON_EXISTING_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, NOT_BALANCE_SUPPLIER_PARTY_ID)
private val INACTIVE_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, INACTIVE_PARTY_ID)
private val AUTHORIZED_PARTY = AuthorizationParty(id = VALID_PARTY_ID, type = PartyType.Organization)
private val VALID_MOVEIN_DATE = LocalDate(2025, 1, 1)
private val END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "123456789")
private val ANOTHER_END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "987654321")
private val SHARED_END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "11223344556")

class MoveInAndChangeOfBalanceSupplierBusinessHandlerTest :
    FunSpec({

        extensions(OrganisationsServiceTestContainerExtension, MeteringPointsServiceTestContainerExtension)
        lateinit var organisationsService: OrganisationsService
        lateinit var meteringPointsService: MeteringPointsService
        lateinit var handler: MoveInAndChangeOfBalanceSupplierBusinessHandler

        val personService = mockk<PersonService>()
        val stromprisService = mockk<StromprisService>()

        beforeSpec {
            organisationsService = OrganisationsApi(
                OrganisationsApiConfig(
                    serviceUrl = OrganisationsServiceTestContainer.serviceUrl(),
                    basicAuthConfig = BasicAuthConfig("user", "pass")
                ),
                organisationsServiceHttpClient
            )
            meteringPointsService = MeteringPointsApi(
                MeteringPointsApiConfig(
                    serviceUrl = MeteringPointsServiceTestContainer.serviceUrl(),
                    basicAuthConfig = no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.BasicAuthConfig("user", "pass")
                ),
                meteringPointsServiceHttpClient
            )
            handler = MoveInAndChangeOfBalanceSupplierBusinessHandler(
                organisationsService = organisationsService,
                meteringPointsService = meteringPointsService,
                personService = personService,
                stromprisService = stromprisService,
                validateBalanceSupplierContractName = false
            )
        }

        coEvery { personService.findOrCreateByNin(END_USER.idValue) } returns Either.Right(Person(UUID.fromString(END_USER_ID_1)))
        coEvery { personService.findOrCreateByNin(ANOTHER_END_USER.idValue) } returns Either.Right(Person(UUID.fromString(END_USER_ID_2)))
        coEvery { personService.findOrCreateByNin(SHARED_END_USER.idValue) } returns Either.Right(Person(UUID.fromString(SHARED_END_USER_ID_1)))

        test("request validation allows missing moveInDate") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = null,
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()
            command.meta.toMetaAttributes()["moveInDate"] shouldBe null
        }

        test("request validation fails on future moveInDate") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = today().plus(DatePeriod(days = 1)),
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.MoveInDateNotBackInTime.message))
        }

        test("request validation allows moveInDate today") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = today(),
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation fails on invalid metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = ANOTHER_END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = "123",
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.InvalidMeteringPointId.message))
        }

        test("request validation fails on non existing metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = ANOTHER_END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = NON_EXISTING_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.NotFound(MoveInAndChangeOfBalanceSupplierValidationError.MeteringPointNotFound.message))
        }

        test("request validation fails on requestedFrom being owner of metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.RequestedFromIsMeteringPointEndUser.message))
        }

        test("request validation allows on requestedFrom having access to metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation fails on not valid requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = NOT_VALID_PARTY,
                        requestedFrom = ANOTHER_END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.InvalidRequestedBy.message))
        }

        test("request validation fails on non existing requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = NON_EXISTING_PARTY,
                        requestedFrom = ANOTHER_END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.NotFound(MoveInAndChangeOfBalanceSupplierValidationError.RequestedByNotFound.message))
        }

        test("request validation fails on non Active requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = INACTIVE_PARTY,
                        requestedFrom = ANOTHER_END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.NotActiveRequestedBy.message))
        }

        test("request validation fails with UnexpectedError when a non-validation-specific error happens in metering points service") {
            val mockMeteringPointsService = mockk<MeteringPointsService>()
            coEvery {
                mockMeteringPointsService.getMeteringPointByIdAndElhubInternalId(
                    any(),
                    any()
                )
            } returns Either.Left(ClientError.BadRequest)
            val handlerWithMockedService = MoveInAndChangeOfBalanceSupplierBusinessHandler(
                organisationsService = organisationsService,
                meteringPointsService = mockMeteringPointsService,
                personService = personService,
                stromprisService = stromprisService,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handlerWithMockedService.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Unexpected(MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.message))
        }

        test("request validation fails with UnexpectedError when a non-validation-specific error happens in organisations service") {
            val mockOrganisationsService = mockk<OrganisationsService>()
            coEvery {
                mockOrganisationsService.getPartyByIdAndPartyType(
                    any(),
                    any()
                )
            } returns Either.Left(ClientError.ServerError)
            val handlerWithMockedService = MoveInAndChangeOfBalanceSupplierBusinessHandler(
                organisationsService = mockOrganisationsService,
                meteringPointsService = meteringPointsService,
                personService = personService,
                stromprisService = stromprisService,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handlerWithMockedService.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Unexpected(MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.message))
        }

        test("request validation fails on requested to not matching requested from") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = SHARED_END_USER,
                        requestedFromName = "From",
                        requestedTo = ANOTHER_END_USER,
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.RequestedToRequestedFromMismatch.message))
        }

        test("strompris service is not called if validateBalanceSupplierContractName is false, assuming all previous validations pass") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = VALID_MOVEIN_DATE,
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
            val handlerWithMockedStromprisService = MoveInAndChangeOfBalanceSupplierBusinessHandler(
                organisationsService = organisationsService,
                meteringPointsService = meteringPointsService,
                personService = personService,
                stromprisService = mockStromprisService,
                validateBalanceSupplierContractName = true
            )
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = VALID_MOVEIN_DATE,
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
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson
            command.validTo shouldBe today().plus(DatePeriod(days = 28)).toTimeZoneOffsetDateTimeAtStartOfDay()
            command.meta.toMetaAttributes()["moveInDate"] shouldBe VALID_MOVEIN_DATE.toString()
            command.meta.toMetaAttributes()["redirectURI"] shouldBe "https://example.com"
            command.meta.toMetaAttributes().containsKey("requestedForMeterNumber") shouldBe true
        }

        test("grant properties moveInDate is present and validTo is one year from acceptance") {
            val party = AuthorizationParty(id = "party-1", type = PartyType.Organization)
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                requestedBy = party,
                requestedFrom = party,
                requestedTo = party,
                validTo = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
            ).copy(
                properties = listOf(
                    AuthorizationRequestProperty(
                        requestId = UUID.randomUUID(),
                        key = "moveInDate",
                        value = "2024-01-01"
                    )
                )
            )

            val properties = handler.getCreateGrantProperties(request)

            properties.meta.getValue("moveInDate") shouldBe "2024-01-01"
            properties.validFrom shouldBe today()
            properties.validTo shouldBe today().plus(DatePeriod(years = 1))
        }

//        test("exception is thrown when moveInDate is not set") {
//            val party = AuthorizationParty(id = "party-1", type = PartyType.Organization)
//            val request = AuthorizationRequest.create(
//                type = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
//                requestedBy = party,
//                requestedFrom = party,
//                requestedTo = party,
//                validTo = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
//            )
//            shouldThrow<NoSuchElementException> {
//                handler.getCreateGrantProperties(request)
//            }
//        }

        test("exception is thrown when properties is invalid") {
            val party = AuthorizationParty(id = "party-1", type = PartyType.Organization)
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                requestedBy = party,
                requestedFrom = party,
                requestedTo = party,
                validTo = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
            ).copy(
                properties = listOf(
                    AuthorizationRequestProperty(
                        requestId = UUID.randomUUID(),
                        key = "random",
                        value = "test"
                    )
                )
            )

            shouldThrow<IllegalArgumentException> {
                handler.getCreateGrantProperties(request)
            }
        }

        test("document produces DocumentCommand for valid input") {
            val model =
                CreateDocumentModel(
                    authorizedParty = AuthorizationParty(id = VALID_PARTY.idValue, type = PartyType.Organization),
                    documentType = AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    meta =
                    CreateDocumentMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = ANOTHER_END_USER,
                        requestedTo = ANOTHER_END_USER,
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                    ),
                )

            val command = handler.validateAndReturnDocumentCommand(model).shouldBeRight()
            command.meta.toMetaAttributes()["moveInDate"] shouldBe VALID_MOVEIN_DATE.toString()
            command.meta.toMetaAttributes()["language"] shouldBe SupportedLanguage.DEFAULT.code
            command.meta.toMetaAttributes().containsKey("requestedForMeterNumber") shouldBe true
        }
    })
