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
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyError
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.common.todayOslo
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.ConfirmWithGrantError
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.SignatureService
import no.elhub.auth.features.documents.common.SignatureValidationError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.CreateGrantProperties
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
    val businessHandler = mockk<DocumentBusinessHandler>()

    fun createDocument(
        documentId: UUID,
        status: AuthorizationDocument.Status = AuthorizationDocument.Status.Pending,
        validTo: OffsetDateTime = todayOslo().plus(DatePeriod(days = 30)).toTimeZoneOffsetDateTimeAtStartOfDay(),
        requestedBy: AuthorizationParty = authorizationParty,
        requestedFromParty: AuthorizationParty = requestedFrom,
        requestedToParty: AuthorizationParty = requestedTo,
        signedBy: AuthorizationParty? = null,
    ): AuthorizationDocument = AuthorizationDocument(
        id = UUID.randomUUID(),
        type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
        status = status,
        file = "file".toByteArray(),
        requestedBy = requestedBy,
        requestedFrom = requestedFrom,
        requestedTo = requestedTo,
        properties = emptyList(),
        validTo = validTo,
        createdAt = currentTimeUtc(),
        updatedAt = currentTimeUtc(),
        signedBy = signedBy,
    ).copy(
        id = documentId,
    )

    fun handler(
        documentRepository: DocumentRepository,
        partyService: PartyService,
        signatureService: SignatureService,
    ) = Handler(
        businessHandler = businessHandler,
        documentRepository = documentRepository,
        partyService = partyService,
        signatureService = signatureService,
    )

    test("returns DocumentNotFoundError when document is missing") {
        val documentId = UUID.randomUUID()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns RepositoryReadError.NotFoundError.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.DocumentNotFoundError)
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns DocumentReadError when repository read fails") {
        val documentId = UUID.randomUUID()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns RepositoryReadError.UnexpectedError.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.DocumentReadError)
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns InvalidRequestedByError when authorizedParty does not match") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns document.right()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = AuthorizationParty(id = "different", type = PartyType.Person),
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.InvalidRequestedByError)
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns IllegalStateError when document is not pending") {
        val documentId = UUID.randomUUID()
        val document = createDocument(
            documentId = documentId,
            status = AuthorizationDocument.Status.Signed
        )

        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns document.right()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.IllegalStateError("AuthorizationDocument must be in 'Pending' status to confirm."))
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns IllegalStateError when document is already signed") {
        val documentId = UUID.randomUUID()
        val document = createDocument(
            documentId = documentId,
            status = AuthorizationDocument.Status.Signed,
            signedBy = AuthorizationParty("some-id", PartyType.Person),
        )

        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns document.right()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.IllegalStateError("AuthorizationDocument must be in 'Pending' status to confirm."))
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
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

        coEvery { documentRepository.find(documentId) } returns document.right()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.ExpiredError)
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { signatureService.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns ValidateSignaturesError when signature validation fails") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            SignatureValidationError.MissingElhubSignature.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(
            ConfirmError.ValidateSignaturesError(SignatureValidationError.MissingElhubSignature)
        )
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 1) { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns RequestedByResolutionError when signatory cannot be resolved") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns PartyError.PersonResolutionError.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.SignatoryResolutionError)
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 1) { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) }
        coVerify(exactly = 1) { partyService.resolve(signatoryIdentifier) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns SignatoryNotAllowedToSignDocument when signatory differs from requestedTo") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns AuthorizationParty(
            id = "another",
            type = PartyType.Person
        ).right()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.SignatoryNotAllowedToSignDocument)
        coVerify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 1) { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) }
        coVerify(exactly = 1) { partyService.resolve(signatoryIdentifier) }
        coVerify(exactly = 0) { documentRepository.findScopeIds(any()) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns ScopeReadError when scope lookup fails") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        coEvery { documentRepository.findScopeIds(documentId) } returns
            RepositoryReadError.UnexpectedError.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(
                documentId = documentId,
                authorizedParty = authorizationParty,
                signedFile = signedFile
            )
        )

        result.shouldBeLeft(ConfirmError.ScopeReadError)
        coVerify(exactly = 1) { documentRepository.findScopeIds(documentId) }
        coVerify(exactly = 0) { documentRepository.confirmWithGrant(any(), any(), any(), any(), any()) }
    }

    test("returns DocumentNotFoundError when confirmWithGrant reports document not found") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val scopeId = UUID.randomUUID()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = todayOslo(),
            validTo = todayOslo().plus(DatePeriod(years = 1)),
            meta = emptyMap()
        )
        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        coEvery { documentRepository.findScopeIds(documentId) } returns listOf(scopeId).right()
        coEvery {
            documentRepository.confirmWithGrant(documentId, signedFile, requestedTo, any(), any())
        } returns ConfirmWithGrantError.DocumentError.NotFound.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(documentId = documentId, authorizedParty = authorizationParty, signedFile = signedFile)
        )

        result.shouldBeLeft(ConfirmError.DocumentNotFoundError)
        coVerify(exactly = 1) {
            documentRepository.confirmWithGrant(documentId, signedFile, requestedTo, any(), any())
        }
    }

    test("returns DocumentUpdateError when confirmWithGrant reports a conflict") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val scopeId = UUID.randomUUID()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = todayOslo(),
            validTo = todayOslo().plus(DatePeriod(years = 1)),
            meta = emptyMap()
        )
        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        coEvery { documentRepository.findScopeIds(documentId) } returns listOf(scopeId).right()
        coEvery {
            documentRepository.confirmWithGrant(documentId, signedFile, requestedTo, any(), any())
        } returns ConfirmWithGrantError.DocumentError.Conflict.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(documentId = documentId, authorizedParty = authorizationParty, signedFile = signedFile)
        )

        result.shouldBeLeft(ConfirmError.DocumentUpdateError)
        coVerify(exactly = 1) {
            documentRepository.confirmWithGrant(documentId, signedFile, requestedTo, any(), any())
        }
    }

    test("returns GrantCreationError when confirmWithGrant reports grant failure") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val scopeId = UUID.randomUUID()
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()

        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = todayOslo(),
            validTo = todayOslo().plus(DatePeriod(years = 1)),
            meta = emptyMap()
        )
        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        coEvery { documentRepository.findScopeIds(documentId) } returns listOf(scopeId).right()
        coEvery {
            documentRepository.confirmWithGrant(documentId, signedFile, requestedTo, any(), any())
        } returns ConfirmWithGrantError.GrantError.left()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(documentId = documentId, authorizedParty = authorizationParty, signedFile = signedFile)
        )

        result.shouldBeLeft(ConfirmError.GrantCreationError)
        coVerify(exactly = 1) {
            documentRepository.confirmWithGrant(documentId, signedFile, requestedTo, any(), any())
        }
    }

    test("confirms document and creates grant on success") {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId)
        val confirmedDocument = document.copy(status = AuthorizationDocument.Status.Signed)
        val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val signatureService = mockk<SignatureService>()
        val validFrom = todayOslo()
        val validTo = todayOslo().plus(DatePeriod(years = 1))

        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = validFrom,
            validTo = validTo,
            meta = emptyMap()
        )
        coEvery { documentRepository.find(documentId) } returns document.right()
        every { signatureService.validateSignaturesAndReturnSignatory(signedFile, document.file) } returns
            signatoryIdentifier.right()
        coEvery { partyService.resolve(signatoryIdentifier) } returns requestedTo.right()
        coEvery { documentRepository.findScopeIds(documentId) } returns scopeIds.right()
        coEvery {
            documentRepository.confirmWithGrant(
                documentId,
                signedFile,
                requestedTo,
                any(),
                any(),
            )
        } returns confirmedDocument.right()

        val result = handler(documentRepository, partyService, signatureService)(
            Command(documentId = documentId, authorizedParty = authorizationParty, signedFile = signedFile)
        )

        result.shouldBeRight()
        coVerify(exactly = 1) { documentRepository.findScopeIds(documentId) }
        coVerify(exactly = 1) {
            documentRepository.confirmWithGrant(
                documentId,
                signedFile,
                requestedTo,
                match { grant ->
                    grant.grantedFor == document.requestedFrom &&
                        grant.grantedBy == requestedTo &&
                        grant.grantedTo == document.requestedBy &&
                        grant.sourceType == AuthorizationGrant.SourceType.Document &&
                        grant.sourceId == document.id &&
                        grant.scopeIds == scopeIds &&
                        grant.validFrom == validFrom.toTimeZoneOffsetDateTimeAtStartOfDay() &&
                        grant.validTo == validTo.toTimeZoneOffsetDateTimeAtStartOfDay()
                },
                any()
            )
        }
    }
})
