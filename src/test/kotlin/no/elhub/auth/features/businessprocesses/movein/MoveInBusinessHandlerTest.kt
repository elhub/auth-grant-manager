package no.elhub.auth.features.businessprocesses.movein

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
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
import no.elhub.auth.features.requests.create.model.today

private val VALID_PARTY = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "123456789")
private val VALID_METERING_POINT = "123456789012345678"
private val AUTHORIZED_PARTY = AuthorizationParty(resourceId = VALID_PARTY.idValue, type = PartyType.Organization)
private val VALID_START_DATE = LocalDate(2025, 1, 1)

class MoveInBusinessHandlerTest :
    FunSpec({

        val handler = MoveInBusinessHandler()

        test("request validation fails on missing startDate") {
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
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(MoveInValidationError.MissingStartDate)
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
                    ),
                )

            handler.validateAndReturnRequestCommand(model).shouldBeLeft(MoveInValidationError.InvalidMeteringPointId)
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
                    ),
                )

            val command = handler.validateAndReturnRequestCommand(model).shouldBeRight()

            command.type shouldBe AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson
            command.validTo shouldBe today().plus(DatePeriod(days = 28))
            command.meta.toMetaAttributes()["startDate"] shouldBe VALID_START_DATE.toString()
        }

        test("grant properties validTo is one year from acceptance") {
            val party = AuthorizationParty(resourceId = "party-1", type = PartyType.Organization)
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson,
                requestedBy = party,
                requestedFrom = party,
                requestedTo = party,
                validTo = today(),
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
