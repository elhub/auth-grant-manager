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

typealias MachineHeaders = PDPAuthorizationProvider.Companion.Headers

const val TOKEN_VALID_FORMAT = "Bearer maskinporten"
const val TOKEN_INVALID_FORMAT = "Bear something"

val validHeadersForMaskinporten = headersOf(
    HttpHeaders.Authorization to listOf(TOKEN_VALID_FORMAT),
    MachineHeaders.SENDER_GLN to listOf("0107000000021"),
    MachineHeaders.ON_BEHALF_OF_GLN to listOf("0107000000020")
)

class PDPAuthorizationProviderTest : FunSpec({

    context("authorize") {

        // --- Common header validation ---

        test("returns MissingAuthorizationHeader when authorization header is missing") {
            val response = runAuthorize(headers = Headers.Empty)
            response.shouldBeLeft(AuthError.MissingAuthorizationHeader)
        }

        test("returns InvalidAuthorizationHeader when authorization header format is invalid") {
            val response = runAuthorize(
                headers = headersOf(HttpHeaders.Authorization to listOf(TOKEN_INVALID_FORMAT))
            )
            response.shouldBeLeft(AuthError.InvalidAuthorizationHeader)
        }

        test("returns InvalidToken when tokenStatus is not verified") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = json.encodeToString(maskinportenResponse(tokenStatus = "wrong"))
            )
            response.shouldBeLeft(AuthError.InvalidToken)
        }

        test("returns InvalidToken for unsupported tokenType") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = json.encodeToString(maskinportenResponse(tokenType = "unknown"))
            )
            response.shouldBeLeft(AuthError.InvalidToken)
        }

        test("returns UnexpectedPdpError when PDP response is not valid") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = """{"not": "a valid pdp response"}"""
            )
            response.shouldBeLeft(AuthError.UnexpectedPdpError)
        }

        // --- Maskinporten token ---

        test("returns AuthorizationParty(OrganizationEntity) for valid maskinporten token") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = json.encodeToString(maskinportenResponse())
            )
            response.shouldBeRight(AuthorizationParty(id = "0107000000021", type = PartyType.OrganizationEntity))
        }

        test("returns MissingSenderGlnHeader when senderGLN header is missing for maskinporten token") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(
                    maskinportenResponse(
                        authInfo = AuthInfo(
                            authorizedFunctions = listOf(
                                AuthorizedFunction(functionCode = "SELF", functionName = RoleType.BalanceSupplier.name)
                            ),
                            actingGLN = "0107000000021"
                        )
                    )
                )
            )
            response.shouldBeLeft(AuthError.MissingSenderGlnHeader)
        }

        test("returns AccessDenied when maskinporten PDP response includes inputFailed") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = json.encodeToString(
                    maskinportenResponse(authInfo = AuthInfo(inputFailed = "some missing stuff"))
                )
            )
            response.shouldBeLeft(AuthError.AccessDenied)
        }

        test("returns InvalidPdpResponseActingGlnMissing when maskinporten response lacks actingGLN") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = json.encodeToString(
                    maskinportenResponse(
                        authInfo = AuthInfo(
                            authorizedFunctions = listOf(
                                AuthorizedFunction(functionCode = "SELF", functionName = RoleType.BalanceSupplier.name)
                            ),
                            actingGLN = null
                        )
                    )
                )
            )
            response.shouldBeLeft(AuthError.InvalidPdpResponseActingGlnMissing)
        }

        test("returns InvalidPdpResponseAuthorizedFunctionsMissing when maskinporten response lacks authorizedFunctions") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = json.encodeToString(
                    maskinportenResponse(
                        authInfo = AuthInfo(authorizedFunctions = null, actingGLN = "0107000000021")
                    )
                )
            )
            response.shouldBeLeft(AuthError.InvalidPdpResponseAuthorizedFunctionsMissing)
        }

        test("returns ActingFunctionNotSupported when maskinporten authorizedFunctions contains no BalanceSupplier") {
            val response = runAuthorize(
                headers = validHeadersForMaskinporten,
                responseBody = json.encodeToString(
                    maskinportenResponse(
                        authInfo = AuthInfo(
                            authorizedFunctions = listOf(
                                AuthorizedFunction(functionCode = "SELF", functionName = "UnknownRole")
                            ),
                            actingGLN = "0107000000021"
                        )
                    )
                )
            )
            response.shouldBeLeft(AuthError.ActingFunctionNotSupported)
        }

        // --- EndUser token ---

        test("returns AuthorizationParty(Person) for enduser token with actingType=person") {
            val actingId = "e76439bc-0344-4ef5-b2f8-41a72c25ffd8"
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(
                    endUserResponse(authInfo = AuthInfo(actingType = "person", actingId = actingId))
                )
            )
            response.shouldBeRight(AuthorizationParty(id = actingId, type = PartyType.Person))
        }

        test("returns AuthorizationParty(Organization) for enduser token with actingType=organisation") {
            val orgNumber = "306137018"
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(
                    endUserResponse(
                        authInfo = AuthInfo(
                            actingType = "organisation",
                            actingId = "b8af0fb3-bad7-4ca4-9153-919243635601",
                            actingOrganisationNumber = orgNumber,
                        )
                    )
                )
            )
            response.shouldBeRight(AuthorizationParty(id = orgNumber, type = PartyType.Organization))
        }

        test("returns AccessDenied when enduser actingType is missing") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(endUserResponse())
            )
            response.shouldBeLeft(AuthError.AccessDenied)
        }

        test("returns AccessDenied when enduser actingType is blank without authInfo error") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(
                    endUserResponse(authInfo = AuthInfo(actingType = "", actingId = ""))
                )
            )
            response.shouldBeLeft(AuthError.AccessDenied)
        }

        test("returns UnexpectedPdpError when enduser actingType is person but actingId is missing") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(
                    endUserResponse(authInfo = AuthInfo(actingType = "person", actingId = null))
                )
            )
            response.shouldBeLeft(AuthError.UnexpectedPdpError)
        }

        test("returns UnexpectedPdpError when enduser actingType is organisation but actingOrganisationNumber is missing") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(
                    endUserResponse(authInfo = AuthInfo(actingType = "organisation", actingOrganisationNumber = null))
                )
            )
            response.shouldBeLeft(AuthError.UnexpectedPdpError)
        }

        test("returns EndUserOnBehalfOfOrganisationVerificationFailed when PDP authInfo contains error") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(
                    endUserResponse(
                        authInfo = AuthInfo(
                            actingId = "",
                            actingType = null,
                            error = "Unable to verify OnBehalfOfOrganisationId for end user token",
                        )
                    )
                )
            )
            response.shouldBeLeft(AuthError.EndUserOnBehalfOfOrganisationVerificationFailed)
        }

        test("deserializes blank actingType as null and returns EndUserOnBehalfOfOrganisationVerificationFailed") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = """
                {
                  "result": {
                    "authInfo": {
                      "actingId": "",
                      "actingType": "",
                      "error": "Unable to verify OnBehalfOfOrganisationId for end user token",
                      "originalId": "e76439bc-0344-4ef5-b2f8-41a72c25ffd8"
                    },
                    "tokenInfo": {
                      "partyId": "e76439bc-0344-4ef5-b2f8-41a72c25ffd8",
                      "tokenScope": "undefined",
                      "tokenStatus": "verified",
                      "tokenType": "enduser"
                    }
                  }
                }
                """.trimIndent()
            )
            response.shouldBeLeft(AuthError.EndUserOnBehalfOfOrganisationVerificationFailed)
        }

        // --- Elhub service token ---

        test("returns AuthorizationParty(System) for valid elhub-service token") {
            val partyId = "system-123"
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(elhubServiceResponse(partyId = partyId))
            )
            response.shouldBeRight(AuthorizationParty(id = partyId, type = PartyType.System))
        }

        test("returns UnexpectedPdpError when elhub-service token has no partyId") {
            val response = runAuthorize(
                headers = authorizationOnlyHeaders(),
                responseBody = json.encodeToString(elhubServiceResponse(partyId = null))
            )
            response.shouldBeLeft(AuthError.UnexpectedPdpError)
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

private suspend fun runAuthorize(
    headers: Headers,
    responseBody: String = json.encodeToString(maskinportenResponse()),
    status: HttpStatusCode = HttpStatusCode.OK
): Either<AuthError, AuthorizationParty> {
    val client = HttpClient(
        MockEngine { _ ->
            respond(
                content = responseBody,
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

    return provider.authorize(mockCall(headers))
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
