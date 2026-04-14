package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.features.businessprocesses.datasharing.Attributes
import no.elhub.auth.features.businessprocesses.datasharing.ProductsResponse
import no.elhub.auth.features.businessprocesses.datasharing.StromprisService
import no.elhub.auth.features.businessprocesses.ediel.EdielEnvironment
import no.elhub.auth.features.businessprocesses.ediel.EdielPartyRedirectResponseDto
import no.elhub.auth.features.businessprocesses.ediel.EdielRedirectUrlsDto
import no.elhub.auth.features.businessprocesses.ediel.EdielService
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
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.common.todayOslo
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.CreateDocumentBusinessMeta
import no.elhub.auth.features.documents.common.CreateDocumentBusinessModel
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.CreateRequestBusinessMeta
import no.elhub.auth.features.requests.common.CreateRequestBusinessModel
import no.elhub.auth.features.requests.create.command.TEXT_VERSION_KEY
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject
import java.util.UUID

private val VALID_PARTY = AuthorizationParty(id = VALID_PARTY_ID, type = PartyType.Organization)
private val NOT_VALID_PARTY = AuthorizationParty(id = "0000", type = PartyType.Organization)
private val NON_EXISTING_PARTY = AuthorizationParty(id = NOT_BALANCE_SUPPLIER_PARTY_ID, type = PartyType.Organization)
private val INACTIVE_PARTY = AuthorizationParty(id = INACTIVE_PARTY_ID, type = PartyType.Organization)
private val AUTHORIZED_PARTY = VALID_PARTY
private val VALID_MOVEIN_DATE = LocalDate(2025, 1, 1)
private val END_USER = AuthorizationParty(id = END_USER_ID_1, type = PartyType.Person)
private val ANOTHER_END_USER = AuthorizationParty(id = END_USER_ID_2, type = PartyType.Person)
private val SHARED_END_USER = AuthorizationParty(id = SHARED_END_USER_ID_1, type = PartyType.Person)

class MoveInAndChangeOfBalanceSupplierBusinessHandlerTest :
    FunSpec({

        extensions(OrganisationsServiceTestContainerExtension, MeteringPointsServiceTestContainerExtension)
        lateinit var organisationsService: OrganisationsService
        lateinit var meteringPointsService: MeteringPointsService
        lateinit var handler: MoveInAndChangeOfBalanceSupplierBusinessHandler

        val stromprisService = mockk<StromprisService>()
        val edielService = mockk<EdielService>()
        val jwtTokenProvider = mockk<JwtTokenProvider>()
        coEvery { jwtTokenProvider.getToken() } returns "token"

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
                ),
                meteringPointsServiceHttpClient,
                jwtTokenProvider
            )
            handler = MoveInAndChangeOfBalanceSupplierBusinessHandler(
                organisationsService = organisationsService,
                meteringPointsService = meteringPointsService,
                stromprisService = stromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = false
            )
        }

        beforeTest {
            clearMocks(edielService, answers = false, recordedCalls = true)
            coEvery { edielService.getPartyRedirect(any()) } returns Either.Right(
                EdielPartyRedirectResponseDto(
                    redirectUrls = EdielRedirectUrlsDto(
                        production = "https://example.com"
                    )
                )
            )
        }

        test("request validation allows missing moveInDate") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = null,
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()
            command.meta.toRequestMetaAttributes()["moveInDate"] shouldBe null
            command.meta.toRequestMetaAttributes()[TEXT_VERSION_KEY] shouldBe "v1"
        }

        test("request validation fails on future moveInDate") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = todayOslo().plus(DatePeriod(days = 1)),
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.MoveInDateNotBackInTime.message))
        }

        test("request validation allows moveInDate today") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = todayOslo(),
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation fails on not valid redirect URI") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.InvalidRedirectURI.message))
        }

        test("request validation allows missing redirect URI and skips Ediel lookup") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = null,
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
            coVerify(exactly = 0) { edielService.getPartyRedirect(any()) }
        }

        test("request validation fails on blank redirect URI") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "   ",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.InvalidRedirectURI.message))
            coVerify(exactly = 1) { edielService.getPartyRedirect(any()) }
        }

        test("request validation allows redirect URI when host matches Ediel domain") {
            coEvery { edielService.getPartyRedirect(any()) } returns Either.Right(edielRedirectResponse("https://example.com/login"))
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com/callback",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation in test environment uses test URL from Ediel") {
            val handlerWithTestEnvironment = MoveInAndChangeOfBalanceSupplierBusinessHandler(
                organisationsService = organisationsService,
                meteringPointsService = meteringPointsService,
                stromprisService = stromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.TEST,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = false
            )
            coEvery { edielService.getPartyRedirect(any()) } returns Either.Right(
                edielRedirectResponse(
                    productionRedirectUrl = "https://production.example/login",
                    testRedirectUrl = "https://test.example/login"
                )
            )
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://app.test.example/callback",
                    ),
                )

            handlerWithTestEnvironment.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation skips redirect URI validation when feature toggle is disabled") {
            val mockEdielService = mockk<EdielService>()
            val handlerWithRedirectValidationDisabled = MoveInAndChangeOfBalanceSupplierBusinessHandler(
                organisationsService = organisationsService,
                meteringPointsService = meteringPointsService,
                stromprisService = stromprisService,
                edielService = mockEdielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = false,
                validateBalanceSupplierContractName = false
            )
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "not-a-valid-uri",
                    ),
                )

            handlerWithRedirectValidationDisabled.validateAndReturnRequestCommand(model).shouldBeRight()
            coVerify(exactly = 0) { mockEdielService.getPartyRedirect(any()) }
        }

        test("request validation allows redirect URI when host is subdomain of Ediel domain") {
            coEvery { edielService.getPartyRedirect(any()) } returns Either.Right(edielRedirectResponse("https://example.com/login"))
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://app.example.com/callback",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation fails when redirect URI domain does not match Ediel domain") {
            coEvery { edielService.getPartyRedirect(any()) } returns Either.Right(edielRedirectResponse("https://other-domain.example/callback"))
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        moveInDate = VALID_MOVEIN_DATE,
                        redirectURI = "https://example.com/callback",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(MoveInAndChangeOfBalanceSupplierValidationError.RedirectURINotMatchingEdiel.message))
        }

        test("request validation fails on invalid metering point") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = SHARED_END_USER,
                    requestedTo = SHARED_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = NOT_VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = NON_EXISTING_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = INACTIVE_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                stromprisService = stromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                stromprisService = stromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = SHARED_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                stromprisService = mockStromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = true
            )
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
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
            command.validTo shouldBe todayOslo().plus(DatePeriod(days = 28)).toTimeZoneOffsetDateTimeAtStartOfDay()
            command.meta.toRequestMetaAttributes()["moveInDate"] shouldBe VALID_MOVEIN_DATE.toString()
            command.meta.toRequestMetaAttributes()["redirectURI"] shouldBe "https://example.com"
            command.meta.toRequestMetaAttributes()[TEXT_VERSION_KEY] shouldBe "v1"
            command.meta.toRequestMetaAttributes().containsKey("requestedForMeterNumber") shouldBe true
        }

        test("grant properties moveInDate is present and validTo is one year from acceptance") {
            val party = AuthorizationParty(id = "party-1", type = PartyType.Organization)
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                requestedBy = party,
                requestedFrom = party,
                requestedTo = party,
                validTo = todayOslo().toTimeZoneOffsetDateTimeAtStartOfDay(),
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
            properties.validFrom shouldBe todayOslo()
            properties.validTo shouldBe todayOslo().plus(DatePeriod(years = 1))
        }

        test("document produces DocumentCommand for valid input") {
            val model =
                CreateDocumentBusinessModel(
                    authorizedParty = VALID_PARTY,
                    documentType = AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = ANOTHER_END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateDocumentBusinessMeta(
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

private fun edielRedirectResponse(productionRedirectUrl: String?, testRedirectUrl: String? = null) =
    EdielPartyRedirectResponseDto(
        redirectUrls = EdielRedirectUrlsDto(
            production = productionRedirectUrl,
            test = testRedirectUrl
        )
    )
