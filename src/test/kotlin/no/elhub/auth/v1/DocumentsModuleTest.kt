package no.elhub.auth.v1

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.elhub.auth.common.configureErrorHandling
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v0.config.configureAuthorization
import no.elhub.auth.v0.config.configureSerialization
import no.elhub.auth.v0.features.common.party.PartyService

class DocumentsModuleTest : FunSpec({
    test("does not register v1 routes when disabled") {
        testApplication {
            environment {
                config = MapApplicationConfig("v1.enabled" to "false")
            }
            application {
                module()
            }

            client.get(V1_DOCUMENTS_PATH).status shouldBe HttpStatusCode.NotFound
        }
    }

    listOf("", ".pdf").forEach { suffix ->
        test("registers the protected GET /{id}$suffix route when v1 is enabled") {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "v1.enabled" to "true",
                        "pdp.baseUrl" to "http://localhost/pdp",
                    )
                }
                application {
                    configureSerialization()
                    configureErrorHandling()
                    configureAuthorization()
                    dependencies {
                        provide<PdfGenerator> { mockk<PdfGenerator>() }
                        provide<PdfSigner> { mockk<PdfSigner>() }
                        provide<PartyService> { mockk<PartyService>() }
                    }
                    module()
                }

                client.get("$V1_DOCUMENTS_PATH/12334$suffix").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("does not register GET /{id}$suffix when v1 is disabled") {
            testApplication {
                environment {
                    config = MapApplicationConfig("v1.enabled" to "false")
                }
                application {
                    module()
                }

                client.get("$V1_DOCUMENTS_PATH/12334$suffix").status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
