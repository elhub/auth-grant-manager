package no.elhub.auth.features.businessprocesses.changeofsupplier

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.features.businessprocesses.structuredata.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsService
import no.elhub.auth.features.businessprocesses.structuredata.domain.Attributes
import no.elhub.auth.features.businessprocesses.structuredata.domain.Relationships
import no.elhub.auth.features.common.Person
import no.elhub.auth.features.common.PersonService
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.devxp.jsonapi.response.JsonApiResponse.SingleDocumentWithRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships
import java.util.UUID

private val VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "123456789")
private val VALID_METERING_POINT = "123456789012345678"
private val VALID_UNEXISTING_METERING_POINT = "876543210987654321"
private val AUTHORIZED_PARTY = AuthorizationParty(resourceId = VALID_PARTY.idValue, type = PartyType.Organization)
private val END_USER_INTERNAL_ID = "d6784082-8344-e733-e053-02058d0a6752"

class ChangeOfSupplierBusinessHandlerTest :
    FunSpec({

        val meteringPointsService = mockk<MeteringPointsService>()
        val personService = mockk<PersonService>()
        val mockResponse = mockk<HttpResponse>(relaxed = true)
        coEvery { meteringPointsService.getMeteringPointByIdAndElhubInternalId(VALID_METERING_POINT, any<String>()) } returns
            Either.Right(
                SingleDocumentWithRelationships(
                    data = JsonApiResponseResourceObjectWithRelationships(
                        id = VALID_METERING_POINT,
                        type = "metering-point",
                        attributes = Attributes(),
                        relationships = Relationships()
                    )
                )
            )
        coEvery {
            meteringPointsService.getMeteringPointByIdAndElhubInternalId(VALID_UNEXISTING_METERING_POINT, any<String>())
        } returns Either.Left(ClientError.UnexpectedError(ClientRequestException(response = mockResponse, "Metering Point not Found")))

        coEvery { personService.findOrCreateByNin(VALID_PARTY.idValue) } returns Either.Right(Person(UUID.fromString(END_USER_INTERNAL_ID)))

        val handler = ChangeOfSupplierBusinessHandler(meteringPointsService = meteringPointsService, personService = personService)

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
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(ChangeOfSupplierValidationError.MissingRequestedFromName)
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
                        requestedTo = VALID_PARTY,
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
                    requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                    meta =
                    CreateRequestMeta(
                        requestedBy = VALID_PARTY,
                        requestedFrom = VALID_PARTY,
                        requestedFromName = "From",
                        requestedTo = VALID_PARTY,
                        requestedForMeteringPointId = VALID_UNEXISTING_METERING_POINT,
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
            command.validTo shouldBe defaultValidTo()
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
