package no.elhub.auth.features.documents.confirm

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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.defaultValidTo
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.today
import no.elhub.auth.features.common.RepositoryReadError
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
import no.elhub.auth.features.documents.common.SignatureValidationError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class HandlerTest : FunSpec({

    val authorizationParty = AuthorizationParty(id = "1234567890123", type = PartyType.OrganizationEntity)
    val requestedFrom = AuthorizationParty(id = "requested-from", type = PartyType.Person)
    val requestedTo = AuthorizationParty(id = "requested-to", type = PartyType.Person)
    val signedFile = "signed".toByteArray()
    val signatoryIdentifier =
        PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = "01019012345")
    val grantPropertiesRepository = mockk<GrantPropertiesRepository>(relaxed = true)
    val businessHandler = mockk<DocumentBusinessHandler>()

    fun createDocument(
        documentId: UUID,
        status: AuthorizationDocument.Status = AuthorizationDocument.Status.Pending,
        validTo: OffsetDateTime = defaultValidTo().toTimeZoneOffsetDateTimeAtStartOfDay(),
        requestedBy: AuthorizationParty = authorizationParty,
        requestedFromParty: AuthorizationParty = requestedFrom,
        requestedToParty: AuthorizationParty = requestedTo
    ): AuthorizationDocument =
        AuthorizationDocument.create(
            type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
            file = "file".toByteArray(),
            requestedBy = requestedBy,
            requestedFrom = requestedFromParty,
            requestedTo = requestedToParty,
            properties = emptyList(),
            validTo = validTo
        ).copy(
            id = documentId,
            status = status
        )

    fun handler(
        documentRepository: DocumentRepository,
        partyService: PartyService,
        signatureService: SignatureService,
        grantRepository: GrantRepository
    ) = Handler(
        businessHandler = businessHandler,
        documentRepository = documentRepository,
        grantRepository = grantRepository,
        partyService = partyService,
        signatureService = signatureService,
        grantPropertiesRepository = grantPropertiesRepository,
    )

    test("returns DocumentNotFoundError when document is missing") {
        val documentId = UUID.randomUUID()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns RepositoryReadError.NotFoundError.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.DocumentNotFoundError)
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns DocumentReadError when repository read fails") {
        val documentId = UUID.randomUUID()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns RepositoryReadError.UnexpectedError.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.DocumentReadError)
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns InvalidRequestedByError when authorizedParty does not match") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = AuthorizationParty(id = "different", type = PartyType.Person),
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.InvalidRequestedByError)
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns IllegalTransitionError when document is not pending") {
        val documentId = UUID.randomUUID()
        val document = createDocument(
            documentId = documentId,
            status = AuthorizationDocument.Status.Signed
        )

        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.IllegalStateError)
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns ExpiredError when document validity period has passed") {
        val documentId = UUID.randomUUID()

        val document = createDocument(
            documentId = documentId,
            validTo = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10)
        )

        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.ExpiredError)
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns ValidateSignaturesError when signature validation fails") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            SignatureValidationError.MissingElhubSignature.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(
            ConfirmError.ValidateSignaturesError(SignatureValidationError.MissingElhubSignature)
        )
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 1) { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns RequestedByResolutionError when signatory cannot be resolved") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns PartyError.PersonResolutionError.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.SignatoryResolutionError)
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 1) { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) }
        coVerify(exactly = 1) { partyService.resolve(signatoryIdentifier) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns SignatoryNotAllowedToSignDocument when signatory differs from requestedTo") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns AuthorizationParty(
            id = "another",
            type = PartyType.Person
        ).right()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.SignatoryNotAllowedToSignDocument)
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 1) { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) }
        coVerify(exactly = 1) { partyService.resolve(signatoryIdentifier) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns DocumentNotFoundError when confirm cannot find document") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        every {
            documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo)
        } returns RepositoryWriteError.NotFoundError.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.DocumentNotFoundError)
        verify(exactly = 1) { documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns DocumentUpdateError when confirm conflicts") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        every {
            documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo)
        } returns RepositoryWriteError.ConflictError.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.DocumentUpdateError)
        verify(exactly = 1) { documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns ScopeReadError when scope lookup fails") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val confirmedDocument = document.copy(status = AuthorizationDocument.Status.Signed)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        every {
            documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo)
        } returns confirmedDocument.right()
        every { documentRepository.findScopeIds(confirmedDocument.id) } returns
            RepositoryReadError.UnexpectedError.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.ScopeReadError)
        verify(exactly = 1) { documentRepository.findScopeIds(confirmedDocument.id) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }

    test("returns GrantCreationError when grant insert fails") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val confirmedDocument = document.copy(status = AuthorizationDocument.Status.Signed)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)
        val scopeId = UUID.randomUUID()

        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = today(),
            validTo = today().plus(DatePeriod(years = 1)),
            meta = emptyMap()
        )
        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        every {
            documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo)
        } returns confirmedDocument.right()
        every { documentRepository.findScopeIds(confirmedDocument.id) } returns listOf(scopeId).right()
        every { grantRepository.insert(any(), any()) } returns RepositoryWriteError.UnexpectedError.left()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.GrantCreationError)
        verify(exactly = 1) { grantRepository.insert(any(), listOf(scopeId)) }
    }

    test("confirms document and creates grant on success") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val confirmedDocument = document.copy(status = AuthorizationDocument.Status.Signed)
        val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val grantRepository = mockk<GrantRepository>()
        val validFrom = today()
        val validTo = today().plus(DatePeriod(years = 1))

        val expectedGrant = AuthorizationGrant.create(
            grantedFor = confirmedDocument.requestedFrom,
            grantedBy = requestedTo,
            grantedTo = confirmedDocument.requestedBy,
            sourceType = AuthorizationGrant.SourceType.Document,
            sourceId = confirmedDocument.id,
            scopeIds = scopeIds,
            validFrom = validFrom.toTimeZoneOffsetDateTimeAtStartOfDay(),
            validTo = validTo.toTimeZoneOffsetDateTimeAtStartOfDay()
        )

        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = today(),
            validTo = today().plus(DatePeriod(years = 1)),
            meta = emptyMap()
        )

        every { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        every {
            documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo)
        } returns confirmedDocument.right()
        every { documentRepository.findScopeIds(confirmedDocument.id) } returns scopeIds.right()
        every { grantRepository.insert(any(), scopeIds) } returns expectedGrant.right()

        val result = handler(documentRepository, partyService, signatureService, grantRepository)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeRight()
        verify(exactly = 1) { documentRepository.confirm(documentId, signedFile, requestedFrom, requestedTo) }
        verify(exactly = 1) { documentRepository.findScopeIds(confirmedDocument.id) }
        verify(exactly = 1) { grantRepository.insert(any(), scopeIds) }
    }
})
