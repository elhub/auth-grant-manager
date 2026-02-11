package no.elhub.auth.features.businessprocesses.movein

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApi
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.INACTIVE_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.NOT_BALANCE_SUPPLIER_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsServiceTestData.VALID_PARTY_ID
import no.elhub.auth.features.businessprocesses.structuredata.organisations.organisationsServiceHttpClient
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.model.today

private val VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, VALID_PARTY_ID)
private val NOT_VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "0000")
private val NON_EXISTING_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, NOT_BALANCE_SUPPLIER_PARTY_ID)
private val INACTIVE_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, INACTIVE_PARTY_ID)
private val VALID_METERING_POINT = "123456789012345678"
private val AUTHORIZED_PARTY = AuthorizationParty(resourceId = VALID_PARTY_ID, type = PartyType.Organization)
private val VALID_START_DATE = LocalDate(2025, 1, 1)

class MoveInBusinessHandlerTest :
    FunSpec({

        extension(OrganisationsServiceTestContainerExtension)
        lateinit var organisationsService: OrganisationsService
        lateinit var handler: MoveInBusinessHandler
        beforeSpec {
            organisationsService = OrganisationsApi(
                OrganisationsApiConfig(
                    serviceUrl = OrganisationsServiceTestContainer.serviceUrl(),
                    basicAuthConfig = no.elhub.auth.features.businessprocesses.structuredata.organisations.BasicAuthConfig("user", "pass")
                ),
                organisationsServiceHttpClient
            )
            handler = MoveInBusinessHandler(organisationsService)
        }

        test("request validation allows missing startDate") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = null,
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()
            command.meta.toMetaAttributes()["startDate"] shouldBe null
        }

        test("request validation fails on future startDate") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = today().plus(DatePeriod(days = 1)),
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(MoveInValidationError.StartDateNotBackInTime)
        }

        test("request validation allows startDate today") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = today(),
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeRight()
        }

        test("request validation fails on invalid metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = "123",
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = VALID_START_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(MoveInValidationError.InvalidMeteringPointId)
        }

        test("request validation fails on not valid requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = NOT_VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = VALID_START_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(MoveInValidationError.InvalidRequestedBy)
        }

        test("request validation fails on non existing requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = NON_EXISTING_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = VALID_START_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(MoveInValidationError.RequestedByNotFound)
        }

        test("request validation fails on non Active requested by") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = INACTIVE_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = VALID_START_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(MoveInValidationError.NotActiveRequestedBy)
        }

        test("request produces RequestCommand for valid input") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = VALID_START_DATE,
                        redirectURI = "https://example.com",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson
            command.validTo shouldBe today().plus(DatePeriod(days = 28)).toTimeZoneOffsetDateTimeAtStartOfDay()
            command.meta.toMetaAttributes()["startDate"] shouldBe VALID_START_DATE.toString()
            command.meta.toMetaAttributes()["redirectURI"] shouldBe "https://example.com"
        }

        test("grant properties validTo is one year from acceptance") {
            val party = AuthorizationParty(resourceId = "party-1", type = PartyType.Organization)
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                requestedBy = party,
                requestedFrom = party,
                requestedTo = party,
                validTo = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
            )

            val properties = handler.getCreateGrantProperties(request)

            properties.validFrom shouldBe today()
            properties.validTo shouldBe today().plus(DatePeriod(years = 1))
        }

        test("document produces DocumentCommand for valid input") {
            val model =
                CreateDocumentModel(
                    authorizedParty = AuthorizationParty(resourceId = VALID_PARTY.idValue, type = PartyType.Organization),
                    documentType = AuthorizationDocument.Type.MoveInAndChangeOfEnergySupplierForPerson,
                    meta =
                    CreateDocumentMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedTo = VALID_PARTY,
                        requestedFromName = "From",
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                        startDate = VALID_START_DATE,
                    ),
                )

            val command = handler.validateAndReturnDocumentCommand(model).shouldBeRight()
            command.meta.toMetaAttributes()["startDate"] shouldBe VALID_START_DATE.toString()
        }
    })
