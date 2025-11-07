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
import no.elhub.auth.features.common.PostgresTestContainerExtension
import java.util.UUID
import no.elhub.auth.features.documents.module as documentsModule
import no.elhub.auth.features.grants.module as grantsModule
import no.elhub.auth.features.openapi.module as openApiModule
import no.elhub.auth.features.requests.module as requestsModule

class FeatureToggleTest : FunSpec({
    extensions(
        PostgresTestContainerExtension,
    )

    test("returns 404 for business endpoints when feature toggle is variable is set to false") {
        testApplication {
            environment {
                config = MapApplicationConfig().apply {
                    put("featureToggle.enableEndpoints", "false")
                    put("pdfSigner.certificate.chain", "chain.pem")
                    put("pdfSigner.certificate.signing", "signing.pem")
                    put("pdfSigner.vault.url", "http://localhost:8200")
                    put("pdfSigner.vault.key", "test-key")
                    put("pdfSigner.vault.tokenPath", "/token")
                    put("ktor.database.username", "app")
                    put("ktor.database.password", "app")
                    put("ktor.database.url", "jdbc:postgresql://localhost:5432/auth")
                    put("ktor.database.driverClass", "org.postgresql.Driver")
                }
            }

            application {
                documentsModule()
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
                HttpMethod.Post to "/authorization-documents",
                HttpMethod.Patch to "/authorization-documents/$id",
                HttpMethod.Get to "/authorization-documents/$id",
                HttpMethod.Get to "/authorization-documents/$id.pdf",
                HttpMethod.Get to "/authorization-documents",
                HttpMethod.Patch to "/authorization-requests/$id",
                HttpMethod.Post to "/authorization-requests",
                HttpMethod.Get to "/authorization-requests/$id",
                HttpMethod.Get to "/authorization-requests",
                HttpMethod.Get to "/authorization-grants/$id",
                HttpMethod.Get to "/authorization-grants/$id/scopes",
                HttpMethod.Get to "/authorization-grants"
            )

            disabledEndpoints.forEach { (method, path) ->
                val response = client.request(path) {
                    this.method = method
                }
                response.status shouldBe HttpStatusCode.NotFound
            }

            val openApiResponse = client.get("/openapi")
            openApiResponse.status shouldBe HttpStatusCode.OK
        }
    }
    test("returns 404 for business endpoints when feature toggle variable is not set") {
        testApplication {
            environment {
                config = MapApplicationConfig().apply {
                    put("featureToggle.enableEndpoints", "false") // This disables all endpoints
                    put("pdfSigner.certificate.chain", "chain.pem")
                    put("pdfSigner.certificate.signing", "signing.pem")
                    put("pdfSigner.vault.url", "http://localhost:8200")
                    put("pdfSigner.vault.key", "test-key")
                    put("pdfSigner.vault.tokenPath", "/token")
                    put("ktor.database.username", "app")
                    put("ktor.database.password", "app")
                    put("ktor.database.url", "jdbc:postgresql://localhost:5432/auth")
                    put("ktor.database.driverClass", "org.postgresql.Driver")
                }
            }

            application {
                documentsModule()
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
                HttpMethod.Post to "/authorization-documents",
                HttpMethod.Patch to "/authorization-documents/$id",
                HttpMethod.Get to "/authorization-documents/$id",
                HttpMethod.Get to "/authorization-documents/$id.pdf",
                HttpMethod.Get to "/authorization-documents",
                HttpMethod.Patch to "/authorization-requests/$id",
                HttpMethod.Post to "/authorization-requests",
                HttpMethod.Get to "/authorization-requests/$id",
                HttpMethod.Get to "/authorization-requests",
                HttpMethod.Get to "/authorization-grants/$id",
                HttpMethod.Get to "/authorization-grants/$id/scopes",
                HttpMethod.Get to "/authorization-grants"
            )

            disabledEndpoints.forEach { (method, path) ->
                val response = client.request(path) {
                    this.method = method
                }
                response.status shouldBe HttpStatusCode.NotFound
            }

            val openApiResponse = client.get("/openapi")
            openApiResponse.status shouldBe HttpStatusCode.OK
        }
    }
})
