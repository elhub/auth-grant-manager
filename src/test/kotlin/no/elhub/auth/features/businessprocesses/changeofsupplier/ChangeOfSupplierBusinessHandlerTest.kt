package no.elhub.auth.features.businessprocesses.changeofsupplier

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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

private val VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "123456789")
private val VALID_METERING_POINT = "123456789012345678"
private val AUTHORIZED_PARTY = AuthorizationParty(resourceId = VALID_PARTY.idValue, type = PartyType.Organization)

class ChangeOfSupplierBusinessHandlerTest :
    FunSpec({

        val handler = ChangeOfSupplierBusinessHandler()

        test("request validation fails on missing requestedFromName") {
            val model =
                CreateRequestModel(
                    authorizedParty = AUTHORIZED_PARTY,
                    requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
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
                    requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
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

        test("request produces RequestCommand for valid input") {
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
                        requestedForMeteringPointId = VALID_METERING_POINT,
                        requestedForMeteringPointAddress = "addr",
                        balanceSupplierName = "Supplier",
                        balanceSupplierContractName = "Contract",
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.ChangeOfSupplierConfirmation
            command.validTo shouldBe defaultValidTo()
        }

        test("document produces DocumentCommand for valid input") {
            val model =
                CreateDocumentModel(
                    authorizedParty = AuthorizationParty(resourceId = VALID_PARTY.idValue, type = PartyType.Organization),
                    documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
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
