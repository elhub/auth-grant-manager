package no.elhub.auth.features.grants

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.DatabaseExtension
import no.elhub.auth.config.AUTHORIZATION_GRANT
import no.elhub.auth.utils.defaultTestApplication
import no.elhub.auth.validate

class AuthorizationGrantRouteTest :
    DescribeSpec({
        extensions(DatabaseExtension)

        lateinit var testApp: TestApplication

        beforeTest {
            testApp = defaultTestApplication()
        }

        afterTest {
            testApp.stop()
        }

        describe("GET /authorization-grants/{id}") {

            it("should return 400 on an invalid ID") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/test")
                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "400"
                            "title" shouldBe "Bad Request"
                            "detail" shouldBe "Missing or malformed id."
                        }
                    }
                    "links" {
                        "self" shouldBe "http://localhost/authorization-grants/test"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }

            it("should return 200 OK on a valid ID") {
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
                    "links" {
                        "self" shouldBe "http://localhost/authorization-grants/123e4567-e89b-12d3-a456-426614174000"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }

            it("should return 404 on a nonexistent ID") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/123e4567-e89b-12d3-a456-426614174001")
                response.status shouldBe HttpStatusCode.NotFound
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "404"
                            "title" shouldBe "Not Found"
                            "detail" shouldBe "Authorization grant with id=123e4567-e89b-12d3-a456-426614174001 not found"
                        }
                    }
                    "links" {
                        "self" shouldBe "http://localhost/authorization-grants/123e4567-e89b-12d3-a456-426614174001"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }
        }

        describe("GET /authorization-grants") {

            it("should return 200 OK") {
                val response = testApp.client.get(AUTHORIZATION_GRANT)
                response.status shouldBe HttpStatusCode.OK

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data".shouldBeList(size = 2) {
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
                    }
                    "links" {
                        "self" shouldBe "http://localhost/authorization-grants"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }
        }
    })
