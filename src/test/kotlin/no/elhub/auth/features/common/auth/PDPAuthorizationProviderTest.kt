package no.elhub.auth.features.common.auth

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.util.Attributes
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import java.util.UUID

typealias MachineHeaders = PDPAuthorizationProvider.Companion.Headers

const val TOKEN_VALID_FORMAT = "Bearer maskinporten"
const val TOKEN_INVALID_FORMAT = "Bear something"

val validHeadersForMaskinporten = headersOf(
    HttpHeaders.Authorization to listOf(TOKEN_VALID_FORMAT),
    MachineHeaders.SENDER_GLN to listOf("0107000000021"),
    MachineHeaders.ON_BEHALF_OF_GLN to listOf("0107000000020")
)

class PDPAuthorizationProviderTest : FunSpec({

    context("authorizeEndUserOrMaskinporten") {
        test("returns AuthorizationParty when PDP reports maskinporten token") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUserOrMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse()
            )

            response.shouldBeRight(
                AuthorizationParty(id = "0107000000021", type = PartyType.OrganizationEntity)
            )
        }

        test("returns AuthorizationParty when PDP reports enduser token") {
            val partyId = "a8098c1a-f86e-11da-bd1a-00112444be1e"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUserOrMaskinporten,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(partyId = partyId)
            )

            response.shouldBeRight(AuthorizationParty(id = UUID.fromString(partyId).toString(), type = PartyType.Person))
        }

        test("returns InvalidToken for unsupported tokenType") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUserOrMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(tokenType = "unknown")
            )

            response.shouldBeLeft(AuthError.InvalidToken)
        }
    }

    context("authorizeAll") {
        test("returns AuthorizationParty when PDP reports maskinporten token") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeAll,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse()
            )

            response.shouldBeRight(
                AuthorizationParty(id = "0107000000021", type = PartyType.OrganizationEntity)
            )
        }

        test("returns AuthorizationParty when PDP reports enduser token") {
            val partyId = "a8098c1a-f86e-11da-bd1a-00112444be1e"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeAll,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(partyId = partyId)
            )

            response.shouldBeRight(AuthorizationParty(id = UUID.fromString(partyId).toString(), type = PartyType.Person))
        }

        test("returns AuthorizationParty when PDP reports elhub-service token") {
            val partyId = "system-123"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeAll,
                headers = authorizationOnlyHeaders(),
                pdpResponse = elhubServiceResponse(partyId = partyId)
            )

            response.shouldBeRight(AuthorizationParty(id = partyId, type = PartyType.System))
        }

        test("returns InvalidToken for unsupported tokenType") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeAll,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(tokenType = "unknown")
            )

            response.shouldBeLeft(AuthError.InvalidToken)
        }
    }

    context("authorizeMaskinporten") {
        test("returns MissingAuthorizationHeader when authorization header is missing") {

            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = Headers.Empty
            )

            response.shouldBeLeft(AuthError.MissingAuthorizationHeader)
        }

        test("returns InvalidAuthorizationHeader when authorization header format is invalid") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headersOf(
                    HttpHeaders.Authorization to listOf(TOKEN_INVALID_FORMAT),
                )
            )

            response.shouldBeLeft(AuthError.InvalidAuthorizationHeader)
        }

        test("returns MissingSenderGlnHeader when senderGLN header is missing") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = authorizationOnlyHeaders(),
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        authorizedFunctions = listOf(
                            AuthorizedFunction(
                                functionCode = "SELF",
                                functionName = RoleType.BalanceSupplier.name
                            )
                        ),
                        actingGLN = "0107000000021"
                    )
                )
            )

            response.shouldBeLeft(AuthError.MissingSenderGlnHeader)
        }

        test("returns InvalidToken when tokenStatus is not verified") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(tokenStatus = "wrong")
            )
            response.shouldBeLeft(AuthError.InvalidToken)
        }

        test("returns Forbidden when tokenType is not maskinporten") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(tokenType = "random")
            )

            response.shouldBeLeft(AuthError.AccessDenied)
        }

        test("returns AuthorizationParty for authorized maskinporten decision") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse()
            )
            response.shouldBeRight(AuthorizationParty(id = "0107000000021", type = PartyType.OrganizationEntity))
        }

        test("returns InvalidPdpResponseActingFunctionMissing when PDP response lacks authorizedFunctions") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        authorizedFunctions = null,
                        actingGLN = "0107000000021"
                    )
                )
            )
            response.shouldBeLeft(AuthError.InvalidPdpResponseAuthorizedFunctionsMissing)
        }
        test("returns InvalidPdpResponseActingGlnMissing when PDP response lacks actingGln") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        authorizedFunctions = listOf(
                            AuthorizedFunction(
                                functionCode = "SELF",
                                functionName = RoleType.BalanceSupplier.name
                            )
                        ),
                        actingGLN = null
                    )
                )
            )
            response.shouldBeLeft(AuthError.InvalidPdpResponseActingGlnMissing)
        }

        test("returns Forbidden when PDP response includes input failed") {

            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        inputFailed = "some missing stuff"
                    )
                )
            )
            response.shouldBeLeft(AuthError.AccessDenied)
        }

        test("returns ActingFunctionNotSupported when PDP response includes unsupported authorizedFunctions") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        authorizedFunctions = listOf(
                            AuthorizedFunction(
                                functionCode = "SELF",
                                functionName = "UnknownRole"
                            )
                        ),
                        actingGLN = "0107000000021"
                    )
                )
            )

            response.shouldBeLeft(AuthError.ActingFunctionNotSupported)
        }
    }

    context("authorizeEndUser") {

        test("returns ActingFunctionNotSupported when tokenType is not enduser and has a valid token") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = maskinportenResponse()
            )

            response.shouldBeLeft(AuthError.ActingFunctionNotSupported)
        }

        test("returns InvalidToken when tokenType is not enduser and has a invalid tokenStatus") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = maskinportenResponse(tokenStatus = "wrong")
            )
            response.shouldBeLeft(AuthError.InvalidToken)
        }

        test("returns UnexpectedPdpError when partyId is missing") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(partyId = null)
            )

            response.shouldBeLeft(AuthError.UnexpectedPdpError)
        }

        test("returns AuthorizedPerson when partyId is present") {
            val partyId = "a8098c1a-f86e-11da-bd1a-00112444be1e"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(partyId = partyId)
            )

            response.shouldBeRight(AuthorizationParty(id = UUID.fromString(partyId).toString(), type = PartyType.Person))
        }

        test("returns AuthorizedPerson with actingId when authInfo contains actingType=person") {
            val actingId = "e76439bc-0344-4ef5-b2f8-41a72c25ffd8"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(
                    authInfo = AuthInfo(actingType = ActingType.Person, actingId = actingId)
                )
            )

            response.shouldBeRight(AuthorizationParty(id = actingId, type = PartyType.Person))
        }

        test("returns UnexpectedPdpError when authInfo.actingType is person but actingId is missing") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(
                    authInfo = AuthInfo(actingType = ActingType.Person, actingId = null)
                )
            )

            response.shouldBeLeft(AuthError.UnexpectedPdpError)
        }

        test("returns AuthorizedOrganization when authInfo contains actingType=organisation") {
            val orgNumber = "306137018"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(
                    authInfo = AuthInfo(
                        actingType = ActingType.Organisation,
                        actingId = "b8af0fb3-bad7-4ca4-9153-919243635601",
                        actingOrganisationNumber = orgNumber,
                        originalId = "e76439bc-0344-4ef5-b2f8-41a72c25ffd8"
                    )
                )
            )

            response.shouldBeRight(AuthorizationParty(id = orgNumber, type = PartyType.Organization))
        }

        test("returns UnexpectedPdpError when authInfo.actingType is organisation but actingOrganisationNumber is missing") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(
                    authInfo = AuthInfo(actingType = ActingType.Organisation, actingOrganisationNumber = null)
                )
            )

            response.shouldBeLeft(AuthError.UnexpectedPdpError)
        }

        test("returns OnBehalfOfOrganisationVerificationFailed when PDP authInfo contains error") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(
                    authInfo = AuthInfo(
                        actingId = "",
                        actingType = null,
                        error = "Unable to verify OnBehalfOfOrganisationId for end user token",
                        originalId = "e76439bc-0344-4ef5-b2f8-41a72c25ffd8"
                    )
                )
            )

            response.shouldBeLeft(AuthError.EndUserOnBehalfOfOrganisationVerificationFailed)
        }
    }

    context("authorizeElhubService") {
        test("returns Forbidden when tokenType is not elhub-service") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeElhubService,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse()
            )

            response.shouldBeLeft(AuthError.AccessDenied)
        }

        test("returns UnexpectedPdpError when partyId is missing") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeElhubService,
                headers = authorizationOnlyHeaders(),
                pdpResponse = elhubServiceResponse(partyId = null)
            )

            response.shouldBeLeft(AuthError.UnexpectedPdpError)
        }

        test("returns AuthorizedSystem when partyId is present") {
            val partyId = "system-123"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeElhubService,
                headers = authorizationOnlyHeaders(),
                pdpResponse = elhubServiceResponse(partyId = partyId)
            )

            response.shouldBeRight(AuthorizationParty(id = partyId, type = PartyType.System))
        }
    }
})

private val json = Json { ignoreUnknownKeys = true }

private fun mockCall(headers: Headers): ApplicationCall {
    val request = mockk<ApplicationRequest>()
    every { request.headers } returns headers
    val call = mockk<ApplicationCall>()
    every { call.attributes } returns Attributes()
    every { call.request } returns request
    return call
}

private suspend fun <T> runProviderMethod(
    method: suspend PDPAuthorizationProvider.(ApplicationCall) -> Either<AuthError, T>,
    headers: Headers,
    pdpResponse: PdpResponse = maskinportenResponse(),
    status: HttpStatusCode = HttpStatusCode.OK
): Either<AuthError, T> {
    val client = HttpClient(
        MockEngine { _ ->
            respond(
                content = json.encodeToString(pdpResponse),
                status = status,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            )
        }
    ) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    val provider = PDPAuthorizationProvider(
        httpClient = client,
        pdpBaseUrl = "http://mock.pdp"
    )

    return provider.method(mockCall(headers))
}

private fun authorizationOnlyHeaders(authorization: String = TOKEN_VALID_FORMAT) = headersOf(
    HttpHeaders.Authorization to listOf(authorization),
)

private fun maskinportenResponse(
    tokenStatus: String = "verified",
    tokenType: String? = TokenType.MASKINPORTEN.value,
    authInfo: AuthInfo? = AuthInfo(
        authorizedFunctions = listOf(
            AuthorizedFunction(
                functionCode = "SELF",
                functionName = RoleType.BalanceSupplier.name
            )
        ),
        actingGLN = "0107000000021"
    )
) = PdpResponse(
    result = Result(
        tokenInfo = TokenInfo(
            tokenStatus = tokenStatus,
            tokenType = tokenType
        ),
        authInfo = authInfo
    )
)

private fun endUserResponse(
    tokenStatus: String = "verified",
    partyId: String? = "a8098c1a-f86e-11da-bd1a-00112444be1e",
    tokenType: String? = TokenType.ENDUSER.value,
    authInfo: AuthInfo? = null,
) = PdpResponse(
    result = Result(
        tokenInfo = TokenInfo(
            tokenStatus = tokenStatus,
            partyId = partyId,
            tokenType = tokenType
        ),
        authInfo = authInfo
    )
)

private fun elhubServiceResponse(
    tokenStatus: String = "verified",
    partyId: String? = "system-123",
    tokenType: String? = TokenType.ELHUB_SERVICE.value,
) = PdpResponse(
    result = Result(
        tokenInfo = TokenInfo(
            tokenStatus = tokenStatus,
            partyId = partyId,
            tokenType = tokenType
        )
    )
)
