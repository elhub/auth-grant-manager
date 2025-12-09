package no.elhub.auth.features.documents.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class ExposedDocumentPropertiesRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension())

    val repository = ExposedDocumentPropertiesRepository()
    val documentRepository = ExposedDocumentRepository(partyRepo = ExposedPartyRepository(), documentPropertiesRepository = repository)

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    beforeTest {
        transaction {
            AuthorizationDocumentPropertyTable.deleteAll()
        }
    }

    val documentId = UUID.randomUUID()
    context("Document properties repository") {
        test("insert empty list should not create rows") {
            val properties = emptyList<AuthorizationDocumentProperty>()
            transaction {
                repository.insert(properties, documentId)
                AuthorizationDocumentPropertyTable.selectAll().count().shouldBe(0)
            }
        }

        test("insert persists all provided properties and find returns them") {
            val document =
                AuthorizationDocument(
                    id = UUID.randomUUID(),
                    title = "Title",
                    file = byteArrayOf(),
                    type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                    status = AuthorizationDocument.Status.Pending,
                    requestedBy = AuthorizationParty(type = PartyType.Person, resourceId = "1234567890"),
                    requestedFrom = AuthorizationParty(type = PartyType.Person, resourceId = "1234567890"),
                    requestedTo = AuthorizationParty(type = PartyType.Person, resourceId = "1234567890"),
                    signedBy = AuthorizationParty(type = PartyType.Person, resourceId = "1234567890"),
                    properties = emptyList(),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )

            documentRepository.insert(document)

            val properties = listOf(
                AuthorizationDocumentProperty("requestedFromName", "Ola Normann"),
                AuthorizationDocumentProperty("meteringPointId", "1234")
            )

            transaction {
                repository.insert(properties, document.id)
                repository.find(document.id) shouldContainExactlyInAnyOrder properties
            }
        }

        test("find returns empty list when no properties exist for document") {
            val documentId = UUID.randomUUID()
            transaction {
                repository.find(documentId).shouldBeEmpty()
            }
        }
    }
})
