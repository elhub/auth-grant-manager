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

    context("authorize") {
        test("returns AuthorizedOrganizationEntity when PDP reports maskinporten token") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorize,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse()
            )

            response.shouldBeRight(
                AuthorizedParty.AuthorizedOrganizationEntity(
                    gln = "0107000000021",
                    role = RoleType.BalanceSupplier
                )
            )
        }

        test("returns AuthorizedPerson when PDP reports enduser token") {
            val partyId = "a8098c1a-f86e-11da-bd1a-00112444be1e"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorize,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(partyId = partyId)
            )

            response.shouldBeRight(AuthorizedParty.AuthorizedPerson(UUID.fromString(partyId)))
        }

        test("returns InvalidToken for unsupported tokenType") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorize,
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
                        actingFunction = RoleType.BalanceSupplier.name,
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

        test("returns NotAuthorized when tokenType is not maskinporten") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(tokenType = "random")
            )

            response.shouldBeLeft(AuthError.NotAuthorized)
        }

        test("returns AuthorizedOrganizationEntity for authorized maskinporten decision") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse()
            )
            response.shouldBeRight(
                AuthorizedParty.AuthorizedOrganizationEntity(
                    gln = "0107000000021",
                    role = RoleType.BalanceSupplier
                )
            )
        }

        test("returns ActingFunctionMissing when PDP response lacks actingFunction") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        actingFunction = null,
                        actingGLN = "0107000000021"
                    )
                )
            )
            response.shouldBeLeft(AuthError.ActingFunctionMissing)
        }
        test("returns ActingGlnMissing when PDP response lacks actingGln") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        actingFunction = RoleType.BalanceSupplier.name,
                        actingGLN = null
                    )
                )
            )
            response.shouldBeLeft(AuthError.ActingGlnMissing)
        }

        test("returns UnknownError when PDP response includes input failed") {

            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        inputFailed = "some missing stuff"
                    )
                )
            )
            response.shouldBeLeft(AuthError.UnknownError)
        }

        test("returns ActingFunctionNotSupported when PDP response includes unsupported actingFunction") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeMaskinporten,
                headers = validHeadersForMaskinporten,
                pdpResponse = maskinportenResponse(
                    authInfo = AuthInfo(
                        actingFunction = "UnknownRole",
                        actingGLN = "0107000000021"
                    )
                )
            )

            response.shouldBeLeft(AuthError.ActingFunctionNotSupported)
        }
    }

    context("authorizePerson") {
        test("returns NotAuthorized when tokenType is not enduser") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = maskinportenResponse()
            )

            response.shouldBeLeft(AuthError.NotAuthorized)
        }

        test("returns UnknownError when partyId is missing") {
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(partyId = null)
            )

            response.shouldBeLeft(AuthError.UnknownError)
        }

        test("returns AuthorizedPerson when partyId is present") {
            val partyId = "a8098c1a-f86e-11da-bd1a-00112444be1e"
            val response = runProviderMethod(
                method = PDPAuthorizationProvider::authorizeEndUser,
                headers = authorizationOnlyHeaders(),
                pdpResponse = endUserResponse(partyId = partyId)
            )

            response.shouldBeRight(AuthorizedParty.AuthorizedPerson(UUID.fromString(partyId)))
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
        actingFunction = RoleType.BalanceSupplier.name,
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
) = PdpResponse(
    result = Result(
        tokenInfo = TokenInfo(
            tokenStatus = tokenStatus,
            partyId = partyId,
            tokenType = tokenType
        )
    )
)
