package no.elhub.auth.v1.acceptance.support

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import no.elhub.auth.common.auth.AUTHINFO_POLICY_ROUTE

/**
 * Fakes for the HTTP services the application calls, backed by one WireMock container.
 *
 * Every test starts from [reset]: only the IDP token endpoint answers, and the PDP denies everything.
 * Tests then describe the outside world they need through service and endpoint-specific stubs.
 */
class ExternalServiceStubs(private val baseUrl: String) {
    private val admin = HttpClient(Apache5)

    val organizationsService = Organizations()
    val pdpService = Pdp()
    val authPersonsService = Persons()
    val edielService = Ediel()

    inner class Organizations {
        /** The organisations service knows [supplier] (by GLN) as a balance supplier with its name. */
        suspend fun stubPartyByIdAndType(supplier: EnergySupplier) =
            registerSupplier(supplier.gln, requireNotNull(supplier.name) { "supplier needs a name" })

        /** The organisations service (Brreg) knows [organization] with its name. */
        suspend fun stubBrregOrganizationByNumber(organization: Organization) =
            registerOrganization(organization.number, requireNotNull(organization.name) { "organization needs a name" })
    }

    inner class Pdp {
        /** The PDP accepts [supplier]'s token with its sender GLN. */
        suspend fun stubAuthInfo(supplier: EnergySupplier) = registerMarketEntity(supplier.caller())

        /** The PDP accepts [person]'s token, as the person or on behalf of [onBehalfOf]. */
        suspend fun stubAuthInfo(person: Person, onBehalfOf: Organization? = null) =
            registerEndUser(person.caller(onBehalfOf))

        /** The PDP lets [approver] approve for [requestedOf] on behalf of [principal]. No other combination is allowed. */
        suspend fun stubAgentApproverDecision(approver: Person, requestedOf: Organization, principal: Organization) =
            this@ExternalServiceStubs.allowApprover(approver.id, requestedOf.number, principal.number)

        /** The PDP lets [requestedOf] act on behalf of [principal]. No other combination is allowed. */
        suspend fun stubAgentRepresentativeDecision(requestedOf: Organization, principal: Organization) =
            this@ExternalServiceStubs.allowRepresentative(requestedOf.number, principal.number)
    }

    inner class Persons {
        /** Auth-persons resolves [person]'s national identity number to their person ID. */
        suspend fun stubPersonByNationalIdentityNumber(person: Person) =
            registerPerson(requireNotNull(person.nationalIdentityNumber) { "person needs a nationalIdentityNumber" }, person.id)
    }

    inner class Ediel {
        /** Ediel has [supplier]'s redirect URL registered for its GLN. */
        suspend fun stubPartyRedirectUrl(supplier: EnergySupplier) =
            registerEdielRedirect(supplier.gln, requireNotNull(supplier.redirectUrl) { "supplier needs a redirectUrl" })

        /** Ediel has no redirect URL for [supplier]'s GLN (404). */
        suspend fun stubPartyRedirectUrlNotFound(supplier: EnergySupplier) = registerEdielFailure(supplier.gln, status = 404)

        /** Ediel fails when asked about [supplier]'s GLN (500). */
        suspend fun stubPartyRedirectUrlUnavailable(supplier: EnergySupplier) = registerEdielFailure(supplier.gln, status = 500)
    }

    suspend fun reset() {
        for (endpoint in listOf("/__admin/mappings", "/__admin/requests")) {
            val response = admin.delete("$baseUrl$endpoint")
            check(response.status.isSuccess()) { "Could not reset WireMock $endpoint: ${response.status} ${response.bodyAsText()}" }
        }
        stub(
            priority = 1,
            request = {
                put("method", "POST")
                put("urlPath", IDP_TOKEN_PATH)
            },
            response = """{"access_token":"$SERVICE_TOKEN","expires_in":3600}""",
        )
        // Anything not explicitly allowed is denied, like the real PDP.
        for (route in listOf(AGENT_APPROVER_ROUTE, AGENT_REPRESENTATIVE_ROUTE)) {
            stub(
                priority = 10,
                request = {
                    put("method", "POST")
                    put("urlPath", route)
                },
                response = pdpDecision(authorized = false),
            )
        }
        stub(
            priority = 10,
            request = {
                put("method", "POST")
                put("urlPath", AUTHINFO_POLICY_ROUTE)
            },
            response = """{"result":{"tokenInfo":{"tokenStatus":"invalid"}}}""",
        )
    }

    /** A Maskinporten token is accepted only for the GLN context registered for this market entity. */
    private suspend fun registerMarketEntity(caller: Caller.MarketEntity) {
        rejectUnexpectedContext(caller.token, caller.organizationNumber, "maskinporten")
        registerAuthInfo(
            caller.token,
            mapOf("SenderGLN" to requireNotNull(caller.senderGln)) +
                listOfNotNull(caller.onBehalfOfGln?.let { "OnBehalfOfGLN" to it }),
            """
        {"result":{
          "tokenInfo":{"tokenStatus":"verified","partyId":"${caller.organizationNumber}","tokenType":"maskinporten"},
          "authInfo":{"authorizedFunctions":[{"functionCode":"SELF","functionName":"BalanceSupplier"}],"actingGLN":"${caller.senderGln}"}
        }}
        """,
        )
    }

    /** An end-user token resolves to the person, or to the organization when registered with a delegation. */
    private suspend fun registerEndUser(caller: Caller.EndUser) {
        rejectUnexpectedContext(caller.token, caller.personId, "enduser")
        if (caller.onBehalfOfOrganization == null) {
            registerAuthInfo(
                caller.token,
                emptyMap(),
                """
            {"result":{
              "tokenInfo":{"tokenStatus":"verified","partyId":"${caller.personId}","tokenType":"enduser"},
              "authInfo":{"actingType":"person","actingId":"${caller.personId}"}
            }}
            """,
            )
        }
        caller.onBehalfOfOrganization?.let { organization ->
            registerAuthInfo(
                caller.token,
                mapOf("OnBehalfOfOrganisationId" to organization),
                """
        {"result":{
          "tokenInfo":{"tokenStatus":"verified","partyId":"${caller.personId}","tokenType":"enduser"},
          "authInfo":{"actingType":"organisation","actingId":"$organization","actingOrganisationNumber":"$organization","originalId":"${caller.personId}"}
        }}
        """,
            )
        }
    }

    private suspend fun rejectUnexpectedContext(token: String, partyId: String, tokenType: String) = stub(
        priority = 5,
        request = {
            put("method", "POST")
            put("urlPath", AUTHINFO_POLICY_ROUTE)
            put(
                "bodyPatterns",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "matchesJsonPath",
                                buildJsonObject {
                                    put("expression", "$.input.token")
                                    put("equalTo", token)
                                }
                            )
                        }
                    )
                }
            )
        },
        response = """
            {
              "result": {
                "tokenInfo": {"tokenStatus": "verified", "partyId": "$partyId", "tokenType": "$tokenType"},
                "authInfo": {"error": "Invalid acting context"}
              }
            }
        """,
    )

    /** PDP: [personId] may approve for [representative] on behalf of [principal]. */
    private suspend fun allowApprover(personId: String, representative: String, principal: String) = stub(
        priority = 1,
        request = {
            put("method", "POST")
            put("urlPath", AGENT_APPROVER_ROUTE)
            put("bodyPatterns", payloadEquals("ApproverPersonId" to personId, "RepresentativeId" to representative, "PrincipalId" to principal))
        },
        response = pdpDecision(authorized = true),
    )

    /** PDP: [representative] may act on behalf of [principal]. */
    private suspend fun allowRepresentative(representative: String, principal: String) = stub(
        priority = 1,
        request = {
            put("method", "POST")
            put("urlPath", AGENT_REPRESENTATIVE_ROUTE)
            put("bodyPatterns", payloadEquals("RepresentativeId" to representative, "PrincipalId" to principal))
        },
        response = pdpDecision(authorized = true),
    )

    private fun payloadEquals(vararg fields: Pair<String, String>) = buildJsonArray {
        fields.forEach { (field, value) ->
            add(
                buildJsonObject {
                    put(
                        "matchesJsonPath",
                        buildJsonObject {
                            put("expression", "$.input.payload.$field")
                            put("equalTo", value)
                        }
                    )
                }
            )
        }
    }

    private suspend fun registerEdielFailure(gln: String, status: Int) = stub(
        priority = 1,
        request = {
            put("method", "GET")
            put("urlPath", "$EDIEL_PATH/PartyRedirectUrl")
            putJsonObject("queryParameters") { putJsonObject("gln") { put("equalTo", gln) } }
        },
        response = null,
        status = status,
    )

    /** Ediel: the redirect URL registered for [gln]. */
    private suspend fun registerEdielRedirect(gln: String, redirectUrl: String) = stub(
        priority = 1,
        request = {
            put("method", "GET")
            put("urlPath", "$EDIEL_PATH/PartyRedirectUrl")
            putJsonObject("queryParameters") { putJsonObject("gln") { put("equalTo", gln) } }
        },
        response = """{"RedirectUrls":{"Production":"$redirectUrl","Test":"$redirectUrl"}}""",
    )

    private suspend fun registerPerson(nationalIdentityNumber: String, personId: String) = stub(
        priority = 1,
        request = {
            put("method", "POST")
            put("urlPath", "$AUTH_PERSONS_PATH/market-parties/v0/persons")
            putJsonArray("bodyPatterns") {
                add(buildJsonObject { put("contains", "\"nationalIdentityNumber\":\"$nationalIdentityNumber\"") })
            }
        },
        response = """{"data":{"id":"$personId","type":"Person","attributes":{"type":"Person","id":"$personId"}}}""",
    )

    private suspend fun registerSupplier(gln: String, name: String) = stub(
        priority = 1,
        request = {
            put("method", "GET")
            put("urlPath", "$ORGANISATIONS_PATH/v1/parties/$gln")
            putJsonObject("queryParameters") { putJsonObject("partyType") { put("equalTo", "BalanceSupplier") } }
        },
        response = """
            {
              "data": {
                "id": "$gln",
                "type": "Party",
                "attributes": {"partyType": "BALANCE_SUPPLIER", "partyId": "$gln", "name": "$name", "status": "ACTIVE"},
                "relationships": {}
              }
            }
        """,
    )

    private suspend fun registerOrganization(number: String, name: String) = stub(
        priority = 1,
        request = {
            put("method", "GET")
            put("urlPath", "$ORGANISATIONS_PATH/v1/brreg/$number")
        },
        response = """{"data":{"id":"$number","type":"Organisation","attributes":{"name":"$name"}}}""",
    )

    suspend fun registerOrganizationIdentification(number: String, internalId: String) = stub(
        priority = 1,
        request = {
            put("method", "GET")
            put("urlPath", "$ORGANISATIONS_PATH/v1/identification/organisation/$number")
        },
        response = """{"data":[{"id":"$internalId","type":"Identification","attributes":{"identificationType":"ORGANIZATION"}}]}""",
    )

    suspend fun registerMeteringPoint(
        id: String,
        endUserId: String,
        postalCode: String,
        meterNumber: String,
        blockedForSwitching: Boolean = false,
    ) = stub(
        priority = 1,
        request = {
            put("method", "GET")
            put("urlPath", "$METERING_POINTS_PATH/metering-points/$id")
            putJsonObject("queryParameters") { putJsonObject("endUserId") { put("equalTo", endUserId) } }
        },
        response = """
            {"data":{"id":"$id","type":"MeteringPoint","attributes":{
              "accountingPoint":{"blockedForSwitching":$blockedForSwitching,"address":{"postalCode":"$postalCode"},"meter":{"meterNumber":"$meterNumber"}}
            },"relationships":{}}}
        """,
    )

    /** Calls the application made that no fake was set up for. Should always be empty. */
    suspend fun unmatchedRequests(): List<String> {
        val body = Json.parseToJsonElement(admin.get("$baseUrl/__admin/requests/unmatched").bodyAsText()).jsonObject
        return body.getValue("requests").jsonArray.map {
            val request = it.jsonObject
            "${request["method"]} ${request["url"]}: ${request["body"]}"
        }
    }

    private suspend fun registerAuthInfo(token: String, context: Map<String, String>, response: String) = stub(
        priority = 1,
        request = {
            put("method", "POST")
            put("urlPath", AUTHINFO_POLICY_ROUTE)
            put(
                "bodyPatterns",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "matchesJsonPath",
                                buildJsonObject {
                                    put("expression", "$.input.token")
                                    put("equalTo", token)
                                }
                            )
                        }
                    )
                    context.forEach { (field, value) ->
                        add(
                            buildJsonObject {
                                put(
                                    "matchesJsonPath",
                                    buildJsonObject {
                                        put("expression", "$.input.payload.$field")
                                        put("equalTo", value)
                                    }
                                )
                            }
                        )
                    }
                }
            )
        },
        response = response,
    )

    private suspend fun stub(priority: Int, request: JsonObjectBuilder.() -> Unit, response: String?, status: Int = 200) {
        val mapping = buildJsonObject {
            put("priority", priority)
            put("request", buildJsonObject(request))
            putJsonObject("response") {
                put("status", status)
                if (response != null) {
                    putJsonObject("headers") { put("Content-Type", "application/json") }
                    put("jsonBody", Json.parseToJsonElement(response.trimIndent()))
                }
            }
        }
        val result = admin.post("$baseUrl/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(mapping.toString())
        }
        check(result.status.isSuccess()) { "Could not register WireMock mapping: ${result.bodyAsText()}" }
    }

    private fun pdpDecision(authorized: Boolean): String =
        """{"result":{"authorized":$authorized,"tokenInfo":{"tokenStatus":"verified","partyId":"auth-grant-manager","tokenType":"elhub-service"}}}"""

    companion object {
        const val AUTH_PERSONS_PATH = "/auth-persons"
        const val ORGANISATIONS_PATH = "/organisations"
        const val METERING_POINTS_PATH = "/metering-points-service"
        const val END_USERS_PATH = "/end-users-service"
        const val IDP_TOKEN_PATH = "/idp/token"
        const val EDIEL_PATH = "/ediel"
        private const val SERVICE_TOKEN = "acceptance-service-token"
        private const val AGENT_APPROVER_ROUTE = "/v1/data/v3/token/agent/approver"
        private const val AGENT_REPRESENTATIVE_ROUTE = "/v1/data/v3/token/agent/representative"
    }
}
