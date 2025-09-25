package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.confirm.getEndUserNin
import no.elhub.auth.features.documents.confirm.isConformant
import no.elhub.auth.features.documents.confirm.isSignedByUs
import no.elhub.auth.features.documents.localVaultConfig
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.fail

// TODO: Provide a valid supplier ID
private const val VALID_SUPPLIER_ID = "abc123"
private const val INVALID_SUPPLIER_ID = "^%)"

private const val VALID_SUPPLIER_NAME = "Supplier AS"

private const val VALID_END_USER_ID = "01010112345"
private const val INVALID_END_USER_ID = "^%)"

// TODO: Provide a valid metering point
private const val VALID_METERING_POINT_ID = "abc123"
private const val INVALID_METERING_POINT_ID = "^%)"

private const val VALID_METERING_POINT_ADDRESS = "Adressevegen 1, 1234"

// TODO: Provide a valid signing party ID
private const val VALID_SIGNING_PARTY_ID = "abc123"

private const val VALID_CONTRACT_NAME = "Contract 123"

private const val BLANK = ""
private const val EMPTY = " "

/*
 * As a balance supplier or service provider
 * I want to generate a Change of Supplier document
 * So that I can obtain consent to move their subscription myself
 */
class CreateDocumentTest : BehaviorSpec(), KoinTest {
    init {
        extension(
            KoinExtension(
                module {

                    single {
                        FileCertificateProviderConfig(
                            TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                            TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        )
                    }

                    singleOf(::FileCertificateProvider) bind CertificateProvider::class

                    single { PAdESService(CommonCertificateVerifier()) }

                    single { httpTestClient }
                    single { localVaultConfig }
                    singleOf(::HashicorpVaultSignatureProvider) bind SignatureProvider::class
                    singleOf(::PAdESDocumentSigningService) bind DocumentSigningService::class

                    single { PdfGeneratorConfig("templates") }
                    singleOf(::PdfDocumentGenerator) bind DocumentGenerator::class

                    singleOf(::ExposedDocumentRepository) bind DocumentRepository::class
                    single { EndUserApiConfig("baseUrl", "/persons/") }
                    singleOf(::ApiEndUserRepository) bind EndUserRepository::class

                    singleOf(::CreateDocumentHandler)
                },
                mode = KoinLifecycleMode.Root
            )
        )

        context("Generate a Change of Supplier document") {

            When("I request a Change of Supplier document") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                val handler by inject<CreateDocumentHandler>()

                val command = CreateDocumentCommand(
                    AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                    supplierId,
                    supplierName,
                    endUserId,
                    meteringPointId,
                    meteringPointAddress,
                    signingPartyId,
                    contractName,
                ).getOrElse { fail("Unexpected command construction error") }

                val document = handler(command)
                    .getOrElse { fail("Document not returned") }

                Then("I should receive a link to a PDF document") {
                    fail("Received the PDF bytes")
                }

                Then("that document should be signed by Elhub") {
                    document.pdfBytes.isSignedByUs() shouldBe true
                }

                Then("that document should contain the necessary metadata") {
                    // TODO: PDF specific references in these tests?
                    document.pdfBytes.getEndUserNin() shouldBe endUserId
                }

                Then("that document should conform to the PDF/A-2b standard") {
                    document.pdfBytes.isConformant() shouldBe true
                }
            }

            Given("that the end user is not already registered in Elhub") {

                val endUserRepo by inject<EndUserRepository>()

                When("I request a Change of Supplier document") {

                    val supplierId = VALID_SUPPLIER_ID
                    val supplierName = VALID_SUPPLIER_NAME
                    val endUserId = VALID_END_USER_ID
                    val meteringPointId = VALID_METERING_POINT_ID
                    val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                    val signingPartyId = VALID_SIGNING_PARTY_ID
                    val contractName = VALID_CONTRACT_NAME

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    ).getOrElse { fail("Unexpected command construction error") }

                    val document = handler(command).getOrElse { fail("Document not returned") }

                    Then("the user should be registered in Elhub") {
                        val endUser = endUserRepo.findOrCreateByNin(endUserId)
                            .getOrElse { fail("Could not retrieve the end user") }
                        endUser.id shouldNotBe null
                    }

                    Then("I should receive a link to a PDF document") {
                        fail("Received the PDF bytes")
                    }

                    Then("that document should be signed by Elhub") {
                        document.pdfBytes.isSignedByUs() shouldBe true
                    }

                    Then("that document should contain the necessary metadata") {
                        // TODO: PDF specific references in these tests?
                        document.pdfBytes.getEndUserNin() shouldBe endUserId
                    }

                    Then("that document should conform to the PDF/A-2b standard") {
                        document.pdfBytes.isConformant() shouldBe true
                    }
                }
            }

            Given("that no balance supplier ID has been provided") {

                val supplierId = BLANK
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME
                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about missing balance supplier ID") {
                        command.shouldBeLeft(listOf(ValidationError.MissingSupplierId))
                    }
                }
            }

            Given("that an invalid balance supplier ID has been provided (GLN)") {

                val supplierId = INVALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about invalid balance supplier ID") {
                        command.shouldBeLeft(listOf(ValidationError.InvalidRequestedFrom))
                    }
                }
            }

            Given("that no balance suppliername has been provided") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = BLANK
                val endUserId = VALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about missing balance supplier name") {
                        command.shouldBeLeft(listOf(ValidationError.MissingSupplierName))
                    }
                }
            }

            Given("that no end user ID has been provided (NIN/GLN)") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = BLANK
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about missing end user ID") {
                        command.shouldBeLeft(listOf(ValidationError.MissingRequestedTo))
                    }
                }
            }

            Given("that an invalid end user ID has been provided (NIN/GLN)") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = INVALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about invalid end user ID") {
                        command.shouldBeLeft(listOf(ValidationError.InvalidRequestedTo))
                    }
                }
            }

            Given("that no metering point ID has been provided") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = BLANK
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about missing metering point ID") {
                        command.shouldBeLeft(listOf(ValidationError.MissingMeteringPointId))
                    }
                }
            }

            Given("that an invalid metering point ID has been provided") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = INVALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about invalid metering point ID") {
                        command.shouldBeLeft(listOf(ValidationError.InvalidMeteringPointId))
                    }
                }
            }

            Given("that no metering point address has been provided") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = BLANK
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about missing metering point address") {
                        command.shouldBeLeft(listOf(ValidationError.MissingMeteringPointAddress))
                    }
                }
            }

            Given("that no signing party ID has been provided (NIN)") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = BLANK
                val contractName = VALID_CONTRACT_NAME

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about missing signing party ID") {
                        command.shouldBeLeft(listOf(ValidationError.MissingSigningPartyId))
                    }
                }
            }

            Given("that no contract name has been provided") {

                val supplierId = VALID_SUPPLIER_ID
                val supplierName = VALID_SUPPLIER_NAME
                val endUserId = VALID_END_USER_ID
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                val signingPartyId = VALID_SIGNING_PARTY_ID
                val contractName = BLANK

                When("I request a Change of Supplier document") {

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        supplierId,
                        supplierName,
                        endUserId,
                        meteringPointId,
                        meteringPointAddress,
                        signingPartyId,
                        contractName,
                    )

                    Then("I should receive an error message about missing contract name") {
                        command.shouldBeLeft(listOf(ValidationError.MissingContractName))
                    }
                }
            }
        }
    }
}
