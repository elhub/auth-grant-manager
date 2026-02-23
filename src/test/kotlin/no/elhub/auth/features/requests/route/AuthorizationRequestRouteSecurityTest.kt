package no.elhub.auth.features.requests.route

import io.kotest.core.spec.style.FunSpec
import io.ktor.server.testing.testApplication
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.http.HttpHeaders
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.setBody
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.auth.validateMissingTokenResponse
import no.elhub.auth.validatePartyNotAuthorizedResponse
import no.elhub.auth.validateUnsupportedPartyResponse

class AuthorizationRequestRouteSecurityTest : FunSpec({
    val pdpContainer = PdpTestContainerExtension()
    extensions(
        AuthPersonsTestContainerExtension,
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grants.sql"),
        pdpContainer
    )

    beforeSpec {
        pdpContainer.registerMaskinportenMapping(
            token = "maskinporten",
            functionName = "BalanceSupplier",
            actingGln = "0107000000021"
        )
        pdpContainer.registerEnduserMapping(
            token = "enduser",
            partyId = "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
        )
        pdpContainer.registerMaskinportenMapping(
            token = "gridowner",
            functionName = "GridOwner",
            actingGln = "0107000000038"
        )
        pdpContainer.registerInvalidTokenMapping()
    }

    context("When token is missing") {
        testApplication {
            setUpAuthorizationRequestTestApplication()
            test("GET /authorization-requests/ returns 401") {
                val response = client.get(REQUESTS_PATH) {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateMissingTokenResponse(response)
            }
            test("GET /authorization-requests/{id} returns 401") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateMissingTokenResponse(response)
            }

            test("POST /authorization-requests/ returns 401") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(
                            PDPAuthorizationProvider.Companion.Headers.SENDER_GLN,
                            "0107000000021"
                        )
                        contentType(ContentType.Application.Json)
                        setBody(examplePostBody)
                    }
                validateMissingTokenResponse(response)
            }
            test("PATCH /authorization-requests/ returns 401") {
                val requestId = insertAuthorizationRequest(
                    properties = mapOf(
                        "requestedFromName" to "Kasper Lind",
                        "requestedForMeteringPointId" to "1234567890555",
                        "requestedForMeteringPointAddress" to "Example Street 2, 0654 Oslo",
                        "balanceSupplierName" to "Power AS",
                        "balanceSupplierContractName" to "ExampleSupplierContract"
                    )
                )
                val response = client.patch("${REQUESTS_PATH}/$requestId") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000038")
                    contentType(ContentType.Application.Json)
                    setBody(examplePatchBody)
                }
                validateMissingTokenResponse(response)
            }
        }
    }
    context("When token is invalid") {
        testApplication {
            setUpAuthorizationRequestTestApplication()
            test("GET /authorization-requests/ returns 401") {
                val response = client.get(REQUESTS_PATH) {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }
                validateInvalidTokenResponse(response)
            }
            test("GET /authorization-requests/{id} returns 401") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }
                validateInvalidTokenResponse(response)
            }

            test("POST /authorization-requests/ returns 401") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(
                            PDPAuthorizationProvider.Companion.Headers.SENDER_GLN,
                            "0107000000021"
                        )
                        header(HttpHeaders.Authorization, "Bearer invalid-token")
                        contentType(ContentType.Application.Json)
                        setBody(examplePostBody)
                    }
                validateInvalidTokenResponse(response)
            }
            test("PATCH /authorization-requests/ returns 401") {
                val requestId = insertAuthorizationRequest(
                    properties = mapOf(
                        "requestedFromName" to "Kasper Lind",
                        "requestedForMeteringPointId" to "1234567890555",
                        "requestedForMeteringPointAddress" to "Example Street 2, 0654 Oslo",
                        "balanceSupplierName" to "Power AS",
                        "balanceSupplierContractName" to "ExampleSupplierContract"
                    )
                )
                val response = client.patch("${REQUESTS_PATH}/$requestId") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000038")
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody(examplePatchBody)
                }
                validateInvalidTokenResponse(response)
            }
        }
    }
    context("Incorrect role or resource ownership") {
        testApplication {
            setUpAuthorizationRequestTestApplication()

            test("GET /authorization-requests/ returns 403 for valid gridowner token") {
                val response = client.get(REQUESTS_PATH) {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    header(HttpHeaders.Authorization, "Bearer gridowner")
                }
                validateUnsupportedPartyResponse(response)
            }
            test("GET /authorization-requests/{id} returns 403 for valid gridowner token") {
                val requestId = insertAuthorizationRequest(
                    properties = mapOf(
                        "requestedFromName" to "Kasper Lind",
                        "requestedForMeteringPointId" to "1234567890555",
                        "requestedForMeteringPointAddress" to "Example Street 2, 0654 Oslo",
                        "balanceSupplierName" to "Power AS",
                        "balanceSupplierContractName" to "ExampleSupplierContract"
                    )
                )
                val response = client.get("$REQUESTS_PATH/$requestId") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    header(HttpHeaders.Authorization, "Bearer gridowner")
                }
                validateUnsupportedPartyResponse(response)
            }
            test("GET /authorization-requests/{id} should return 403 when the request does not belong to the requester using maskinporten token") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validatePartyNotAuthorizedResponse(response)
            }

            test("GET /authorization-requests/{id} should return 403 when the request does not belong to the requester using end user token") {
                val response = client.get("$REQUESTS_PATH/3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47") {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                }
                validatePartyNotAuthorizedResponse(response)
            }
        }
    }
})
