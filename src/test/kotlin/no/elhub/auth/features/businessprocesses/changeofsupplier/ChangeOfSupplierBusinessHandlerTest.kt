package no.elhub.auth.features.businessprocesses.changeofsupplier

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.features.businessprocesses.structuredata.BasicAuthConfig
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsApi
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsApiConfig
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsService
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsServiceTestContainer
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsServiceTestContainerExtension
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsServiceTestData.ANOTHER_END_USER_ID
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsServiceTestData.END_USER_ID
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsServiceTestData.NON_EXISTING_METERING_POINT
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsServiceTestData.SHARED_END_USER_ID
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsServiceTestData.VALID_METERING_POINT
import no.elhub.auth.features.businessprocesses.structuredata.meteringPointsServiceHttpClient
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.person.Person
import no.elhub.auth.features.common.person.PersonService
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import java.util.UUID

private val VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "123456789")
private val AUTHORIZED_PARTY = AuthorizationParty(resourceId = VALID_PARTY.idValue, type = PartyType.Organization)
private val END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "123456789")
private val ANOTHER_END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "987654321")
private val SHARED_END_USER = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "11223344556")

class ChangeOfSupplierBusinessHandlerTest :
    FunSpec({

        extension(MeteringPointsServiceTestContainerExtension)
        lateinit var meteringPointsService: MeteringPointsService
        lateinit var handler: ChangeOfSupplierBusinessHandler
        val personService = mockk<PersonService>()

        beforeSpec {
            meteringPointsService =
                MeteringPointsApi(
                    MeteringPointsApiConfig(serviceUrl = MeteringPointsServiceTestContainer.serviceUrl(), basicAuthConfig = BasicAuthConfig("user", "pass")),
                    meteringPointsServiceHttpClient,
                )
            handler = ChangeOfSupplierBusinessHandler(meteringPointsService = meteringPointsService, personService = personService)
        }

        coEvery { personService.findOrCreateByNin(END_USER.idValue) } returns Either.Right(Person(UUID.fromString(END_USER_ID)))
        coEvery { personService.findOrCreateByNin(ANOTHER_END_USER.idValue) } returns Either.Right(Person(UUID.fromString(ANOTHER_END_USER_ID)))
        coEvery { personService.findOrCreateByNin(SHARED_END_USER.idValue) } returns Either.Right(Person(UUID.fromString(SHARED_END_USER_ID)))

        test("request validation fails on missing requestedFromName") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                        CreateRequestMeta(
                            requestedBy = VALID_PARTY,
                            requestedFrom = VALID_PARTY,
                            requestedFromName = "",
                            requestedTo = END_USER,
                            requestedForMeteringPointId = VALID_METERING_POINT,
                            requestedForMeteringPointAddress = "addr",
                            balanceSupplierName = "Supplier",
                            balanceSupplierContractName = "Contract",
                        ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(ChangeOfSupplierValidationError.MissingRequestedFromName)
        }

        test("request validation fails when requestedTo is not related to metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                        CreateRequestMeta(
                            requestedBy = VALID_PARTY,
                            requestedFrom = VALID_PARTY,
                            requestedFromName = "From",
                            requestedTo = ANOTHER_END_USER,
                            requestedForMeteringPointId = VALID_METERING_POINT,
                            requestedForMeteringPointAddress = "addr",
                            balanceSupplierName = "Supplier",
                            balanceSupplierContractName = "Contract",
                        ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(ChangeOfSupplierValidationError.RequestedToNotMeteringPointEndUser)
        }

        test("request validation fails when requestedTo is has access to metering point but is not end user") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                        CreateRequestMeta(
                            requestedBy = VALID_PARTY,
                            requestedFrom = VALID_PARTY,
                            requestedFromName = "From",
                            requestedTo = SHARED_END_USER,
                            requestedForMeteringPointId = VALID_METERING_POINT,
                            requestedForMeteringPointAddress = "addr",
                            balanceSupplierName = "Supplier",
                            balanceSupplierContractName = "Contract",
                        ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(ChangeOfSupplierValidationError.RequestedToNotMeteringPointEndUser)
        }

        test("request validation fails on invalid metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                        CreateRequestMeta(
                            requestedBy = VALID_PARTY,
                            requestedFrom = VALID_PARTY,
                            requestedFromName = "From",
                            requestedTo = END_USER,
                            requestedForMeteringPointId = "123",
                            requestedForMeteringPointAddress = "addr",
                            balanceSupplierName = "Supplier",
                            balanceSupplierContractName = "Contract",
                        ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(ChangeOfSupplierValidationError.InvalidMeteringPointId)
        }

        test("request validation fails on unexisting metering point") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    meta =
                        CreateRequestMeta(
                            requestedBy = VALID_PARTY,
                            requestedFrom = VALID_PARTY,
                            requestedFromName = "From",
                            requestedTo = VALID_PARTY,
                            requestedForMeteringPointId = NON_EXISTING_METERING_POINT,
                            requestedForMeteringPointAddress = "addr",
                            balanceSupplierName = "Supplier",
                            balanceSupplierContractName = "Contract",
                        ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(ChangeOfSupplierValidationError.MeteringPointNotFound)
        }

        test("request produces RequestCommand for valid input") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
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
                        ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson
            command.validTo shouldBe defaultValidTo().toTimeZoneOffsetDateTimeAtStartOfDay()
        }

        test("document produces DocumentCommand for valid input") {
            val model =
                CreateDocumentModel(
                    authorizedParty = AuthorizationParty(resourceId = VALID_PARTY.idValue, type = PartyType.Organization),
                    documentType = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
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
                        ),
                )

            val command = handler.validateAndReturnDocumentCommand(model).shouldBeRight()
            command.meta.toMetaAttributes()["requestedFromName"] shouldBe "From"
        }
    })
