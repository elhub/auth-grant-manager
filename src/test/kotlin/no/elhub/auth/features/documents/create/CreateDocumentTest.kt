package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import com.github.mustachejava.DefaultMustacheFactory
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import no.elhub.auth.config.loadCerts
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.confirm.getEndUserNin
import no.elhub.auth.features.documents.confirm.isConformant
import no.elhub.auth.features.documents.confirm.isSignedByUs
import no.elhub.auth.features.documents.localVaultConfig
import org.apache.commons.lang3.NotImplementedException
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

/*
 * As a balance supplier or service provider
 * I want to generate a Change of Supplier document
 * So that I can obtain consent to move their subscription myself
 */
class CreateDocumentTest : BehaviorSpec(), KoinTest {
    init {
        extension(KoinExtension(module {
            single { loadCerts(File(TestCertificateUtil.Constants.CERTIFICATE_LOCATION)).single() }
            single { loadCerts(File(TestCertificateUtil.Constants.CERTIFICATE_LOCATION)) }
            single { PAdESService(CommonCertificateVerifier()) }
            single { httpTestClient }
            single { localVaultConfig }
            singleOf(::HashicorpVaultSignatureProvider) bind SignatureProvider::class
            singleOf(::PAdESDocumentSigningService) bind DocumentSigningService::class

            single { DefaultMustacheFactory(PdfDocumentGenerator.MustacheConstants.RESOURCES_PATH) }
            singleOf(::PdfDocumentGenerator) bind DocumentGenerator::class

            singleOf(::ExposedDocumentRepository) bind DocumentRepository::class

            singleOf(::CreateDocumentHandler)
        }, mode = KoinLifecycleMode.Root))

        xcontext("Generate a Change of Supplier document") {

            When("I request a Change of Supplier document") {

                val handler by inject<CreateDocumentHandler>()

                val endUserNin = "01010112345"

                val document = handler(
                    CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        "supplierABC",
                        endUserNin,
                        "abc123",
                    )
                ).getOrElse { fail("Document not returned") }

                Then("I should receive a link to a PDF document") {
                    fail("Received the PDF bytes")
                }

                Then("that document should be signed by Elhub") {
                    document.pdfBytes.isSignedByUs() shouldBe true
                }

                Then("that document should contain the necessary metadata") {
                    // TODO: PDF specific references in these tests?
                    document.pdfBytes.getEndUserNin() shouldBe endUserNin
                }

                Then("that document should conform to the PDF/A-2b standard") {
                    document.pdfBytes.isConformant() shouldBe true
                }
            }

        }
    }
}
