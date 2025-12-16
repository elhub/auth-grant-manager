package no.elhub.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.openapi.API_PATH_OPENAPI
import no.elhub.auth.features.requests.REQUESTS_PATH
import java.util.UUID
import no.elhub.auth.features.documents.module as documentsModule
import no.elhub.auth.features.grants.module as grantsModule
import no.elhub.auth.features.openapi.module as openApiModule
import no.elhub.auth.features.requests.module as requestsModule

class FeatureToggleTest : FunSpec({
    extensions(
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grants.sql"),

        AuthPersonsTestContainerExtension,
    )

    test("returns 404 for business endpoints when feature toggle is variable is set to false") {
        testApplication {
            environment {
                config = MapApplicationConfig().apply {
                    put("ktor.database.username", "app")
                    put("ktor.database.password", "app")
                    put("ktor.database.url", "jdbc:postgresql://localhost:5432/auth")
                    put("ktor.database.driverClass", "org.postgresql.Driver")
                    put("authPersons.baseUri", AuthPersonsTestContainer.baseUri())
                    put("pdp.baseUrl", "http://localhost:8085")
                }
            }

            application {
                module()
                commonModule()
                grantsModule()
                requestsModule()
                openApiModule()
            }

            client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val id = UUID.randomUUID().toString()

            val disabledEndpoints = listOf(
                HttpMethod.Patch to "${REQUESTS_PATH}/$id",
                HttpMethod.Post to REQUESTS_PATH,
                HttpMethod.Get to "${REQUESTS_PATH}/$id",
                HttpMethod.Get to REQUESTS_PATH,
                HttpMethod.Patch to "$GRANTS_PATH/b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a"
            )

            disabledEndpoints.forEach { (method, path) ->
                val response = client.request(path) {
                    this.method = method
                }
                response.status shouldBe HttpStatusCode.NotFound
            }

            val openApiResponse = client.get(API_PATH_OPENAPI)
            openApiResponse.status shouldBe HttpStatusCode.OK
        }
    }
    test("returns 404 for business endpoints when feature toggle variable is not set") {
        testApplication {
            environment {
                config = MapApplicationConfig().apply {
                    put("ktor.database.username", "app")
                    put("ktor.database.password", "app")
                    put("ktor.database.url", "jdbc:postgresql://localhost:5432/auth")
                    put("ktor.database.driverClass", "org.postgresql.Driver")
                    put("authPersons.baseUri", AuthPersonsTestContainer.baseUri())
                    put("pdp.baseUrl", "http://localhost:8085")
                }
            }

            application {
                module()
                commonModule()
                grantsModule()
                requestsModule()
                openApiModule()
            }

            client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val id = UUID.randomUUID().toString()

            val disabledEndpoints = listOf(
                HttpMethod.Patch to "${REQUESTS_PATH}/$id",
                HttpMethod.Post to REQUESTS_PATH,
                HttpMethod.Get to "${REQUESTS_PATH}/$id",
                HttpMethod.Get to REQUESTS_PATH,
                HttpMethod.Patch to "$GRANTS_PATH/$id"
            )

            disabledEndpoints.forEach { (method, path) ->
                val response = client.request(path) {
                    this.method = method
                }
                response.status shouldBe HttpStatusCode.NotFound
            }

            val openApiResponse = client.get(API_PATH_OPENAPI)
            openApiResponse.status shouldBe HttpStatusCode.OK
        }
    }
})
