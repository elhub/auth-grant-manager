package no.elhub.auth.features.grants

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.AuthInfo
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.auth.PdpResponse
import no.elhub.auth.features.common.auth.Result
import no.elhub.auth.features.common.auth.TokenInfo
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.grants.common.AuthorizationGrantListResponse
import no.elhub.auth.features.grants.common.AuthorizationGrantResponse
import no.elhub.auth.features.grants.common.AuthorizationGrantScopesResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.auth.module as applicationModule

class AuthorizationGrantRouteTest : FunSpec({
    extensions(
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grants.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grant-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
        AuthPersonsTestContainerExtension,
        PdpTestContainerExtension(
            alwaysReturn = Json.encodeToString(
                PdpResponse(
                    result = Result(
                        tokenInfo =
                        TokenInfo(
                            tokenStatus = "verified",
                            partyId = "something",
                            tokenType = "maskinporten"
                        ),
                        authInfo = AuthInfo(
                            actingFunction = "BalanceSupplier",
                            actingGLN = "0107000000021"
                        )
                    )
                )
            )
        )
    )

    context("GET /authorization-grants/{id}") {
        testApplication {
            setupAuthorizationGrantTestApplication()

            test("Should return 200 OK on a valid ID") {
                val response = client.get("$GRANTS_PATH/123e4567-e89b-12d3-a456-426614174000") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: AuthorizationGrantResponse = response.body()
                responseJson.data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "AuthorizationGrant"
                    attributes.shouldNotBeNull()
                    attributes!!.apply {
                        status shouldBe "Active"
                        grantedAt shouldBe "2025-04-04T04:00"
                        validFrom shouldBe "2025-04-04T04:00"
                        validTo shouldBe "2026-04-04T04:00"
                    }
                    relationships.apply {
                        grantedFor.apply {
                            data.apply {
                                id shouldBe "12345678901"
                                type shouldBe "Person"
                            }
                        }
                        grantedBy.apply {
                            data.apply {
                                id shouldBe "12345678901"
                                type shouldBe "Person"
                            }
                        }
                        grantedTo.apply {
                            data.apply {
                                id shouldBe "0107000000021"
                                type shouldBe "OrganizationEntity"
                            }
                        }
                    }
                }
            }

            test("Should return 400 on an invalid ID") {
                val response = client.get("$GRANTS_PATH/test") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "400"
                        code shouldBe "INVALID_INPUT"
                        title shouldBe "Invalid input"
                        detail shouldBe "The provided payload did not satisfy the expected format"
                    }
                }
            }

            test("Should return 403 when the grant does not belong to the requester") {
                val response = client.get("$GRANTS_PATH/b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.Forbidden
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "403"
                        code shouldBe "REQUESTED_BY_MISMATCH"
                        title shouldBe "RequestedBy mismatch"
                        detail shouldBe "The requester is not allowed to access this resource"
                    }
                }
            }

            test("Should return 404 on a nonexistent ID") {
                val response = client.get("$GRANTS_PATH/123e4567-e89b-12d3-a456-426614174001") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.NotFound
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "404"
                        code shouldBe "NOT_FOUND"
                        title shouldBe "Not Found"
                        detail shouldBe "The requested resource could not be found"
                    }
                }
            }
        }
    }

    context("GET /authorization-grants/{id}/scopes") {
        testApplication {
            setupAuthorizationGrantTestApplication()

            test("Should return 200 OK on a valid ID and a single authorization scope") {
                val response = client.get("$GRANTS_PATH/123e4567-e89b-12d3-a456-426614174000/scopes") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: AuthorizationGrantScopesResponse = response.body()
                responseJson.data.apply {
                    size shouldBe 1
                    this[0].apply {
                        id shouldBe "123"
                        type shouldBe "AuthorizationScope"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            authorizedResourceType shouldBe ElhubResource.MeteringPoint
                            authorizedResourceId shouldBe "b7f9c2e4"
                            permissionType shouldBe PermissionType.ReadAccess
                            createdAt.shouldNotBeNull()
                        }
                    }
                }
            }

            test("Should return 200 OK and an empty list for a grant with no authorization scopes") {
                val response = client.get("$GRANTS_PATH/a8f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a/scopes") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: AuthorizationGrantScopesResponse = response.body()
                responseJson.data.size shouldBe 0
            }

            test("Should return 200 OK on a valid ID and multiple authorization scope") {
                val response = client.get("$GRANTS_PATH/d75522ba-0e62-449b-b1de-70b16f12ecaf/scopes") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: AuthorizationGrantScopesResponse = response.body()
                responseJson.data.apply {
                    size shouldBe 2
                    this[0].apply {
                        id shouldBe "345"
                        type shouldBe "AuthorizationScope"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            authorizedResourceType shouldBe ElhubResource.MeteringPoint
                            authorizedResourceId shouldBe "b7f9c2e4"
                            permissionType shouldBe PermissionType.ChangeOfSupplier
                            createdAt.shouldNotBeNull()
                        }
                    }
                    this[1].apply {
                        id shouldBe "567"
                        type shouldBe "AuthorizationScope"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            authorizedResourceType shouldBe ElhubResource.Organization
                            authorizedResourceId shouldBe "b7f9c2e4"
                            permissionType shouldBe PermissionType.ChangeOfSupplier
                            createdAt.shouldNotBeNull()
                        }
                    }
                }
            }

            test("Should return 400 on an invalid ID") {
                val response = client.get("$GRANTS_PATH/test/scopes") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "400"
                        code shouldBe "INVALID_INPUT"
                        title shouldBe "Invalid input"
                        detail shouldBe "The provided payload did not satisfy the expected format"
                    }
                }
            }

            test("Should return 404 on a nonexistent ID") {
                val response = client.get("$GRANTS_PATH/123e4567-e89b-12d3-a456-426614174005/scopes") {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.NotFound
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "404"
                        title shouldBe "Not Found"
                        detail shouldBe "The requested resource could not be found"
                    }
                }
            }
        }
    }

    context("GET /authorization-grants") {
        testApplication {
            setupAuthorizationGrantTestApplication()

            test("Should return 200 OK") {
                val response = client.get(GRANTS_PATH) {
                    header(HttpHeaders.Authorization, "Bearer something")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: AuthorizationGrantListResponse = response.body()
                responseJson.data.apply {
                    size shouldBe 3
                    this[0].apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationGrant"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            status shouldBe "Active"
                            grantedAt shouldBe "2025-04-04T04:00"
                            validFrom shouldBe "2025-04-04T04:00"
                            validTo shouldBe "2026-04-04T04:00"
                        }
                        relationships.apply {
                            grantedFor.apply {
                                data.apply {
                                    id shouldBe "12345678901"
                                    type shouldBe "Person"
                                }
                            }
                            grantedBy.apply {
                                data.apply {
                                    id shouldBe "12345678901"
                                    type shouldBe "Person"
                                }
                            }
                            grantedTo.apply {
                                data.apply {
                                    id shouldBe "0107000000021"
                                    type shouldBe "OrganizationEntity"
                                }
                            }
                        }
                    }
                    this[1].apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationGrant"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            status shouldBe "Revoked"
                            grantedAt shouldBe "2025-01-04T03:00"
                            validFrom shouldBe "2025-02-03T17:07"
                            validTo shouldBe "2025-05-16T04:00"
                        }
                        relationships.apply {
                            grantedFor.apply {
                                data.apply {
                                    id shouldBe "123123123"
                                    type shouldBe "OrganizationEntity"
                                }
                            }
                            grantedBy.apply {
                                data.apply {
                                    id shouldBe "23456789012"
                                    type shouldBe "Person"
                                }
                            }
                            grantedTo.apply {
                                data.apply {
                                    id shouldBe "0107000000021"
                                    type shouldBe "OrganizationEntity"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
})

private fun ApplicationTestBuilder.setupAuthorizationGrantTestApplication() {
    client = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    application {
        applicationModule()
        commonModule()
        module()
    }

    environment {
        config = MapApplicationConfig(
            "ktor.database.username" to "app",
            "ktor.database.password" to "app",
            "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
            "ktor.database.driverClass" to "org.postgresql.Driver",
            "featureToggle.enableEndpoints" to "true",
            "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
            "pdp.baseUrl" to "http://localhost:8085"
        )
    }
}
