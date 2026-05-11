package no.elhub.auth.features.requests.create

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierValidationError
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyError
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.CreateRequestBusinessMeta
import no.elhub.auth.features.requests.common.CreateRequestBusinessModel
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker
import no.elhub.auth.features.requests.create.command.withTextVersion
import no.elhub.auth.features.requests.create.model.CreateRequestCoreMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel

class HandlerTest : FunSpec({

    val requestedByIdentifier = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "987654321")
    val requestedFromIdentifier = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "01010112345")
    val requestedToIdentifier = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "02020212345")

    val requestedByParty = AuthorizationParty(id = requestedByIdentifier.idValue, type = PartyType.OrganizationEntity)
    val requestedFromParty = AuthorizationParty(id = "person-1", type = PartyType.Person)
    val requestedToParty = AuthorizationParty(id = "person-2", type = PartyType.Person)

    val coreMeta =
        CreateRequestCoreMeta(
            requestedBy = requestedByIdentifier,
            requestedFrom = requestedFromIdentifier,
            requestedTo = requestedToIdentifier,
        )

    val businessMeta =
        CreateRequestBusinessMeta(
            requestedFromName = "Requested From",
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeteringPointAddress = "Address",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
            redirectURI = "https://example.com",
        )

    val model =
        CreateRequestModel(
            authorizedParty = requestedByParty,
            requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            coreMeta = coreMeta,
            businessMeta = businessMeta,
        )

    val businessModel =
        CreateRequestBusinessModel(
            authorizedParty = requestedByParty,
            requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            requestedBy = requestedByParty,
            requestedFrom = requestedFromParty,
            requestedTo = requestedToParty,
            meta = businessMeta,
        )

    val commandMeta = object : RequestMetaMarker {
        override fun toRequestMetaAttributes(): Map<String, String> =
            mapOf("k" to "v").withTextVersion("v1")
    }

    val command =
        RequestCommand(
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            validTo = LocalDate(2025, 1, 1).toTimeZoneOffsetDateTimeAtStartOfDay(),
            scopes = listOf(
                CreateScopeData(
                    authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                    authorizedResourceId = "123456789012345678",
                    permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson,
                )
            ),
            meta = commandMeta,
        )

    fun stubPartyResolution(partyService: PartyService) {
        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns requestedFromParty.right()
        coEvery { partyService.resolve(requestedToIdentifier) } returns requestedToParty.right()
    }

    test("returns InvalidPartyTypeError when authorized party is not an OrganizationEntity") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>(relaxed = true)
        val requestRepo = mockk<RequestRepository>(relaxed = true)

        val handler = Handler(businessHandler, partyService, requestRepo)

        val response = handler(model.copy(authorizedParty = AuthorizationParty(id = "person-1", type = PartyType.Person)))

        response.shouldBeLeft(CreateError.InvalidPartyTypeError)
        coVerify(exactly = 0) { partyService.resolve(any()) }
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns saved request when dependencies succeed") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>()
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnRequestCommand(businessModel) } returns command.right()

        val savedRequest =
            AuthorizationRequest.create(
                type = command.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = command.validTo,
            )

        val expectedProperties =
            commandMeta
                .toRequestMetaAttributes()
                .map { (key, value) ->
                    AuthorizationRequestProperty(
                        requestId = savedRequest.id,
                        key = key,
                        value = value,
                    )
                }

        coEvery { requestRepo.insert(any(), any()) } returns savedRequest.copy(properties = expectedProperties).right()

        val handler = Handler(businessHandler, partyService, requestRepo)

        val response = handler(model)

        response.shouldBeRight(savedRequest.copy(properties = expectedProperties))
        coVerify(exactly = 1) { requestRepo.insert(any(), any()) }
        coVerify(exactly = 1) { businessHandler.validateAndReturnRequestCommand(businessModel) }
    }

    test("returns RequestedPartyError when requestedBy cannot be resolved") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, partyService, requestRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns AuthorizationError when requestedBy does not match authorized party") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()

        val handler = Handler(businessHandler, partyService, requestRepo)
        val otherAuthorizedParty = AuthorizationParty(id = "other", type = PartyType.OrganizationEntity)

        val response = handler(model.copy(authorizedParty = otherAuthorizedParty))

        response.shouldBeLeft(CreateError.AuthorizationError)
        coVerify(exactly = 0) { partyService.resolve(requestedFromIdentifier) }
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns RequestedFromPartyError when requestedFrom cannot be resolved") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, partyService, requestRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns RequestedPartyError when requestedTo cannot be resolved") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns requestedFromParty.right()
        coEvery { partyService.resolve(requestedToIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, partyService, requestRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns ValidationError when business validation fails") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>()
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)

        stubPartyResolution(partyService)
        coEvery {
            businessHandler.validateAndReturnRequestCommand(businessModel)
        } returns BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.MissingRequestedFromName.message).left()

        val handler = Handler(businessHandler, partyService, requestRepo)

        val response = handler(model)

        response.shouldBeLeft(
            CreateError.BusinessError(BusinessProcessError.Validation(ChangeOfBalanceSupplierValidationError.MissingRequestedFromName.message))
        )
        coVerify(exactly = 0) { requestRepo.insert(any(), any()) }
    }

    test("returns PersistenceError when repository insert fails") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>()
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnRequestCommand(businessModel) } returns command.right()
        coEvery { requestRepo.insert(any(), any()) } returns RepositoryWriteError.UnexpectedError.left()

        val handler = Handler(businessHandler, partyService, requestRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.PersistenceError)
    }
})
