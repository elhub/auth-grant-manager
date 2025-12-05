package no.elhub.auth.features.requests.create.requesttypes.changeofsupplier

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierValidationError
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.common.PartyIdentifierType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.RequestBusinessOrchestrator
import no.elhub.auth.features.requests.create.command.toRequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel

private const val VALID_METERING_POINT = "123456789012345678"

class ChangeOfSupplierConfirmationRequestTypeHandlerTest :
    FunSpec({

        test("returns validation error when requestedFromName is blank") {
            val orchestrator = RequestBusinessOrchestrator(ChangeOfSupplierBusinessHandler())
            val model =
                CreateRequestModel(
                    validTo = LocalDate.parse("2030-01-01"),
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

            val result = orchestrator.handle(model)

            result.shouldBeLeft(ChangeOfSupplierValidationError.MissingRequestedFromName)
        }

        test("builds RequestCommand for valid input") {
            val orchestrator = RequestBusinessOrchestrator(ChangeOfSupplierBusinessHandler())
            val model =
                CreateRequestModel(
                    validTo = LocalDate.parse("2030-01-01"),
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

            val command = orchestrator.handle(model).shouldBeRight()

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
