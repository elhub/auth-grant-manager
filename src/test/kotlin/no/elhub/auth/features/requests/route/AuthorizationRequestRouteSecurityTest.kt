package no.elhub.auth.features.requests.route

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.auth.validateMissingTokenResponse
import no.elhub.auth.validateUnsupportedPartyResponse
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.create.dto.CreateRequestAttributes
import no.elhub.auth.features.requests.create.dto.CreateRequestMeta
import no.elhub.auth.features.requests.create.dto.JsonApiCreateRequest
import no.elhub.auth.features.requests.update.dto.JsonApiUpdateRequest
import no.elhub.auth.features.requests.update.dto.UpdateRequestAttributes
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

class AuthorizationRequestRouteSecurityTest : FunSpec({
    val pdpContainer = PdpTestContainerExtension()
    extensions(
        AuthPersonsTestContainerExtension,
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
        pdpContainer
    )

    beforeSpec {
        pdpContainer.registerMaskinportenMapping(
            token = "maskinporten",
            actingFunction = "BalanceSupplier",
            actingGln = "0107000000021"
        )
        pdpContainer.registerEnduserMapping(
            token = "enduser",
            partyId = "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
        )
        pdpContainer.registerMaskinportenMapping(
            token = "gridowner",
            actingFunction = "GridOwner",
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
    context("Incorrect role") {
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
        }
    }
})

private val examplePostBody = JsonApiCreateRequest(
    data = JsonApiRequestResourceObjectWithMeta(
        type = "AuthorizationRequest",
        attributes =
            CreateRequestAttributes(requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson),
        meta = CreateRequestMeta(
            requestedBy = PartyIdentifier(
                PartyIdentifierType.GlobalLocationNumber,
                "0107000000021"
            ),
            requestedFrom = PartyIdentifier(
                PartyIdentifierType.NationalIdentityNumber,
                REQUESTED_FROM_NIN,
            ),
            requestedFromName = "Hillary Orr",
            requestedTo = PartyIdentifier(
                PartyIdentifierType.NationalIdentityNumber,
                REQUESTED_TO_NIN
            ),
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeteringPointAddress = "quaerendum",
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Selena Chandler",
            redirectURI = "https://example.com/redirect",
        ),
    ),
)

private val examplePatchBody = JsonApiUpdateRequest(
    data = JsonApiRequestResourceObject(
        type = "AuthorizationRequest",
        attributes = UpdateRequestAttributes(
            status = AuthorizationRequest.Status.Accepted
        )
    )
)

