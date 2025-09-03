package no.elhub.auth.features.grants

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.config.AUTHORIZATION_GRANT
import no.elhub.auth.defaultTestApplication
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.validate

class AuthorizationGrantRouteTest :
    FunSpec({
        extensions(
            PostgresTestContainerExtension,
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grants.sql"),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grant-scopes.sql"),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql")
        )

        lateinit var testApp: TestApplication

        beforeSpec {
            testApp = defaultTestApplication()
        }

        afterSpec {
            testApp.stop()
        }

        context("GET /authorization-grants/{id}") {

            test("Should return 200 OK on a valid ID") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/123e4567-e89b-12d3-a456-426614174000")
                response.status shouldBe HttpStatusCode.OK
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data" {
                        "id".shouldNotBeNull()
                        "type" shouldBe "AuthorizationGrant"
                        "attributes" {
                            "status" shouldBe "Active"
                            "grantedAt" shouldBe "2025-04-04T04:00"
                            "validFrom" shouldBe "2025-04-04T04:00"
                            "validTo" shouldBe "2026-04-04T04:00"
                        }
                        "relationships" {
                            "grantedFor" {
                                "data" {
                                    "id" shouldBe "1111111111111111"
                                    "type" shouldBe "Person"
                                }
                            }
                            "grantedBy" {
                                "data" {
                                    "id" shouldBe "1111111111111111"
                                    "type" shouldBe "Person"
                                }
                            }
                            "grantedTo" {
                                "data" {
                                    "id" shouldBe "2222222222222222"
                                    "type" shouldBe "Organization"
                                }
                            }
                        }
                    }
                }
            }

            test("Should return 400 on an invalid ID") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/test")
                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "400"
                            "code" shouldBe "INVALID_RESOURCE_ID"
                            "title" shouldBe "Malformed ID"
                            "detail" shouldBe "The provided ID is not valid"
                        }
                    }
                }
            }

            test("Should return 404 on a nonexistent ID") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/123e4567-e89b-12d3-a456-426614174001")
                response.status shouldBe HttpStatusCode.NotFound
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "404"
                            "code" shouldBe "NOT_FOUND"
                            "title" shouldBe "Authorization not found"
                            "detail" shouldBe "The authorization was not found"
                        }
                    }
                }
            }
        }

        context("GET /authorization-grants/{id}/scopes") {

            test("Should return 200 OK on a valid ID and a single authorization scope") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/123e4567-e89b-12d3-a456-426614174000/scopes")
                response.status shouldBe HttpStatusCode.OK

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data".shouldBeList(size = 1) {
                        item(0) {
                            "id" shouldBe "123"
                            "type" shouldBe "AuthorizationScope"
                            "attributes" {
                                "authorizedResourceType" shouldBe "MeteringPoint"
                                "authorizedResourceId" shouldBe "b7f9c2e4"
                                "permissionType" shouldBe "ReadAccess"
                                "createdAt".shouldNotBeNull()
                            }
                        }
                    }
                }
            }

            test("Should return 200 OK and an empty list for a grant with no authorization scopes") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/a8f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a/scopes")
                response.status shouldBe HttpStatusCode.OK

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

                responseJson.validate {
                    "data".shouldBeList(size = 0) {}
                }
            }

            test("Should return 200 OK on a valid ID and multiple authorization scope") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a/scopes")
                response.status shouldBe HttpStatusCode.OK

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data".shouldBeList(size = 3) {
                        item(0) {
                            "id" shouldBe "345"
                            "type" shouldBe "AuthorizationScope"
                            "attributes" {
                                "authorizedResourceType" shouldBe "MeteringPoint"
                                "authorizedResourceId" shouldBe "b7f9c2e4"
                                "permissionType" shouldBe "ChangeOfSupplier"
                                "createdAt".shouldNotBeNull()
                            }
                        }
                        item(1) {
                            "id" shouldBe "567"
                            "type" shouldBe "AuthorizationScope"
                            "attributes" {
                                "authorizedResourceType" shouldBe "Organization"
                                "authorizedResourceId" shouldBe "b7f9c2e4"
                                "permissionType" shouldBe "ChangeOfSupplier"
                                "createdAt".shouldNotBeNull()
                            }
                        }
                        item(2) {
                            "id" shouldBe "678"
                            "type" shouldBe "AuthorizationScope"
                            "attributes" {
                                "authorizedResourceType" shouldBe "Person"
                                "authorizedResourceId" shouldBe "b7f9c2e4"
                                "permissionType" shouldBe "FullDelegation"
                                "createdAt".shouldNotBeNull()
                            }
                        }
                    }
                }
            }

            test("Should return 400 on an invalid ID") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/test/scopes")
                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "400"
                            "code" shouldBe "INVALID_RESOURCE_ID"
                            "title" shouldBe "Malformed ID"
                            "detail" shouldBe "The provided ID is not valid"
                        }
                    }
                }
            }

            test("Should return 404 on a nonexistent ID") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/123e4567-e89b-12d3-a456-426614174005/scopes")
                response.status shouldBe HttpStatusCode.NotFound
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "404"
                            "title" shouldBe "Authorization not found"
                            "detail" shouldBe "The authorization was not found"
                        }
                    }
                }
            }
        }

        context("GET /authorization-grants") {

            test("Should return 200 OK") {
                val response = testApp.client.get(AUTHORIZATION_GRANT)
                response.status shouldBe HttpStatusCode.OK
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data".shouldBeList(size = 3) {
                        item(0) {
                            "id".shouldNotBeNull()
                            "type" shouldBe "AuthorizationGrant"
                            "attributes" {
                                "status" shouldBe "Active"
                                "grantedAt" shouldBe "2025-04-04T04:00"
                                "validFrom" shouldBe "2025-04-04T04:00"
                                "validTo" shouldBe "2026-04-04T04:00"
                            }
                            "relationships" {
                                "grantedFor" {
                                    "data" {
                                        "id" shouldBe "1111111111111111"
                                        "type" shouldBe "Person"
                                    }
                                }
                                "grantedBy" {
                                    "data" {
                                        "id" shouldBe "1111111111111111"
                                        "type" shouldBe "Person"
                                    }
                                }
                                "grantedTo" {
                                    "data" {
                                        "id" shouldBe "2222222222222222"
                                        "type" shouldBe "Organization"
                                    }
                                }
                            }
                        }
                        item(1) {
                            "id".shouldNotBeNull()
                            "type" shouldBe "AuthorizationGrant"
                            "attributes" {
                                "status" shouldBe "Expired"
                                "grantedAt" shouldBe "2023-04-04T04:00"
                                "validFrom" shouldBe "2023-04-04T04:00"
                                "validTo" shouldBe "2024-04-04T04:00"
                            }
                            "relationships" {
                                "grantedFor" {
                                    "data" {
                                        "id" shouldBe "3333333333333333"
                                        "type" shouldBe "Person"
                                    }
                                }
                                "grantedBy" {
                                    "data" {
                                        "id" shouldBe "3333333333333333"
                                        "type" shouldBe "Person"
                                    }
                                }
                                "grantedTo" {
                                    "data" {
                                        "id" shouldBe "2222222222222222"
                                        "type" shouldBe "Organization"
                                    }
                                }
                            }
                        }
                        item(2) {
                            "id".shouldNotBeNull()
                            "type" shouldBe "AuthorizationGrant"
                            "attributes" {
                                "status" shouldBe "Revoked"
                                "grantedAt" shouldBe "2025-01-04T03:00"
                                "validFrom" shouldBe "2025-02-03T17:07"
                                "validTo" shouldBe "2025-05-16T04:00"
                            }
                            "relationships" {
                                "grantedFor" {
                                    "data" {
                                        "id" shouldBe "4444444444444444"
                                        "type" shouldBe "OrganizationEntity"
                                    }
                                }
                                "grantedBy" {
                                    "data" {
                                        "id" shouldBe "3333333333333333"
                                        "type" shouldBe "Person"
                                    }
                                }
                                "grantedTo" {
                                    "data" {
                                        "id" shouldBe "5555555555555555"
                                        "type" shouldBe "MeteringPoint"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    })
