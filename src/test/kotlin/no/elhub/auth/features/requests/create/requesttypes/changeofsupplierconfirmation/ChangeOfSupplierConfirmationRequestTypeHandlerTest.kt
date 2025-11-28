package no.elhub.auth.features.requests.create.requesttypes.changeofsupplierconfirmation

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.common.PartyIdentifierType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel

private const val VALID_METERING_POINT = "123456789012345678"

class ChangeOfSupplierConfirmationRequestTypeHandlerTest :
    FunSpec({

        test("returns validation error when requestedFromName is blank") {
            val handler = ChangeOfSupplierConfirmationRequestTypeHandler()
            val model =
                CreateRequestModel(
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

            val result = handler.handle(model)

            result.shouldBeLeft(ChangeOfSupplierConfirmationValidationError.MissingRequestedFromName)
        }

        test("builds RequestCommand for valid input") {
            val handler = ChangeOfSupplierConfirmationRequestTypeHandler()
            val model =
                CreateRequestModel(
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

            val command = handler.handle(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.ChangeOfSupplierConfirmation
            command.requestedBy shouldBe model.meta.requestedBy
            command.requestedFrom shouldBe model.meta.requestedFrom
            command.requestedTo shouldBe model.meta.requestedTo
            command.validTo.shouldNotBeNull()

            val metaAttributes = command.meta.toMetaAttributes()
            metaAttributes["requestedFromName"] shouldBe "Supplier AS"
            metaAttributes["requestedForMeteringPointId"] shouldBe VALID_METERING_POINT
            metaAttributes["requestedForMeteringPointAddress"] shouldBe "Some address"
            metaAttributes["balanceSupplierContractName"] shouldBe "Contract"
        }
    })
