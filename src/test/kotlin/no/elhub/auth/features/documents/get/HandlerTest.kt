package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

class HandlerTest : FunSpec({

    val requestedBy = AuthorizationParty(id = "org-entity-1", type = PartyType.OrganizationEntity)
    val requestedFrom = AuthorizationParty(id = "person-1", type = PartyType.Person)
    val requestedTo = AuthorizationParty(id = "person-2", type = PartyType.Person)
    val validTo = currentTimeWithTimeZone().plusDays(1)
    val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())

    val documentId = UUID.randomUUID()
    val document = AuthorizationDocument(
        id = documentId,
        type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
        status = AuthorizationDocument.Status.Pending,
        file = "file".toByteArray(),
        requestedBy = requestedBy,
        requestedFrom = requestedFrom,
        requestedTo = requestedTo,
        properties = listOf(AuthorizationDocumentProperty(key = "k", value = "v")),
        validTo = validTo,
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone()
    )

    val grant = AuthorizationGrant.create(
        grantedFor = requestedFrom,
        grantedBy = requestedBy,
        grantedTo = requestedTo,
        sourceType = AuthorizationGrant.SourceType.Document,
        sourceId = documentId,
        scopeIds = scopeIds
    )

    fun documentRepoReturning(result: Either<RepositoryReadError, AuthorizationDocument>): DocumentRepository =
        mockk<DocumentRepository> {
            every { find(documentId) } returns result
        }

    fun grantRepoReturning(result: Either<RepositoryReadError, AuthorizationGrant?>): GrantRepository =
        mockk<GrantRepository> {
            every { findBySource(AuthorizationGrant.SourceType.Document, documentId) } returns result
        }

    test("returns document with grant id when authorized party matches requestedBy") {
        val handler = Handler(
            documentRepoReturning(document.right()),
            grantRepoReturning(grant.right())
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedBy
            )
        )

        response.shouldBeRight(document.copy(grantId = grant.id))
    }

    test("returns document with grant id when authorized party matches requestedFrom") {
        val handler = Handler(
            documentRepoReturning(document.right()),
            grantRepoReturning(grant.right())
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedFrom
            )
        )

        response.shouldBeRight(document.copy(grantId = grant.id))
    }

    test("returns NotAuthorized when authorized party matches requestedTo") {
        val handler = Handler(
            documentRepoReturning(document.right()),
            grantRepoReturning(grant.right())
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedTo
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("returns document with null grant id when grant is missing") {
        val noGrant: Either<RepositoryReadError, AuthorizationGrant?> = null.right()
        val handler = Handler(
            documentRepoReturning(document.right()),
            grantRepoReturning(noGrant)
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedFrom
            )
        )

        response.shouldBeRight(document.copy(grantId = null))
    }

    test("maps document repository not found to QueryError.ResourceNotFoundError") {
        val handler = Handler(
            documentRepoReturning(RepositoryReadError.NotFoundError.left()),
            grantRepoReturning(grant.right())
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedBy
            )
        )

        response.shouldBeLeft(QueryError.ResourceNotFoundError)
    }

    test("maps document repository unexpected error to QueryError.IOError") {
        val handler = Handler(
            documentRepoReturning(RepositoryReadError.UnexpectedError.left()),
            grantRepoReturning(grant.right())
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedFrom
            )
        )

        response.shouldBeLeft(QueryError.IOError)
    }

    test("maps grant repository not found to QueryError.ResourceNotFoundError") {
        val handler = Handler(
            documentRepoReturning(document.right()),
            grantRepoReturning(RepositoryReadError.NotFoundError.left())
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedBy
            )
        )

        response.shouldBeLeft(QueryError.ResourceNotFoundError)
    }

    test("maps grant repository unexpected error to QueryError.IOError") {
        val handler = Handler(
            documentRepoReturning(document.right()),
            grantRepoReturning(RepositoryReadError.UnexpectedError.left())
        )

        val response = handler(
            Query(
                documentId = documentId,
                authorizedParty = requestedFrom
            )
        )

        response.shouldBeLeft(QueryError.IOError)
    }
})
