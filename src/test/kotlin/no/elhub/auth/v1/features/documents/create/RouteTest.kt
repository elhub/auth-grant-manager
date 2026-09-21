package no.elhub.auth.v1.features.documents.create

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import arrow.core.right
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v0.postJson
import no.elhub.auth.v0.validateInternalServerErrorResponse
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

class RouteTest : FunSpec({
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestBody = """
        {
          "data": {
            "type": "AuthorizationDocument",
            "attributes": {
              "documentType": "ChangeOfEnergySupplierForOrganization",
              "requestedScope": {
                "appliesTo": {
                  "meteringPointIds": ["707057500000000001"]
                }
              },
              "externalReference": "contract-123"
            },
            "meta": {
              "requestedFrom": {
                "idType": "GlobalLocationNumber",
                "idValue": "999888777"
              },
              "requestedTo": {
                "idType": "NationalIdentityNumber",
                "idValue": "01019012345"
              },
              "language": "Nb"
            }
          }
        }
    """.trimIndent()

    lateinit var handler: CreateDocumentBusinessHandler
    lateinit var partyService: PartyService
    lateinit var payloadValidator: CreateAuthorizationDocumentPayloadValidator

    beforeAny {
        handler = mockk()
        partyService = mockk()
        payloadValidator = CreateAuthorizationDocumentPayloadValidator()
    }

    test("POST / returns 201 and passes the authorized party to the handler") {
        coEvery { handler.invoke(any()) } returns mockk()
        coEvery { partyService.resolve(any()) } returnsMany listOf(
            AuthorizationParty("999888777", PartyType.OrganizationEntity).right(),
            AuthorizationParty("person-1", PartyType.Person).right(),
        )

        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson("/", requestBody)

            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) {
                handler.invoke(match {
                    it.requestedBy == authorizedParty &&
                        it.requestedScope.documentType == AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization &&
                    it.requestedFrom.id == "999888777" &&
                        it.requestedTo.id == "person-1" &&
                        it.language == DocumentLanguage.Nb &&
                        it.requestedFrom == AuthorizationParty("999888777", PartyType.OrganizationEntity) &&
                        it.requestedTo == AuthorizationParty("person-1", PartyType.Person)
                })
            }
        }
    }

    test("POST / returns 409 when the resource type is wrong") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson("/", requestBody.replace("AuthorizationDocument", "OtherResource"))

            response.status shouldBe HttpStatusCode.Conflict
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe "Expected 'data.type' to be 'AuthorizationDocument', but received 'OtherResource'"
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("POST / returns 400 when the request cannot be deserialized") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson("/", "{ \"data\": { \"type\": \"AuthorizationDocument\" } }")

            response.status shouldBe HttpStatusCode.BadRequest
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().title shouldBe "Invalid request body"
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("POST / ignores allowed changes that are not used by change of supplier") {
        coEvery { handler.invoke(any()) } returns mockk()
        coEvery { partyService.resolve(any()) } returnsMany listOf(
            AuthorizationParty("999888777", PartyType.OrganizationEntity).right(),
            AuthorizationParty("person-1", PartyType.Person).right(),
        )

        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson(
                "/",
                requestBody.replace(
                    "\"externalReference\": \"contract-123\"",
                    "\"externalReference\": \"contract-123\",\n              \"requestedScope\": {\n                \"appliesTo\": {\n                  \"meteringPointIds\": [\"707057500000000001\"]\n                },\n                \"allowedChanges\": { \"validFrom\": [\"2026-10-01\"] }\n              }",
                ),
            )

            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) {
                handler.invoke(match {
                    it.requestedScope is RequestedScope.ChangeOfEnergySupplierForOrganization
                })
            }
        }
    }

    test("POST / returns 422 when move in does not contain exactly one valid-from date") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson(
                "/",
                requestBody
                    .replace("ChangeOfEnergySupplierForOrganization", "MoveInAndChangeOfEnergySupplierForOrganization")
                    .replace("\"externalReference\": \"contract-123\"", "\"externalReference\": \"contract-123\",\n              \"requestedScope\": {\n                \"appliesTo\": {\n                  \"meteringPointIds\": [\"707057500000000001\"]\n                },\n                \"allowedChanges\": { \"validFrom\": [\"2026-10-01\", \"2026-11-01\"] }\n              }")
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("POST / returns 422 when a metering-point ID has an invalid format") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson(
                "/",
                requestBody.replace("707057500000000001", "invalid"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScope.appliesTo.meteringPointIds must contain only valid 18-digit metering-point IDs"
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
})
