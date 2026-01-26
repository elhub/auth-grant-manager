package no.elhub.auth.features.requests.create.requesttypes.changeofsupplier

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierValidationError
import no.elhub.auth.features.businessprocesses.structuredata.MeteringPointsService
import no.elhub.auth.features.common.Person
import no.elhub.auth.features.common.PersonService
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import java.util.UUID

private const val VALID_METERING_POINT = "123456789012345678"

class ChangeOfSupplierConfirmationRequestTypeHandlerTest :
    FunSpec({

        val authorizedParty = AuthorizationParty(resourceId = "987654321", type = PartyType.Organization)
        val personService = mockk<PersonService>()
        coEvery { personService.findOrCreateByNin("12345678902") } returns Either.Right(Person(UUID.randomUUID()))
        val handler = ChangeOfSupplierBusinessHandler(mockk<MeteringPointsService>(relaxed = true), personService)

        test("returns validation error when requestedFromName is blank") {
            val model =
                CreateRequestModel(
                    authorizedParty = authorizedParty,
                    requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                    meta =
                    CreateRequestMeta(
                        requestedBy = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "987654321"),
                        requestedFrom = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678901"),
                        requestedFromName = "",
                        requestedTo = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678902"),
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "Some address",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                    ),
                )

            val result = handler.validateAndReturnRequestCommand(model)

            result.shouldBeLeft(ChangeOfSupplierValidationError.MissingRequestedFromName)
        }

        test("builds RequestCommand for valid input") {
            val model =
                CreateRequestModel(
                    authorizedParty = authorizedParty,
                    requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                    meta =
                    CreateRequestMeta(
                        requestedBy = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "987654321"),
                        requestedFrom = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678901"),
                        requestedFromName = "Supplier AS",
                        requestedTo = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678902"),
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "Some address",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.ChangeOfSupplierConfirmation
            command.requestedBy shouldBe model.meta.requestedBy
            command.requestedFrom shouldBe model.meta.requestedFrom
            command.requestedTo shouldBe model.meta.requestedTo

            val metaAttributes = command.meta.toMetaAttributes()
            metaAttributes["requestedFromName"] shouldBe "Supplier AS"
            metaAttributes["requestedForMeteringPointId"] shouldBe VALID_METERING_POINT
            metaAttributes["requestedForMeteringPointAddress"] shouldBe "Some address"
            metaAttributes["balanceSupplierContractName"] shouldBe "Contract"
        }
    })
