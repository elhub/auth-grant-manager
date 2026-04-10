package no.elhub.auth.features.businessprocesses.changeofbalancesupplier

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
import no.elhub.auth.features.common.party.PartyType.Organization
import no.elhub.auth.features.common.party.PartyType.Person
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.common.todayOslo
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.CreateDocumentBusinessMeta
import no.elhub.auth.features.documents.common.CreateDocumentBusinessModel
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.CreateRequestBusinessMeta
import no.elhub.auth.features.requests.common.CreateRequestBusinessModel
import no.elhub.auth.features.requests.create.command.TEXT_VERSION_KEY
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject

private val VALID_PARTY = AuthorizationParty(id = VALID_PARTY_ID, type = Organization)
private val AUTHORIZED_PARTY = VALID_PARTY
private val NOT_VALID_PARTY = AuthorizationParty(id = "0000", type = Organization)
private val NON_EXISTING_PARTY = AuthorizationParty(id = NOT_BALANCE_SUPPLIER_PARTY_ID, type = Organization)
private val INACTIVE_PARTY = AuthorizationParty(id = INACTIVE_PARTY_ID, type = Organization)
private val MATCHING_PARTY = AuthorizationParty(id = CURRENT_PARTY_ID, type = Organization)
private val END_USER = AuthorizationParty(id = END_USER_ID_1, type = Person)
private val ANOTHER_END_USER = AuthorizationParty(id = END_USER_ID_2, type = Person)
private val SHARED_END_USER = AuthorizationParty(id = SHARED_END_USER_ID_1, type = Person)

class ChangeOfBalanceSupplierBusinessHandlerTest :
    FunSpec({

        extensions(MeteringPointsServiceTestContainerExtension, OrganisationsServiceTestContainerExtension)
        lateinit var meteringPointsService: MeteringPointsService
        lateinit var handler: ChangeOfBalanceSupplierBusinessHandler
        lateinit var organisationsService: OrganisationsService

        val stromprisService = mockk<StromprisService>()
        val edielService = mockk<EdielService>()
        val jwtTokenProvider = mockk<JwtTokenProvider>()
        coEvery { jwtTokenProvider.getToken() } returns "token"

        beforeSpec {
            meteringPointsService = MeteringPointsApi(
                MeteringPointsApiConfig(
                    serviceUrl = MeteringPointsServiceTestContainer.serviceUrl(),
                ),
                meteringPointsServiceHttpClient,
                jwtTokenProvider
            )
            organisationsService = OrganisationsApi(
                OrganisationsApiConfig(
                    serviceUrl = OrganisationsServiceTestContainer.serviceUrl(),
                    basicAuthConfig = no.elhub.auth.features.businessprocesses.structuredata.organisations.BasicAuthConfig(
                        "user",
                        "pass"
                    )
                ),
                organisationsServiceHttpClient
            )
            handler = ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                organisationsService = organisationsService,
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

        test("request validation fails on missing requestedFromName") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.MissingRequestedFromName.message))
        }

        test("request validation fails when requestedFrom is not related to metering point") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.RequestedFromNotMeteringPointEndUser.message))
        }

        test("request validation fails when requestedFrom has access to metering point but is not end user") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.RequestedFromNotMeteringPointEndUser.message))
        }

        test("request validation fails on invalid metering point") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = "123",
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.InvalidMeteringPointId.message))
        }

        test("request validation fails on unexisting metering point") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = NON_EXISTING_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.NotFound(ChangeOfBalanceSupplierValidationError.MeteringPointNotFound.message))
        }

        test("request validation fails on metering point blocked for switching") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = BLOCKED_FOR_SWITCHING_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.MeteringPointBlockedForSwitching.message))
        }

        test("request validation fails on not valid redirect URI") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.InvalidRedirectURI.message))
        }

        test("request validation allows missing redirect URI and skips Ediel lookup") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "   ",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.InvalidRedirectURI.message))
            coVerify(exactly = 1) { edielService.getPartyRedirect(any()) }
        }

        test("request validation fails when redirect URI domain does not match Ediel domain") {
            coEvery { edielService.getPartyRedirect(any()) } returns Either.Right(
                EdielPartyRedirectResponseDto(
                    redirectUrls = EdielRedirectUrlsDto(
                        production = "https://other-domain.example/login"
                    )
                )
            )
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "https://example.com/callback",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.RedirectURINotMatchingEdiel.message))
        }

        test("request validation in test environment uses test URL from Ediel") {
            val handlerWithTestEnvironment = ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                organisationsService = organisationsService,
                stromprisService = stromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.TEST,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = false
            )
            coEvery { edielService.getPartyRedirect(any()) } returns Either.Right(
                EdielPartyRedirectResponseDto(
                    redirectUrls = EdielRedirectUrlsDto(
                        production = "https://production.example/login",
                        test = "https://test.example/login"
                    )
                )
            )
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "https://app.test.example/callback",
                    ),
                )

            handlerWithTestEnvironment.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation skips redirect URI validation when feature toggle is disabled") {
            val mockEdielService = mockk<EdielService>()
            val handlerWithRedirectValidationDisabled = ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                organisationsService = organisationsService,
                stromprisService = stromprisService,
                edielService = mockEdielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = false,
                validateBalanceSupplierContractName = false
            )
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "not-a-valid-uri",
                    ),
                )

            handlerWithRedirectValidationDisabled.validateAndReturnRequestCommand(model).shouldBeRight()
            coVerify(exactly = 0) { mockEdielService.getPartyRedirect(any()) }
        }

        test("request validation fails on not valid requested by") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = NOT_VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.InvalidRequestedBy.message))
        }

        test("request validation fails on non existing requested by") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = NON_EXISTING_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.NotFound(ChangeOfBalanceSupplierValidationError.RequestedByNotFound.message))
        }

        test("request validation fails on not active requested by") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = INACTIVE_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.NotActiveRequestedBy.message))
        }

        test("request validation fails on requested to not matching requested from") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = ANOTHER_END_USER,
                    meta =
                    CreateRequestBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.RequestedToRequestedFromMismatch.message))
        }

        test("request validation fails with UnexpectedError when a non-validation-specific error happens in metering points service") {
            val mockMeteringPointsService = mockk<MeteringPointsService>()
            coEvery {
                mockMeteringPointsService.getMeteringPointByIdAndElhubInternalId(
                    any(),
                    any()
                )
            } returns Either.Left(ClientError.BadRequest)
            val handlerWithMockedService = ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = mockMeteringPointsService,
                organisationsService = organisationsService,
                stromprisService = stromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "https://example.com",
                    ),
                )

            handlerWithMockedService.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Unexpected(ChangeOfBalanceSupplierValidationError.UnexpectedError.message))
        }

        test("request validation fails with UnexpectedError when a non-validation-specific error happens in organisations service") {
            val mockOrganisationsService = mockk<OrganisationsService>()
            coEvery {
                mockOrganisationsService.getPartyByIdAndPartyType(
                    any(),
                    any()
                )
            } returns Either.Left(ClientError.ServerError)
            val handlerWithMockedService = ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                organisationsService = mockOrganisationsService,
                stromprisService = stromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = false
            )

            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "https://example.com",
                    ),
                )

            handlerWithMockedService.validateAndReturnRequestCommand(model)
                .shouldBeLeft(BusinessProcessError.Unexpected(ChangeOfBalanceSupplierValidationError.UnexpectedError.message))
        }

        test("strompris service is not called if validateBalanceSupplierContractName is false, assuming all previous validations pass") {
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
            val handlerWithMockedStromprisService = ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = meteringPointsService,
                organisationsService = organisationsService,
                stromprisService = mockStromprisService,
                edielService = edielService,
                edielEnvironment = EdielEnvironment.PRODUCTION,
                validateRedirectUriFeature = true,
                validateBalanceSupplierContractName = true
            )
            val model =
                CreateRequestBusinessModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                    requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
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
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson
            command.validTo shouldBe todayOslo().plus(DatePeriod(days = 28)).toTimeZoneOffsetDateTimeAtStartOfDay()
            command.meta.toRequestMetaAttributes()["redirectURI"] shouldBe "https://example.com"
            command.meta.toRequestMetaAttributes()[TEXT_VERSION_KEY] shouldBe "v1"
            command.meta.toRequestMetaAttributes().containsKey("requestedForMeterNumber") shouldBe true
        }

        test("grant properties validTo is one year from acceptance") {
            val party = AuthorizationParty(id = "party-1", type = Organization)
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                requestedBy = party,
                requestedFrom = party,
                requestedTo = party,
                validTo = todayOslo().toTimeZoneOffsetDateTimeAtStartOfDay(),
            )

            val properties = handler.getCreateGrantProperties(request)

            properties.validFrom shouldBe todayOslo()
            properties.validTo shouldBe todayOslo().plus(DatePeriod(years = 1))
        }

        test("document produces DocumentCommand for valid input") {
            val model =
                CreateDocumentBusinessModel(
                    authorizedParty = VALID_PARTY,
                    documentType = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = VALID_PARTY,
                    requestedFrom = END_USER,
                    requestedTo = END_USER,
                    meta =
                    CreateDocumentBusinessMeta(
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT_1,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                    ),
                )

            val command = handler.validateAndReturnDocumentCommand(model).shouldBeRight()
            command.meta.toMetaAttributes()["requestedFromName"] shouldBe "From"
            command.meta.toMetaAttributes()["language"] shouldBe SupportedLanguage.DEFAULT.code
            command.meta.toMetaAttributes().containsKey("requestedForMeterNumber") shouldBe true
        }
    })
