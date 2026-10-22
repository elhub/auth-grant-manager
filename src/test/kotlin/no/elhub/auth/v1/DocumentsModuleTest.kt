package no.elhub.auth.v1

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.elhub.auth.common.configureErrorHandling
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v0.config.configureAuthorization
import no.elhub.auth.v0.config.configureSerialization
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
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

    listOf("", "/12334", "/12334.pdf").forEach { path ->
        test("registers the protected GET $V1_DOCUMENTS_PATH$path route when v1 is enabled") {
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
                        provide<PdfSignatureValidator> { mockk<PdfSignatureValidator>() }
                        provide<PartyService> { mockk<PartyService>() }
                        provide<MeteringPointsService> { mockk<MeteringPointsService>() }
                    }
                    module()
                }

                client.get("$V1_DOCUMENTS_PATH$path").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("does not register GET $V1_DOCUMENTS_PATH$path when v1 is disabled") {
            testApplication {
                environment {
                    config = MapApplicationConfig("v1.enabled" to "false")
                }
                application {
                    module()
                }

                client.get("$V1_DOCUMENTS_PATH$path").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    test("requires authentication for PUT /{id}.pdf when v1 is enabled") {
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
                    provide<PdfSignatureValidator> { mockk<PdfSignatureValidator>() }
                    provide<PartyService> { mockk<PartyService>() }
                    provide<MeteringPointsService> { mockk<MeteringPointsService>() }
                }
                module()
            }

            val response = client.put("$V1_DOCUMENTS_PATH/123e4567-e89b-12d3-a456-426614174000.pdf") {
                contentType(ContentType.Application.Pdf)
                setBody("%PDF-1.7\nexample document".toByteArray())
            }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("does not register PUT /{id}.pdf when v1 is disabled") {
        testApplication {
            environment {
                config = MapApplicationConfig("v1.enabled" to "false")
            }
            application {
                module()
            }

            val response = client.put("$V1_DOCUMENTS_PATH/123e4567-e89b-12d3-a456-426614174000.pdf") {
                contentType(ContentType.Application.Pdf)
                setBody("%PDF-1.7\nexample document".toByteArray())
            }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
