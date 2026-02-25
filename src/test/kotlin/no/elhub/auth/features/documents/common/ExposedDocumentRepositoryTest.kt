package no.elhub.auth.features.documents.common

import arrow.core.getOrElse
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class ExposedDocumentRepositoryTest :
    FunSpec({
        extensions(PostgresTestContainerExtension())
        val partyRepository = ExposedPartyRepository()
        val propertiesRepository = ExposedDocumentPropertiesRepository()
        val repository = ExposedDocumentRepository(partyRepository, propertiesRepository)

        beforeSpec {
            Database.connect(
                url = PostgresTestContainer.JDBC_URL,
                driver = PostgresTestContainer.DRIVER,
                user = PostgresTestContainer.USERNAME,
                password = PostgresTestContainer.PASSWORD,
            )
        }

        context("Insert Document") {
            test("Should insert a document and its scopes with correct references") {
                // Given
                val document =
                    AuthorizationDocument(
                        id = UUID.randomUUID(),
                        file = byteArrayOf(),
                        type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
                        status = AuthorizationDocument.Status.Pending,
                        requestedBy = AuthorizationParty(type = PartyType.Person, id = "1234567890"),
                        requestedFrom = AuthorizationParty(type = PartyType.Person, id = "1234567890"),
                        requestedTo = AuthorizationParty(type = PartyType.Person, id = "1234567890"),
                        signedBy = AuthorizationParty(type = PartyType.Person, id = "1234567890"),
                        properties = emptyList(),
                        validTo = currentTimeWithTimeZone().plusDays(1),
                        createdAt = currentTimeWithTimeZone(),
                        updatedAt = currentTimeWithTimeZone()
                    )

                val scopes = listOf(
                    CreateScopeData(
                        authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                        authorizedResourceId = "1234",
                        permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson

                    )
                )

                transaction {
                    // When
                    repository.insert(document, scopes)

                    // Then
                    val documentExists = repository.find(document.id)
                    documentExists shouldNotBe null

                    val authorizationDocumentScopeRow =
                        AuthorizationDocumentScopeTable
                            .selectAll()
                            .where { AuthorizationDocumentScopeTable.authorizationDocumentId eq document.id }
                            .map { it }
                            .singleOrNull()
                    authorizationDocumentScopeRow.shouldNotBeNull()

                    val authorizationScopeRow =
                        AuthorizationScopeTable
                            .selectAll()
                            .where { AuthorizationScopeTable.id eq (authorizationDocumentScopeRow[AuthorizationDocumentScopeTable.authorizationScopeId]) }
                            .singleOrNull()
                    authorizationScopeRow.shouldNotBeNull()
                    authorizationScopeRow[AuthorizationScopeTable.authorizedResourceId] shouldBe "1234"
                    authorizationScopeRow[AuthorizationScopeTable.authorizedResourceType] shouldBe AuthorizationScope.AuthorizationResource.MeteringPoint
                    authorizationScopeRow[AuthorizationScopeTable.permissionType] shouldBe AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson
                }
            }
        }

        context("Find all") {
            test("should return only documents requested by the given party") {
                val matchingRequestedBy =
                    AuthorizationParty(type = PartyType.Organization, id = "matching-party")
                val otherRequestedBy = AuthorizationParty(type = PartyType.Organization, id = "other-party")

                val matchingDocument = AuthorizationDocument(
                    id = UUID.randomUUID(),
                    file = byteArrayOf(),
                    type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
                    status = AuthorizationDocument.Status.Pending,
                    requestedBy = matchingRequestedBy,
                    requestedFrom = AuthorizationParty(type = PartyType.Person, id = "from-1"),
                    requestedTo = AuthorizationParty(type = PartyType.Person, id = "to-1"),
                    signedBy = AuthorizationParty(type = PartyType.Person, id = "signer-1"),
                    properties = emptyList(),
                    validTo = currentTimeWithTimeZone().plusDays(1),
                    createdAt = currentTimeWithTimeZone(),
                    updatedAt = currentTimeWithTimeZone()
                )

                val otherDocument = AuthorizationDocument(
                    id = UUID.randomUUID(),
                    file = byteArrayOf(),
                    type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
                    status = AuthorizationDocument.Status.Pending,
                    requestedBy = otherRequestedBy,
                    requestedFrom = AuthorizationParty(type = PartyType.Person, id = "from-2"),
                    requestedTo = AuthorizationParty(type = PartyType.Person, id = "to-2"),
                    signedBy = AuthorizationParty(type = PartyType.Person, id = "signer-2"),
                    createdAt = currentTimeWithTimeZone(),
                    properties = emptyList(),
                    validTo = currentTimeWithTimeZone().plusDays(1),
                    updatedAt = currentTimeWithTimeZone()
                )

                transaction {
                    repository.insert(matchingDocument, listOf())
                    repository.insert(otherDocument, listOf())

                    val documents =
                        repository.findAll(AuthorizationParty(matchingRequestedBy.id, PartyType.Organization))
                            .getOrElse { error -> fail("Failed to fetch documents: $error") }
                    val documentIds = documents.map { it.id }

                    documentIds.shouldHaveSize(1)
                    documentIds shouldContain matchingDocument.id
                    documentIds shouldNotContain otherDocument.id
                }
            }
        }
    })
