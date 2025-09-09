package no.elhub.auth.features.documents.create

import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import no.elhub.auth.config.loadCerts
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
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

const val elhubSignature = "Elhub"
const val ssn = "ssn"

// TODO: Implement
fun ByteArray.getSignature() = elhubSignature

// TODO: Implement
fun ByteArray.isPdf() = this.isNotEmpty()

fun ByteArray.metadata() = mapOf(ssn to "01010112345")

/*
 * As a supplier
 * I want to generate a Change of Supplier confirmation document for a given customer
 * So that I can obtain their consent myself
 */
class CreateDocumentTest : BehaviorSpec(), KoinTest {
    init {
        beforeSpec {
            startKoin {
                modules(
                    module {
                        single { loadCerts(File(TestCertificateUtil.Constants.CERTIFICATE_LOCATION)).single() }
                        single { loadCerts(File(TestCertificateUtil.Constants.CERTIFICATE_LOCATION)) }
                        single { PAdESService(CommonCertificateVerifier()) }
                        single { httpTestClient }
                        single { localVaultConfig }
                        singleOf(::HashicorpVaultSignatureProvider) bind SignatureProvider::class
                        singleOf(::PAdESDocumentSigningService) bind DocumentSigningService::class

                        singleOf(::PdfDocumentGenerator) bind DocumentGenerator::class

                        singleOf(::ExposedDocumentRepository) bind DocumentRepository::class

                        singleOf(::CreateDocumentHandler)
                    },
                )
            }
        }

        afterSpec {
            stopKoin()
        }

        xcontext("Generate Change of Supplier confirmation document") {

            val documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation
            val requestedTo = "supplierABC"
            val meteringPointId = "abc123"

            val handler by inject<CreateDocumentHandler>()

            Given("that no pending documents already exist for the customer") {

                val customerSsn = "01010112345"

                And("that the customer exists") {

                    When("I request a document") {

                        val document = handler(
                            CreateDocumentCommand(
                                documentType,
                                requestedTo,
                                customerSsn,
                                meteringPointId,
                            )
                        )

                        Then("I should receive a link to PDF file") {

                            document.shouldBeRight()
                            // document.pdfBytes.isPdf() shouldBe true
                        }

                        Then("That file should include Elhub's signature") {
                            // document.pdfBytes.getSignature() shouldBe elhubSignature
                        }

                        Then("That file metadata should include the customer's social security number") {
                            // document.pdfBytes.metadata()[ssn] shouldBe requestedTo
                        }
                    }
                }

                And("I provide an invalid social security number") {

                    val invalidCustomerSsn = "abcdefgh"

                    When("I request a document") {

                        val document = handler(
                            CreateDocumentCommand(
                                documentType,
                                requestedTo,
                                invalidCustomerSsn,
                                meteringPointId,
                            )
                        )

                        Then("I should receive an error telling me that the social security number is invalid") {

                            TODO()
                            throw NotImplementedException()
                        }
                    }
                }

                And("that the customer does not exist") {

                    val nonExistentCustomerSsn = "01012712345"

                    When("I request a document") {

                        val document = handler(
                            CreateDocumentCommand(
                                documentType,
                                requestedTo,
                                nonExistentCustomerSsn,
                                meteringPointId,
                            )
                        )

                        Then("I should receive an error telling me that the customer does not exist") {

                            TODO()
                            throw NotImplementedException()
                        }
                    }
                }
            }
        }
    }
}
