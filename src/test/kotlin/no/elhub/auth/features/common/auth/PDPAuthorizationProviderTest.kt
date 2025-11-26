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
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json

typealias MachineHeaders = PDPAuthorizationProvider.Companion.Headers

const val TOKEN_VALID_FORMAT = "Bearer something"
const val TOKEN_INVALID_FORMAT = "Bear something"

val validHeadersForMaskinporten = headersOf(
    HttpHeaders.Authorization to listOf(TOKEN_VALID_FORMAT),
    MachineHeaders.SENDER_GLN to listOf("0107000000021"),
    MachineHeaders.ON_BEHALF_OF_GLN to listOf("0107000000020")
)

class PDPAuthorizationProviderTest : FunSpec({

    context("Given authorization header is wrong, validate Provider propagates proper error") {
        test("When authorization header is missing, Provider returns MissingAuthorizationHeader") {

            val response = authorizeWith(
                headers = Headers.Empty
            )

            response.shouldBeLeft(AuthError.MissingAuthorizationHeader)
        }

        test("When authorization header format is invalid, Provider returns InvalidAuthorizationHeader") {
            val response = authorizeWith(
                headersOf(
                    HttpHeaders.Authorization to listOf(TOKEN_INVALID_FORMAT),
                )
            )

            response.shouldBeLeft(AuthError.InvalidAuthorizationHeader)
        }
    }

    context("Given required headers are missing, validate Provider propagates proper error") {
        test("When senderGLN header is missing, Provider returns MissingSenderGlnHeader") {
            val response = authorizeWith(
                headersOf(
                    HttpHeaders.Authorization to listOf(TOKEN_VALID_FORMAT),
                )
            )

            response.shouldBeLeft(AuthError.MissingSenderGlnHeader)
        }
    }

    context("Given getting valid response from PDP, validate PDPAuthorizationProvider responds properly") {
        test("When PDP returns tokenStatus != 'verified', Provider returns InvalidToken") {
            val pdpResponse = Json.encodeToString(
                PDPAuthorizationProvider.PdpResponse(
                    result = PDPAuthorizationProvider.PdpResponse.Result(
                        tokenInfo = TokenInfo(
                            tokenStatus = "wrong"
                        )
                    )
                )
            )
            val response = authorizeWith(
                headers = validHeadersForMaskinporten,
                clientResponseJson = pdpResponse
            )
            response.shouldBeLeft(AuthError.InvalidToken)
        }

        test("When PDP returns unexpected tokenType, Provider returns InvalidToken") {
            val pdpResponse = Json.encodeToString(
                PDPAuthorizationProvider.PdpResponse(
                    result = PDPAuthorizationProvider.PdpResponse.Result(
                        tokenInfo = TokenInfo(
                            tokenStatus = "verified",
                            tokenType = "random"
                        )
                    )
                )
            )

            val response = authorizeWith(
                headers = validHeadersForMaskinporten,
                clientResponseJson = pdpResponse
            )

            response.shouldBeLeft(AuthError.InvalidToken)
        }

        test("When PDP returns an authorized maskinporten decision, Provider returns ResolvedActor") {
            val pdpResponse = Json.encodeToString(
                PDPAuthorizationProvider.PdpResponse(
                    result = PDPAuthorizationProvider.PdpResponse.Result(
                        tokenInfo = TokenInfo(
                            tokenStatus = "verified",
                            tokenType = "maskinporten"
                        ),
                        authInfo = AuthInfo(
                            actingFunction = "BalanceSupplier",
                            actingGLN = "0107000000021"
                        )
                    )
                )
            )
            val response = authorizeWith(
                headers = validHeadersForMaskinporten,
                clientResponseJson = pdpResponse
            )
            response.shouldBeRight(ResolvedActor(gln = "0107000000021", role = RoleType.BalanceSupplier))
        }
    }

    context("Given getting invalid response from PDP, validate Provider propagates proper error") {
        test("When PDP response is missing actingFunction, Provider returns ActingFunctionMissing") {
            val pdpResponse = Json.encodeToString(
                PDPAuthorizationProvider.PdpResponse(
                    result = PDPAuthorizationProvider.PdpResponse.Result(
                        tokenInfo = TokenInfo(
                            tokenStatus = "verified",
                            tokenType = "maskinporten"
                        ),
                        authInfo = AuthInfo(
                            actingFunction = null,
                            actingGLN = "0107000000021"
                        )
                    )
                )
            )
            val response = authorizeWith(
                headers = validHeadersForMaskinporten,
                clientResponseJson = pdpResponse
            )
            response.shouldBeLeft(AuthError.ActingFunctionMissing)
        }
        test("When PDP response is missing actingGln, Provider returns ActingGlnMissing") {
            val pdpResponse = Json.encodeToString(
                PDPAuthorizationProvider.PdpResponse(
                    result = PDPAuthorizationProvider.PdpResponse.Result(
                        tokenInfo = TokenInfo(
                            tokenStatus = "verified",
                            tokenType = "maskinporten"
                        ),
                        authInfo = AuthInfo(
                            actingFunction = "BalanceSupplier",
                            actingGLN = null
                        )
                    )
                )
            )
            val response = authorizeWith(
                headers = validHeadersForMaskinporten,
                clientResponseJson = pdpResponse
            )
            response.shouldBeLeft(AuthError.ActingGlnMissing)
        }

        test("When PDP response includes 'input failed:', Provider returns UnkownError") {

            val pdpResponse = Json.encodeToString(
                PDPAuthorizationProvider.PdpResponse(
                    result = PDPAuthorizationProvider.PdpResponse.Result(
                        tokenInfo = TokenInfo(
                            tokenStatus = "verified",
                            tokenType = "maskinporten"
                        ),
                        authInfo = AuthInfo(
                            inputFailed = "some missing stuff"
                        )
                    )
                )
            )

            val response = authorizeWith(
                headers = validHeadersForMaskinporten,
                clientResponseJson = pdpResponse
            )
            response.shouldBeLeft(AuthError.UnknownError)
        }

        test("When PDP response includes unsupported actingFunction, Provider returns ActingFunctionNotSupported") {
            val pdpResponse = Json.encodeToString(
                PDPAuthorizationProvider.PdpResponse(
                    result = PDPAuthorizationProvider.PdpResponse.Result(
                        tokenInfo = TokenInfo(
                            tokenStatus = "verified",
                            tokenType = "maskinporten"
                        ),
                        authInfo = AuthInfo(
                            actingFunction = "UnknownRole",
                            actingGLN = "0107000000021"
                        )
                    )
                )
            )

            val response = authorizeWith(
                headers = validHeadersForMaskinporten,
                clientResponseJson = pdpResponse
            )

            response.shouldBeLeft(AuthError.ActingFunctionNotSupported)
        }
    }
})

suspend fun authorizeWith(
    headers: Headers,
    clientResponseJson: String = ""
): Either<AuthError, ResolvedActor> {
    // Mock engine returning your desired JSON
    val engine = MockEngine { _ ->
        respond(
            content = clientResponseJson,
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString()
            )
        )
    }

    // Client using the mock engine
    val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val request = mockk<ApplicationRequest>()
    every { request.headers } returns headers
    val call = mockk<ApplicationCall>()
    every { call.request } returns request

    // Use your real provider implementation
    val provider = PDPAuthorizationProvider(
        httpClient = client,
        pdpBaseUrl = "http://mock.pdp"
    )

    return provider.authorizeMarketParty(call)
}
