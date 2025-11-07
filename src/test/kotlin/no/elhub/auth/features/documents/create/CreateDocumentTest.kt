package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.ElhubResourceType
import no.elhub.auth.features.common.ExposedPartyRepository
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.documents.AuthPersonsTestContainer
import no.elhub.auth.features.documents.AuthPersonsTestContainerExtension
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.VaultTransitTestContainerExtension
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.confirm.getEndUserNin
import no.elhub.auth.features.documents.confirm.isSignedByUs
import no.elhub.auth.features.documents.getCustomMetaDataValue
import no.elhub.auth.features.documents.localVaultConfig
import no.elhub.auth.features.documents.validateFileIsPDFA2BCompliant
import no.elhub.auth.features.documents.validateFileIsSignedByUs
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.fail

// TODO: Provide a valid supplier ID
private const val PERSON_RESOURCE_ID = "18084190426"
private val VALID_REQUESTED_FROM = AuthorizationParty(type = ElhubResourceType.Person, resourceId = PERSON_RESOURCE_ID)
private const val INVALID_REQUESTED_FROM = "^%)"
private const val VALID_REQUESTED_FROM_NAME = "Supplier AS"

private val VALID_REQUESTED_BY = AuthorizationParty(type = ElhubResourceType.Organization, resourceId = "567891")
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
        extensions(
            VaultTransitTestContainerExtension,
            PostgresTestContainerExtension,
            AuthPersonsTestContainerExtension,
        )
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
                    singleOf(::ExposedPartyRepository) bind PartyRepository::class
                    singleOf(::ApiEndUserService) bind EndUserService::class
                    single { EndUserApiConfig(baseUri = AuthPersonsTestContainer.baseUri()) }

                    singleOf(::Handler)
                },
                mode = KoinLifecycleMode.Root
            )
        )

        beforeSpec {
            Database.connect(
                url = PostgresTestContainer.JDBC_URL,
                driver = PostgresTestContainer.DRIVER,
                user = PostgresTestContainer.USERNAME,
                password = PostgresTestContainer.PASSWORD,
            )
        }

        context("Generate a Change of Supplier document") {

            When("I request a Change of Supplier document") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                val handler by inject<Handler>()

                val command = Command(
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

                xThen("I should receive a link to a PDF document") {
                    fail("Received the PDF bytes")
                }

                Then("that document should be signed by Elhub") {
                    document.file.validateFileIsSignedByUs()
                }

                Then("that document should contain the necessary metadata") {
                    val signerNin = document.file.getCustomMetaDataValue(PdfGenerator.PdfConstants.PDF_METADATA_KEY_NIN)
                    signerNin shouldBe PERSON_RESOURCE_ID
                }

                Then("that document should conform to the PDF/A-2b standard") {
                    document.file.validateFileIsPDFA2BCompliant() shouldBe true
                }
            }

            Given("that the end user is not already registered in Elhub") {

                When("I request a Change of Supplier document") {

                    val requestedFrom = VALID_REQUESTED_FROM
                    val requestedFromName = VALID_REQUESTED_FROM_NAME
                    val requestedBy = VALID_REQUESTED_BY
                    val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                    val meteringPointId = VALID_METERING_POINT_ID
                    val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                    val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                    val handler by inject<Handler>()

                    val command = Command(
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
                        val elhubInternalId = document.requestedFrom.resourceId
                        // the resourceId in requestedFrom should be an elhubInternalId with UUID type
                        elhubInternalId shouldMatch Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
                    }

                    xThen("I should receive a link to a PDF document") {
                        fail("Received the PDF bytes")
                    }

                    xThen("that document should be signed by Elhub") {
                        document.file.isSignedByUs() shouldBe true
                    }

                    xThen("that document should contain the necessary metadata") {
                        // TODO: PDF specific references in these tests?
                        document.file.getEndUserNin() shouldBe requestedFrom
                    }

                    xThen("that document should conform to the PDF/A-2b standard") {
                        document.file.validateFileIsPDFA2BCompliant() shouldBe true
                    }
                }
            }

            xGiven("that no balance supplier ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = AuthorizationParty(resourceId = "", type = ElhubResourceType.valueOf(""))
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that an invalid balance supplier ID has been provided (GLN)") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = AuthorizationParty(resourceId = "", type = ElhubResourceType.valueOf("")) // TODO - might need better test data here
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that no balance supplier name has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = BLANK
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that no end user ID has been provided (NIN/GLN)") {

                val requestedFrom = AuthorizationParty(resourceId = "", type = ElhubResourceType.valueOf("")) // TODO better test data here
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that an invalid end user ID has been provided (NIN/GLN)") {

                val requestedFrom = AuthorizationParty(resourceId = "", type = ElhubResourceType.valueOf("")) // TODO better test data
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that no metering point ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = BLANK
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that an invalid metering point ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = INVALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that no metering point address has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = BLANK

                When("I request a Change of Supplier document") {

                    val command = Command(
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

            xGiven("that no contract name has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = BLANK
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val command = Command(
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
