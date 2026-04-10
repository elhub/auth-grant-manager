package no.elhub.auth.features.documents.common

import arrow.core.getOrElse
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.config.withTransaction
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.grants.common.AuthorizationGrantPropertyTable
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.grants.common.ExposedGrantPropertiesRepository
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class ExposedDocumentRepositoryTest :
    FunSpec({
        extensions(PostgresTestContainerExtension())
        val transactionContext = TransactionContext(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
        val partyRepository = ExposedPartyRepository()
        val propertiesRepository = ExposedDocumentPropertiesRepository()
        val grantPropertiesRepository = ExposedGrantPropertiesRepository(transactionContext)
        val grantRepository = ExposedGrantRepository(partyRepository, grantPropertiesRepository, transactionContext)
        val repository =
            ExposedDocumentRepository(
                partyRepository,
                grantRepository,
                propertiesRepository,
                grantPropertiesRepository,
                transactionContext,
            )

        beforeSpec {
            Database.connect(
                url = PostgresTestContainer.JDBC_URL,
                driver = PostgresTestContainer.DRIVER,
                user = PostgresTestContainer.USERNAME,
                password = PostgresTestContainer.PASSWORD,
            )
        }

        context("Find") {
            test("Should return not found error when no data exists") {
                val result = repository.find(UUID.randomUUID())
                result shouldBeLeft RepositoryReadError.NotFoundError
            }
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
                        validTo = currentTimeUtc().plusDays(1),
                        createdAt = currentTimeUtc(),
                        updatedAt = currentTimeUtc()
                    )

                val scopes = listOf(
                    CreateScopeData(
                        authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                        authorizedResourceId = "1234",
                        permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson

                    )
                )

                // When
                repository.insert(document, scopes)

                // Then
                val documentExists = repository.find(document.id)
                documentExists shouldNotBe null

                withTransaction {
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
                    validTo = currentTimeUtc().plusDays(1),
                    createdAt = currentTimeUtc(),
                    updatedAt = currentTimeUtc()
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
                    createdAt = currentTimeUtc(),
                    properties = emptyList(),
                    validTo = currentTimeUtc().plusDays(1),
                    updatedAt = currentTimeUtc()
                )

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

        context("confirmWithGrant") {
            test("atomically confirms document and creates grant with properties") {
                val requestedBy = AuthorizationParty(type = PartyType.Person, id = "conf-req-by")
                val requestedFrom = AuthorizationParty(type = PartyType.Person, id = "conf-req-from")
                val requestedTo = AuthorizationParty(type = PartyType.Person, id = "conf-req-to")
                val signatory = requestedTo

                val document = AuthorizationDocument(
                    id = UUID.randomUUID(),
                    file = "original".toByteArray(),
                    type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
                    status = AuthorizationDocument.Status.Pending,
                    requestedBy = requestedBy,
                    requestedFrom = requestedFrom,
                    requestedTo = requestedTo,
                    signedBy = null,
                    properties = emptyList(),
                    validTo = currentTimeUtc().plusDays(30),
                    createdAt = currentTimeUtc(),
                    updatedAt = currentTimeUtc()
                )
                repository.insert(document, listOf())

                val grant = AuthorizationGrant.create(
                    grantedFor = requestedFrom,
                    grantedBy = signatory,
                    grantedTo = requestedBy,
                    sourceType = AuthorizationGrant.SourceType.Document,
                    sourceId = document.id,
                    scopeIds = emptyList(),
                    validFrom = currentTimeUtc(),
                    validTo = currentTimeUtc().plusDays(365)
                )
                val grantProperties = listOf(
                    AuthorizationGrantProperty(grantId = grant.id, key = "meta-key", value = "meta-value")
                )

                val result = repository.confirmWithGrant(
                    documentId = document.id,
                    signedFile = "signed".toByteArray(),
                    requestedFrom = requestedFrom,
                    signatory = signatory,
                    grant = grant,
                    grantProperties = grantProperties,
                )

                result.shouldBeRight()

                val confirmedDoc = repository.find(document.id).shouldBeRight()
                confirmedDoc.signedBy shouldBe signatory

                val createdGrant = grantRepository.find(grant.id).shouldBeRight()
                createdGrant.sourceId shouldBe document.id
                createdGrant.sourceType shouldBe AuthorizationGrant.SourceType.Document

                withTransaction {
                    val storedProperties = AuthorizationGrantPropertyTable
                        .selectAll()
                        .where { AuthorizationGrantPropertyTable.grantId eq grant.id }
                        .map { it[AuthorizationGrantPropertyTable.key] to it[AuthorizationGrantPropertyTable.value] }
                    storedProperties shouldContainExactlyInAnyOrder listOf("meta-key" to "meta-value")
                }
            }
        }
    })
