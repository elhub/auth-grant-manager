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
private const val VALID_REQUESTED_FROM = "abc123"
private const val INVALID_REQUESTED_FROM = "^%)"
private const val VALID_REQUESTED_FROM_NAME = "Supplier AS"

private const val VALID_REQUESTED_BY = "01010112345"
private const val INVALID_REQUESTED_BY = "^%)"

// TODO: Provide a valid metering point
private const val VALID_METERING_POINT_ID = "abc123"
private const val INVALID_METERING_POINT_ID = "^%)"

private const val VALID_METERING_POINT_ADDRESS = "Adressevegen 1, 1234"

private const val VALID_BALANCE_SUPPLIER_NAME = "Supplier AS"
private const val VALID_BALANCE_SUPPLIER_CONTRACT_NAME = "Contract 123"

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
                    singleOf(::PdfSigningService) bind FileSigningService::class
                    single { PdfGeneratorConfig("templates") }
                    singleOf(::PdfGenerator) bind FileGenerator::class

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

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                val handler by inject<CreateDocumentHandler>()

                val command = CreateDocumentCommand(
                    AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                    requestedFrom,
                    requestedFromName,
                    requestedBy,
                    balanceSupplierName,
                    balanceSupplierContractName,
                    meteringPointId,
                    meteringPointAddress,
                ).getOrElse { fail("Unexpected command construction error") }

                val document = handler(command)
                    .getOrElse { fail("Document not returned") }

                Then("I should receive a link to a PDF document") {
                    fail("Received the PDF bytes")
                }

                Then("that document should be signed by Elhub") {
                    document.file.isSignedByUs() shouldBe true
                }

                Then("that document should contain the necessary metadata") {
                    // TODO: PDF specific references in these tests?
                    document.file.getEndUserNin() shouldBe requestedFrom
                }

                Then("that document should conform to the PDF/A-2b standard") {
                    document.file.isConformant() shouldBe true
                }
            }

            Given("that the end user is not already registered in Elhub") {

                val endUserRepo by inject<EndUserRepository>()

                When("I request a Change of Supplier document") {

                    val requestedFrom = VALID_REQUESTED_FROM
                    val requestedFromName = VALID_REQUESTED_FROM_NAME
                    val requestedBy = VALID_REQUESTED_BY
                    val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                    val meteringPointId = VALID_METERING_POINT_ID
                    val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                    val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                    val handler by inject<CreateDocumentHandler>()

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    ).getOrElse { fail("Unexpected command construction error") }

                    val document = handler(command).getOrElse { fail("Document not returned") }

                    Then("the user should be registered in Elhub") {
                        val endUser = endUserRepo.findOrCreateByNin(requestedFrom)
                            .getOrElse { fail("Could not retrieve the end user") }
                        endUser.id shouldNotBe null
                    }

                    Then("I should receive a link to a PDF document") {
                        fail("Received the PDF bytes")
                    }

                    Then("that document should be signed by Elhub") {
                        document.file.isSignedByUs() shouldBe true
                    }

                    Then("that document should contain the necessary metadata") {
                        // TODO: PDF specific references in these tests?
                        document.file.getEndUserNin() shouldBe requestedFrom
                    }

                    Then("that document should conform to the PDF/A-2b standard") {
                        document.file.isConformant() shouldBe true
                    }
                }
            }

            Given("that no balance supplier ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = BLANK
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about missing balance supplier ID") {
                        command.shouldBeLeft(listOf(ValidationError.MissingRequestedBy))
                    }
                }
            }

            Given("that an invalid balance supplier ID has been provided (GLN)") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = INVALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about invalid balance supplier ID") {
                        command.shouldBeLeft(listOf(ValidationError.InvalidRequestedBy))
                    }
                }
            }

            Given("that no balance supplier name has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = BLANK
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about missing balance supplier name") {
                        command.shouldBeLeft(listOf(ValidationError.MissingBalanceSupplierName))
                    }
                }
            }

            Given("that no end user ID has been provided (NIN/GLN)") {

                val requestedFrom = BLANK
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about missing end user ID") {
                        command.shouldBeLeft(listOf(ValidationError.MissingRequestedFrom))
                    }
                }
            }

            Given("that an invalid end user ID has been provided (NIN/GLN)") {

                val requestedFrom = INVALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about invalid end user ID") {
                        command.shouldBeLeft(listOf(ValidationError.InvalidRequestedFrom))
                    }
                }
            }

            Given("that no metering point ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = BLANK
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about missing metering point ID") {
                        command.shouldBeLeft(listOf(ValidationError.MissingMeteringPointId))
                    }
                }
            }

            Given("that an invalid metering point ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = INVALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about invalid metering point ID") {
                        command.shouldBeLeft(listOf(ValidationError.InvalidMeteringPointId))
                    }
                }
            }

            Given("that no metering point address has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = BLANK

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about missing metering point address") {
                        command.shouldBeLeft(listOf(ValidationError.MissingMeteringPointAddress))
                    }
                }
            }

            Given("that no contract name has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = BLANK
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = CreateDocumentCommand(
                        AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        requestedFrom,
                        requestedFromName,
                        requestedBy,
                        balanceSupplierName,
                        balanceSupplierContractName,
                        meteringPointId,
                        meteringPointAddress,
                    )

                    Then("I should receive an error message about missing contract name") {
                        command.shouldBeLeft(listOf(ValidationError.MissingBalanceSupplierContractName))
                    }
                }
            }
        }
    }
}
