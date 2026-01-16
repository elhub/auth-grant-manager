package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import arrow.core.right
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.movein.MoveInBusinessHandler
import no.elhub.auth.features.common.ApiPersonService
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PersonApiConfig
import no.elhub.auth.features.common.PersonService
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.VaultTransitTestContainerExtension
import no.elhub.auth.features.documents.common.DocumentPropertiesRepository
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentPropertiesRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.common.ProxyDocumentBusinessHandler
import no.elhub.auth.features.documents.confirm.getPersonNin
import no.elhub.auth.features.documents.confirm.isSignedByUs
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.documents.getCustomMetaDataValue
import no.elhub.auth.features.documents.localVaultConfig
import no.elhub.auth.features.documents.validateFileIsPDFA2BCompliant
import no.elhub.auth.features.documents.validateFileIsSignedByUs
import no.elhub.auth.features.filegenerator.PdfGenerator
import no.elhub.auth.features.filegenerator.PdfGeneratorConfig
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.fail

// TODO: Provide a valid supplier ID
private val VALID_REQUESTED_FROM_IDENTIFIER = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "12345678901")
private const val INVALID_REQUESTED_FROM = "^%)"
private const val VALID_REQUESTED_FROM_NAME = "Supplier AS"

private val VALID_REQUESTED_BY_IDENTIFIER = PartyIdentifier(idType = PartyIdentifierType.GlobalLocationNumber, idValue = "56012398741")
private const val INVALID_REQUESTED_BY = "^%)"

private val VALID_REQUESTED_TO_IDENTIFIER = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "56012398742")
private val VALID_SIGNED_BY_IDENTIFIER = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "56012398743")

// TODO: Provide a valid metering point
private const val VALID_METERING_POINT_ID = "123456789012345678"
private const val INVALID_METERING_POINT_ID = "^%)"

private const val VALID_METERING_POINT_ADDRESS = "Adressevegen 1, 1234"

private const val VALID_BALANCE_SUPPLIER_NAME = "Supplier AS"
private const val VALID_BALANCE_SUPPLIER_CONTRACT_NAME = "Contract 123"

private const val BLANK = ""
private const val EMPTY = " "

private fun authorizedPartyFor(identifier: PartyIdentifier): AuthorizationParty =
    when (identifier.idType) {
        PartyIdentifierType.NationalIdentityNumber ->
            AuthorizationParty(resourceId = identifier.idValue, type = PartyType.Person)

        PartyIdentifierType.OrganizationNumber ->
            AuthorizationParty(resourceId = identifier.idValue, type = PartyType.Organization)

        PartyIdentifierType.GlobalLocationNumber ->
            AuthorizationParty(resourceId = identifier.idValue, type = PartyType.OrganizationEntity)
    }

private fun testDocumentOrchestrator(): ProxyDocumentBusinessHandler = ProxyDocumentBusinessHandler(
    ChangeOfSupplierBusinessHandler(),
    MoveInBusinessHandler(),
    FakeFileGenerator()
)

private class FakeFileGenerator : FileGenerator {
    override fun generate(
        signerNin: String,
        documentMeta: DocumentMetaMarker,
    ) = ByteArray(0).right()
}

/*
 * As a balance supplier or service provider
 * I want to generate a Change of Supplier document
 * So that I can obtain consent to move their subscription myself
 */
class CreateDocumentTest :
    BehaviorSpec(),
    KoinTest {
    init {
        extensions(
            VaultTransitTestContainerExtension,
            PostgresTestContainerExtension(),
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
                    singleOf(::PdfSigningService) bind SigningService::class
                    single { PdfGeneratorConfig("templates") }
                    singleOf(::PdfGenerator) bind FileGenerator::class

                    singleOf(::ExposedDocumentRepository) bind DocumentRepository::class
                    singleOf(::ExposedPartyRepository) bind PartyRepository::class
                    singleOf(::ApiPersonService) bind PersonService::class
                    single { PersonApiConfig(baseUri = AuthPersonsTestContainer.baseUri()) }
                    single { PartyService(get()) }
                    singleOf(::ExposedDocumentPropertiesRepository) bind DocumentPropertiesRepository::class
                    singleOf(::ChangeOfSupplierBusinessHandler)
                    singleOf(::MoveInBusinessHandler)
                    singleOf(::ProxyDocumentBusinessHandler)

                    singleOf(::Handler)
                },
                mode = KoinLifecycleMode.Root,
            ),
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

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                val handler by inject<Handler>()

                val model =
                    CreateDocumentModel(
                        authorizedParty = authorizedPartyFor(requestedBy),
                        documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        meta =
                        CreateDocumentMeta(
                            requestedBy = requestedBy,
                            requestedFrom = requestedFrom,
                            requestedTo = requestedFrom,
                            requestedFromName = requestedFromName,
                            balanceSupplierName = balanceSupplierName,
                            balanceSupplierContractName = balanceSupplierContractName,
                            requestedForMeteringPointId = meteringPointId,
                            requestedForMeteringPointAddress = meteringPointAddress,
                        ),
                    )

                val document =
                    handler(model)
                        .getOrElse { fail("Document not returned") }

                xThen("I should receive a link to a PDF document") {
                    fail("Received the PDF bytes")
                }

                Then("that document should be signed by Elhub") {
                    document.file.validateFileIsSignedByUs()
                }

                Then("that document should contain the necessary metadata") {
                    val signerNin = document.file.getCustomMetaDataValue(PdfGenerator.PdfConstants.PDF_METADATA_KEY_NIN)
                    signerNin shouldBe model.meta.requestedTo.idValue
                }

                Then("that document should conform to the PDF/A-2b standard") {
                    document.file.validateFileIsPDFA2BCompliant() shouldBe true
                }
            }

            Given("that the end user is not already registered in Elhub") {

                When("I request a Change of Supplier document") {

                    val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                    val requestedFromName = VALID_REQUESTED_FROM_NAME
                    val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                    val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                    val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                    val meteringPointId = VALID_METERING_POINT_ID
                    val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                    val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                    val handler by inject<Handler>()

                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    val document = handler(model).getOrElse { fail("Document not returned") }

                    Then("the user should be registered in Elhub") {
                        val resolvedResourceId = document.requestedFrom.resourceId

                        resolvedResourceId shouldNotBe requestedFrom

                        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
                        resolvedResourceId shouldMatch uuidRegex
                    }

                    xThen("I should receive a link to a PDF document") {
                        fail("Received the PDF bytes")
                    }

                    xThen("that document should be signed by Elhub") {
                        document.file.isSignedByUs() shouldBe true
                    }

                    xThen("that document should contain the necessary metadata") {
                        // TODO: PDF specific references in these tests?
                        document.file.getPersonNin() shouldBe requestedFrom
                    }

                    Then("that document should conform to the PDF/A-2b standard") {
                        document.file.validateFileIsPDFA2BCompliant() shouldBe true
                    }
                }
            }

            xGiven("that no balance supplier ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "")
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS
                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about missing balance supplier ID") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Error"))
                    }
                }
            }

            xGiven("that an invalid balance supplier ID has been provided (GLN)") {

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "")
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about invalid balance supplier ID") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }

            xGiven("that no balance supplier name has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = BLANK
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about missing balance supplier name") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }

            xGiven("that no end user ID has been provided (NIN/GLN)") {

                val requestedFrom = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "")
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about missing end user ID") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }

            xGiven("that an invalid end user ID has been provided (NIN/GLN)") {

                val requestedFrom = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "")
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about invalid end user ID") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }

            xGiven("that no metering point ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = BLANK
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about missing metering point ID") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }

            xGiven("that an invalid metering point ID has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = INVALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about invalid metering point ID") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }

            xGiven("that no metering point address has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = VALID_BALANCE_SUPPLIER_CONTRACT_NAME
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = BLANK

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about missing metering point address") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }

            xGiven("that no contract name has been provided") {

                val requestedFrom = VALID_REQUESTED_FROM_IDENTIFIER
                val requestedFromName = VALID_REQUESTED_FROM_NAME
                val requestedBy = VALID_REQUESTED_BY_IDENTIFIER
                val requestedTo = VALID_REQUESTED_TO_IDENTIFIER
                val balanceSupplierName = VALID_BALANCE_SUPPLIER_NAME
                val balanceSupplierContractName = BLANK
                val meteringPointId = VALID_METERING_POINT_ID
                val meteringPointAddress = VALID_METERING_POINT_ADDRESS

                When("I request a Change of Supplier document") {

                    val orchestrator = testDocumentOrchestrator()
                    val model =
                        CreateDocumentModel(
                            authorizedParty = authorizedPartyFor(requestedBy),
                            documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                            meta =
                            CreateDocumentMeta(
                                requestedBy = requestedBy,
                                requestedFrom = requestedFrom,
                                requestedTo = requestedTo,
                                requestedFromName = requestedFromName,
                                balanceSupplierName = balanceSupplierName,
                                balanceSupplierContractName = balanceSupplierContractName,
                                requestedForMeteringPointId = meteringPointId,
                                requestedForMeteringPointAddress = meteringPointAddress,
                            ),
                        )

                    Then("I should receive an error message about missing contract name") {
                        orchestrator
                            .validateAndReturnDocumentCommand(
                                model,
                            ).shouldBeLeft(CreateDocumentError.BusinessValidationError("Something"))
                    }
                }
            }
        }
    }
}
