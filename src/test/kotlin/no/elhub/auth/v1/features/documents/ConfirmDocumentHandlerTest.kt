package no.elhub.auth.v1.features.documents

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.documents.pdf.PdfSignatory
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.common.documents.pdf.PdfValidationException
import no.elhub.auth.v0.features.common.party.PartyError
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.repositories.DocumentRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.auth.v1.repositories.RepositoryWriteError
import java.util.UUID
import kotlin.time.Instant

class ConfirmDocumentHandlerTest : FunSpec({
    val repository = mockk<DocumentRepository>()
    val partyService = mockk<PartyService>()
    val validator = mockk<PdfSignatureValidator>()
    val handler = ConfirmDocumentHandler(repository, partyService, validator)
    val requestedBy = AuthorizationParty("requested-by", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("requested-principal", PartyType.Organization)
    val requestedOf = AuthorizationParty("requested-of", PartyType.Person)
    val signedFile = "%PDF-1.7 signed".toByteArray()
    val originalFile = "%PDF-1.7 original".toByteArray()
    val nin = "01019012345"
    val identifier = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, nin)
    val ids = setOf(MeteringPointId.create("707057500000000001"), MeteringPointId.create("707057500000000002"))
    val document = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = listOf(
            RequestedAuthorizationScope(
                ResourceType.MeteringPointContract,
                listOf(
                    AuthorizationScopeConstraint(
                        AuthorizationScopeConstraintKind.AppliesTo,
                        AuthorizationScopeConstraintAttribute.MeteringPointId,
                        ResourceConstraint.MeteringPoints(ids),
                    ),
                    AuthorizationScopeConstraint(
                        AuthorizationScopeConstraintKind.AllowedChanges,
                        AuthorizationScopeConstraintAttribute.ContractType,
                        ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
                    ),
                ),
            )
        ),
        externalReference = "contract-123",
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = requestedBy,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        pdfBytes = originalFile,
    )
    val id = UUID.fromString(document.id)
    val frameworkDocument = document.copy(
        documentType = AuthorizationDocumentType.EnergySupplierFrameworkAgreement,
        requestedScopes = listOf(
            RequestedAuthorizationScope(
                ResourceType.MeteringPointContract,
                listOf(
                    AuthorizationScopeConstraint(
                        AuthorizationScopeConstraintKind.AllowedChanges,
                        AuthorizationScopeConstraintAttribute.ContractType,
                        ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier)),
                    ),
                ),
            ),
        ),
        requestedGrantValidFrom = Instant.parse("2098-01-01T00:00:00Z"),
        requestedGrantValidTo = Instant.parse("2099-01-01T00:00:00Z"),
    )

    beforeTest {
        clearMocks(repository, partyService, validator)
        coEvery { repository.find(id) } returns document
    }

    test("rejects an authorized party other than requestedBy before validating the PDF") {
        shouldThrow<ConfirmDocumentError.NotAuthorized> {
            handler(id, requestedBy.copy(id = "other"), signedFile)
        }
        verify(exactly = 0) { validator.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    listOf(AuthorizationDocumentStatus.Approved, AuthorizationDocumentStatus.Expired).forEach { status ->
        test("rejects a document in $status status without validating the PDF") {
            coEvery { repository.find(id) } returns document.copy(status = status)
            shouldThrow<ConfirmDocumentError.InvalidStatus> { handler(id, requestedBy, signedFile) }
            verify(exactly = 0) { validator.validateSignaturesAndReturnSignatory(any(), any()) }
            coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
        }
    }

    test("propagates a missing document without validating the PDF") {
        val error = RepositoryReadError.NotFoundError("Missing document")
        coEvery { repository.find(id) } throws error
        shouldThrow<RepositoryReadError.NotFoundError> { handler(id, requestedBy, signedFile) } shouldBeSameInstanceAs error
        verify(exactly = 0) { validator.validateSignaturesAndReturnSignatory(any(), any()) }
        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    test("propagates a repository read failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { repository.find(id) } throws error
        shouldThrow<RepositoryReadError.UnexpectedError> { handler(id, requestedBy, signedFile) } shouldBeSameInstanceAs error
        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    test("propagates PDF validation failures without resolving a signatory or confirming") {
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } throws
            PdfValidationException.OriginalDocumentMismatch
        shouldThrow<PdfValidationException.OriginalDocumentMismatch> { handler(id, requestedBy, signedFile) }
        coVerify(exactly = 0) { partyService.resolve(any()) }
        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    test("rejects an unresolvable signatory") {
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } returns PdfSignatory(nin)
        coEvery { partyService.resolve(identifier) } returns PartyError.PersonResolutionError.left()
        shouldThrow<ConfirmDocumentError.SignatoryResolutionFailed> { handler(id, requestedBy, signedFile) }
        coVerify(exactly = 1) { partyService.resolve(identifier) }
        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    test("rejects a signatory other than requestedOf") {
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } returns PdfSignatory(nin)
        coEvery { partyService.resolve(identifier) } returns requestedOf.copy(id = "other").right()
        shouldThrow<ConfirmDocumentError.SignatoryNotAllowed> { handler(id, requestedBy, signedFile) }
        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    test("rejects invalid document scopes without confirming the document") {
        val invalidScope = document.requestedScopes.single().copy(constraints = emptyList())
        coEvery { repository.find(id) } returns document.copy(requestedScopes = listOf(invalidScope))
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } returns PdfSignatory(nin)
        coEvery { partyService.resolve(identifier) } returns requestedOf.right()

        shouldThrow<InvalidDocumentScopes> { handler(id, requestedBy, signedFile) }

        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    test("allows requestedBy to confirm with the signed bytes, resolved requestedOf and one grant per metering point") {
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } returns PdfSignatory(nin)
        coEvery { partyService.resolve(identifier) } returns requestedOf.right()
        val grants = document.toGrants(requestedOf)
        val confirmed = document.copy(status = AuthorizationDocumentStatus.Approved, authorizationGrants = grants)
        coEvery { repository.confirm(id, signedFile, requestedOf, any()) } returns confirmed

        handler(id, requestedBy, signedFile) shouldBeSameInstanceAs confirmed

        verify(exactly = 1) { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) }
        coVerify(exactly = 1) { partyService.resolve(identifier) }
        coVerify(exactly = 1) {
            repository.confirm(
                id,
                signedFile,
                requestedOf,
                match { produced ->
                    produced.size == ids.size && produced.map { it.scopes }.toSet() == grants.map { it.scopes }.toSet() &&
                        produced.all {
                            it.sourceId == id && it.principal == requestedPrincipal && it.grantedBy == requestedOf &&
                                it.grantedTo == requestedBy && it.approvedBy == requestedOf
                        }
                }
            )
        }
    }

    test("confirms a framework agreement with exactly one persistent Document-source grant") {
        coEvery { repository.find(id) } returns frameworkDocument
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } returns PdfSignatory(nin)
        coEvery { partyService.resolve(identifier) } returns requestedOf.right()
        val grants = slot<List<AuthorizationGrant>>()
        val confirmed = frameworkDocument.copy(status = AuthorizationDocumentStatus.Approved)
        coEvery { repository.confirm(id, signedFile, requestedOf, capture(grants)) } returns confirmed

        handler(id, requestedBy, signedFile) shouldBeSameInstanceAs confirmed

        val grant = grants.captured.single()
        grant.type shouldBe AuthorizationGrantType.Persistent
        grant.sourceType shouldBe AuthorizationGrantSourceType.Document
        grant.sourceId shouldBe id
        grant.principal shouldBe requestedPrincipal
        grant.grantedBy shouldBe requestedOf
        grant.grantedTo shouldBe requestedBy
        grant.approvedBy shouldBe requestedOf
        grant.validFrom shouldBe frameworkDocument.requestedGrantValidFrom
        grant.validTo shouldBe frameworkDocument.requestedGrantValidTo
        grant.scopes shouldBe listOf(
            GrantedAuthorizationScope(
                capability = PermissionCapability.Write,
                resourceType = ResourceType.MeteringPointContract,
                constraints = frameworkDocument.requestedScopes.single().constraints,
            ),
        )
        verify(exactly = 1) { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) }
        coVerify(exactly = 1) { partyService.resolve(identifier) }
        coVerify(exactly = 1) { repository.confirm(id, signedFile, requestedOf, any()) }
    }

    test("rejects a framework agreement with an expired requested grant end without confirming") {
        coEvery { repository.find(id) } returns frameworkDocument.copy(
            requestedGrantValidFrom = null,
            requestedGrantValidTo = Instant.parse("2000-01-01T00:00:00Z"),
        )
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } returns PdfSignatory(nin)
        coEvery { partyService.resolve(identifier) } returns requestedOf.right()

        shouldThrow<ConfirmDocumentError.InvalidGrantValidity> { handler(id, requestedBy, signedFile) }

        coVerify(exactly = 0) { repository.confirm(any(), any(), any(), any()) }
    }

    test("propagates a repository confirmation failure") {
        every { validator.validateSignaturesAndReturnSignatory(signedFile, originalFile) } returns PdfSignatory(nin)
        coEvery { partyService.resolve(identifier) } returns requestedOf.right()
        val error = RepositoryWriteError.ConflictError("Already confirmed")
        coEvery { repository.confirm(id, signedFile, requestedOf, any()) } throws error

        shouldThrow<RepositoryWriteError.ConflictError> { handler(id, requestedBy, signedFile) } shouldBeSameInstanceAs error
        coVerify(exactly = 1) { repository.confirm(id, signedFile, requestedOf, any()) }
    }
})
