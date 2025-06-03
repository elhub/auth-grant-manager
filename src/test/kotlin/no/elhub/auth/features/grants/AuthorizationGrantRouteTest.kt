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

        describe("GET /authorization-grants") {
            it("should return 200 OK") {
                val response = testApp.client.get(AUTHORIZATION_GRANT)
                response.status shouldBe HttpStatusCode.OK

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data".validateArray { obj, index ->
                        when (index) {
                            0 ->
                                obj validate {
                                    "id".shouldNotBeNull()
                                    "type" shouldBe "AuthorizationGrant"
                                    "attributes" validate {
                                        "status" shouldBe "Active"
                                        "grantedAt" shouldBe "2025-04-04T04:00"
                                        "validFrom" shouldBe "2025-04-04T04:00"
                                        "validTo" shouldBe "2026-04-04T04:00"
                                    }
                                    "relationships" validate {
                                        "grantedFor" validate {
                                            "data" validate {
                                                "id" shouldBe "1111111111111111"
                                                "type" shouldBe "Person"
                                            }
                                        }
                                        "grantedBy" validate {
                                            "data" validate {
                                                "id" shouldBe "1111111111111111"
                                                "type" shouldBe "Person"
                                            }
                                        }
                                        "grantedTo" validate {
                                            "data" validate {
                                                "id" shouldBe "2222222222222222"
                                                "type" shouldBe "Organization"
                                            }
                                        }
                                    }
                                }
                            1 ->
                                obj.validate {
                                    "id".shouldNotBeNull()
                                    "type" shouldBe "AuthorizationGrant"
                                    "attributes" validate {
                                        "status" shouldBe "Expired"
                                        "grantedAt" shouldBe "2023-04-04T04:00"
                                        "validFrom" shouldBe "2023-04-04T04:00"
                                        "validTo" shouldBe "2024-04-04T04:00"
                                    }
                                    "relationships" validate {
                                        "grantedFor" validate {
                                            "data" validate {
                                                "id" shouldBe "3333333333333333"
                                                "type" shouldBe "Person"
                                            }
                                        }
                                        "grantedBy" validate {
                                            "data" validate {
                                                "id" shouldBe "3333333333333333"
                                                "type" shouldBe "Person"
                                            }
                                        }
                                        "grantedTo" validate {
                                            "data" validate {
                                                "id" shouldBe "2222222222222222"
                                                "type" shouldBe "Organization"
                                            }
                                        }
                                    }
                                }
                        }
                        "links".validate {
                            "self" shouldBe "http://localhost/authorization-grants"
                        }
                        "meta".validate {
                            "createdAt".shouldNotBeNull()
                        }
                    }
                }
            }
        }
    })
