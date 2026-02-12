package no.elhub.auth.features.documents.create

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
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.ChangeOfEnergySupplierValidationError
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.defaultValidTo
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyError
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.SignatureService
import no.elhub.auth.features.documents.common.SignatureSigningError
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.AuthorizationScope

class HandlerTest : FunSpec({

    val requestedByIdentifier = PartyIdentifier(PartyIdentifierType.GlobalLocationNumber, "1234567890123")
    val requestedFromIdentifier = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "01010112345")
    val requestedToIdentifier = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "02020212345")

    val requestedByParty = AuthorizationParty(resourceId = requestedByIdentifier.idValue, type = PartyType.OrganizationEntity)
    val requestedFromParty = AuthorizationParty(resourceId = "person-1", type = PartyType.Person)
    val requestedToParty = AuthorizationParty(resourceId = "person-2", type = PartyType.Person)

    val meta =
        CreateDocumentMeta(
            requestedBy = requestedByIdentifier,
            requestedFrom = requestedFromIdentifier,
            requestedTo = requestedToIdentifier,
            requestedFromName = "Requested From",
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeteringPointAddress = "Address",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract"
        )

    val model =
        CreateDocumentModel(
            authorizedParty = requestedByParty,
            documentType = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
            meta = meta,
        )

    val commandMeta = object : DocumentMetaMarker {
        override fun toMetaAttributes(): Map<String, String> = mapOf("k" to "v")
    }

    val command =
        DocumentCommand(
            type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
            requestedFrom = requestedFromIdentifier,
            requestedTo = requestedToIdentifier,
            requestedBy = requestedByIdentifier,
            validTo = defaultValidTo().toTimeZoneOffsetDateTimeAtStartOfDay(),
            scopes = listOf(
                CreateScopeData(
                    authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                    authorizedResourceId = "123456789012345678",
                    permissionType = AuthorizationScope.PermissionType.ChangeOfEnergySupplierForPerson,
                )
            ),
            meta = commandMeta,
        )

    val unsignedFile = "file".toByteArray()
    val signedFile = "signed-file".toByteArray()

    fun stubPartyResolution(partyService: PartyService) {
        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns requestedFromParty.right()
        coEvery { partyService.resolve(requestedToIdentifier) } returns requestedToParty.right()
    }

    test("returns saved document when dependencies succeed") {
        val businessHandler = mockk<DocumentBusinessHandler>()
        val signatureService = mockk<SignatureService>()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnDocumentCommand(model) } returns command.right()
        every { fileGenerator.generate(requestedFromIdentifier.idValue, commandMeta) } returns unsignedFile.right()
        coEvery { signatureService.sign(unsignedFile) } returns signedFile.right()

        val savedDocument = AuthorizationDocument.create(
            type = command.type,
            file = signedFile,
            requestedBy = requestedByParty,
            requestedFrom = requestedFromParty,
            requestedTo = requestedToParty,
            properties = commandMeta.toMetaAttributes().toDocumentProperties(),
            validTo = command.validTo,
        )
        every { documentRepository.insert(any(), command.scopes) } returns savedDocument.right()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeRight(savedDocument)
        verify(exactly = 1) { documentRepository.insert(any(), command.scopes) }
    }

    test("returns RequestedPartyError when requestedBy cannot be resolved") {
        val businessHandler = mockk<DocumentBusinessHandler>(relaxed = true)
        val signatureService = mockk<SignatureService>(relaxed = true)
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        coEvery { partyService.resolve(requestedByIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnDocumentCommand(any()) }
    }

    test("returns AuthorizationError when requestedBy does not match authorized party") {
        val businessHandler = mockk<DocumentBusinessHandler>(relaxed = true)
        val signatureService = mockk<SignatureService>(relaxed = true)
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)
        val otherAuthorizedParty = AuthorizationParty(resourceId = "other", type = PartyType.OrganizationEntity)

        val response = handler(model.copy(authorizedParty = otherAuthorizedParty))

        response.shouldBeLeft(CreateError.AuthorizationError)
        coVerify(exactly = 0) { partyService.resolve(requestedFromIdentifier) }
        coVerify(exactly = 0) { businessHandler.validateAndReturnDocumentCommand(any()) }
    }

    test("returns RequestedFromPartyError when requestedFrom cannot be resolved") {
        val businessHandler = mockk<DocumentBusinessHandler>(relaxed = true)
        val signatureService = mockk<SignatureService>(relaxed = true)
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnDocumentCommand(any()) }
    }

    test("returns RequestedPartyError when requestedTo cannot be resolved") {
        val businessHandler = mockk<DocumentBusinessHandler>(relaxed = true)
        val signatureService = mockk<SignatureService>(relaxed = true)
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedByParty.right()
        coEvery { partyService.resolve(requestedFromIdentifier) } returns requestedFromParty.right()
        coEvery { partyService.resolve(requestedToIdentifier) } returns PartyError.PersonResolutionError.left()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeLeft(CreateError.RequestedPartyError)
        coVerify(exactly = 0) { businessHandler.validateAndReturnDocumentCommand(any()) }
    }

    test("returns BusinessValidationError when validation fails") {
        val businessHandler = mockk<DocumentBusinessHandler>()
        val signatureService = mockk<SignatureService>(relaxed = true)
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        stubPartyResolution(partyService)
        coEvery {
            businessHandler.validateAndReturnDocumentCommand(model)
        } returns BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.MissingRequestedFromName.message).left()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeLeft(
            CreateError.BusinessError(BusinessProcessError.Validation(ChangeOfEnergySupplierValidationError.MissingRequestedFromName.message))
        )

        verify(exactly = 0) { fileGenerator.generate(any(), any()) }
    }

    test("returns FileGenerationError when file generation fails") {
        val businessHandler = mockk<DocumentBusinessHandler>()
        val signatureService = mockk<SignatureService>(relaxed = true)
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnDocumentCommand(model) } returns command.right()
        every {
            fileGenerator.generate(requestedFromIdentifier.idValue, commandMeta)
        } returns DocumentGenerationError.ContentGenerationError.left()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeLeft(CreateError.FileGenerationError)
        coVerify(exactly = 0) { signatureService.sign(any()) }
    }

    test("returns SignFileError when signing fails") {
        val businessHandler = mockk<DocumentBusinessHandler>()
        val signatureService = mockk<SignatureService>()
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnDocumentCommand(model) } returns command.right()
        every { fileGenerator.generate(requestedFromIdentifier.idValue, commandMeta) } returns unsignedFile.right()
        coEvery {
            signatureService.sign(unsignedFile)
        } returns SignatureSigningError.SignatureFetchingError.left()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeLeft(CreateError.SignFileError(SignatureSigningError.SignatureFetchingError))
        verify(exactly = 0) { documentRepository.insert(any(), any()) }
    }

    test("returns PersistenceError when repository insert fails") {
        val businessHandler = mockk<DocumentBusinessHandler>()
        val signatureService = mockk<SignatureService>()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val fileGenerator = mockk<FileGenerator>()

        stubPartyResolution(partyService)
        coEvery { businessHandler.validateAndReturnDocumentCommand(model) } returns command.right()
        every { fileGenerator.generate(requestedFromIdentifier.idValue, commandMeta) } returns unsignedFile.right()
        coEvery { signatureService.sign(unsignedFile) } returns signedFile.right()
        every {
            documentRepository.insert(any(), command.scopes)
        } returns RepositoryWriteError.UnexpectedError.left()

        val handler = Handler(businessHandler, signatureService, documentRepository, partyService, fileGenerator)

        val response = handler(model)

        response.shouldBeLeft(CreateError.PersistenceError)
    }
})
