package no.elhub.auth.features.requests.create

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierValidationError
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
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker
import no.elhub.auth.features.requests.create.model.CreateRequestMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel

class HandlerTest : FunSpec({

    val requestedByIdentifier = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "987654321")
    val requestedFromIdentifier = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "01010112345")
    val requestedToIdentifier = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "02020212345")

    val requestedByParty = AuthorizationParty(resourceId = requestedByIdentifier.idValue, type = PartyType.Organization)
    val requestedFromParty = AuthorizationParty(resourceId = "person-1", type = PartyType.Person)
    val requestedToParty = AuthorizationParty(resourceId = "person-2", type = PartyType.Person)

    val meta =
        CreateRequestMeta(
            requestedBy = requestedByIdentifier,
            requestedFrom = requestedFromIdentifier,
            requestedFromName = "Requested From",
            requestedTo = requestedToIdentifier,
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeteringPointAddress = "Address",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
            redirectURI = "https://example.com",
        )

    val model =
        CreateRequestModel(
            authorizedParty = requestedByParty,
            requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
            meta = meta,
        )

    val commandMeta = object : RequestMetaMarker {
        override fun toMetaAttributes(): Map<String, String> = mapOf("k" to "v")
    }

    val command =
        RequestCommand(
            type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
            requestedFrom = requestedFromIdentifier,
            requestedBy = requestedByIdentifier,
            requestedTo = requestedToIdentifier,
            validTo = LocalDate(2025, 1, 1).toTimeZoneOffsetDateTimeAtStartOfDay(),
            scopes = listOf(
                CreateScopeData(
                    authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                    authorizedResourceId = "123456789012345678",
                    permissionType = AuthorizationScope.PermissionType.ChangeOfEnergySupplierForPerson,
                )
            ),
            meta = commandMeta,
        )

    fun stubPartyResolution(partyService: PartyService) {
        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns requestedFromParty.right()
        coEvery { partyService.resolve(requestedToIdentifier) } returns requestedToParty.right()
    }

    test("returns saved request when dependencies succeed") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>()
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>()
        val requestPropertyRepo = mockk<RequestPropertiesRepository>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnRequestCommand(model) } returns command.right()

        val savedRequest =
            AuthorizationRequest.create(
                type = command.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = command.validTo,
            )

        every { requestRepo.insert(any(), any()) } returns savedRequest.right()
        every { requestPropertyRepo.insert(any()) } returns Unit.right()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)

        val response = handler(model)

        val expectedProperties =
            commandMeta
                .toMetaAttributes()
                .map { (key, value) ->
                    AuthorizationRequestProperty(
                        requestId = savedRequest.id,
                        key = key,
                        value = value,
                    )
                }
        val expectedRequest = savedRequest.copy(properties = expectedProperties)

        response.shouldBeRight(expectedRequest)
        verify(exactly = 1) { requestRepo.insert(any(), any()) }
        verify(exactly = 1) { requestPropertyRepo.insert(expectedProperties) }
    }

    test("returns RequestedPartyError when requestedBy cannot be resolved") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)
        val requestPropertyRepo = mockk<RequestPropertiesRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns AuthorizationError when requestedBy does not match authorized party") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)
        val requestPropertyRepo = mockk<RequestPropertiesRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)
        val otherAuthorizedParty = AuthorizationParty(resourceId = "other", type = PartyType.Organization)

        val response = handler(model.copy(authorizedParty = otherAuthorizedParty))

        response.shouldBeLeft(CreateError.AuthorizationError)
        coVerify(exactly = 0) { partyService.resolve(requestedFromIdentifier) }
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns RequestedFromPartyError when requestedFrom cannot be resolved") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)
        val requestPropertyRepo = mockk<RequestPropertiesRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns RequestedPartyError when requestedTo cannot be resolved") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>(relaxed = true)
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)
        val requestPropertyRepo = mockk<RequestPropertiesRepository>(relaxed = true)

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns requestedFromParty.right()
        coEvery { partyService.resolve(requestedToIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnRequestCommand(any()) }
    }

    test("returns ValidationError when business validation fails") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>()
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>(relaxed = true)
        val requestPropertyRepo = mockk<RequestPropertiesRepository>(relaxed = true)

        stubPartyResolution(partyService)
        coEvery {
            businessHandler.validateAndReturnRequestCommand(model)
        } returns ChangeOfSupplierValidationError.MissingRequestedFromName.left()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.ValidationError(ChangeOfSupplierValidationError.MissingRequestedFromName))
        verify(exactly = 0) { requestRepo.insert(any(), any()) }
    }

    test("returns PersistenceError when repository insert fails") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>()
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>()
        val requestPropertyRepo = mockk<RequestPropertiesRepository>(relaxed = true)

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnRequestCommand(model) } returns command.right()
        every { requestRepo.insert(any(), any()) } returns RepositoryWriteError.UnexpectedError.left()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.PersistenceError)
        verify(exactly = 0) { requestPropertyRepo.insert(any()) }
    }

    test("returns PersistenceError when property insert fails") {
        val businessHandler = mockk<ProxyRequestBusinessHandler>()
        val partyService = mockk<PartyService>()
        val requestRepo = mockk<RequestRepository>()
        val requestPropertyRepo = mockk<RequestPropertiesRepository>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnRequestCommand(model) } returns command.right()

        val savedRequest =
            AuthorizationRequest.create(
                type = command.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = command.validTo,
            )

        every { requestRepo.insert(any(), any()) } returns savedRequest.right()
        every { requestPropertyRepo.insert(any()) } returns RepositoryWriteError.UnexpectedError.left()

        val handler = Handler(businessHandler, partyService, requestRepo, requestPropertyRepo)

        val response = handler(model)

        response.shouldBeLeft(CreateError.PersistenceError)
    }
})
