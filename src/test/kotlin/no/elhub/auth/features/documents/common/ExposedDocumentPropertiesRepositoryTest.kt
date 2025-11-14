package no.elhub.auth.features.documents.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ExposedDocumentPropertiesRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension)

    val repository = ExposedDocumentPropertiesRepository()

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

    context("Document properties repository") {
        test("insert empty list should not create rows") {
            val properties = emptyList<AuthorizationDocumentProperty>()
            repository.insert(properties)
            transaction {
                AuthorizationDocumentPropertyTable.selectAll().count().shouldBe(0)
            }
        }

        test("insert persists all provided properties and find returns them") {
            val documentId = UUID.randomUUID()
            val properties = listOf(
                AuthorizationDocumentProperty(documentId, "requestedFromName", "Ola Normann"),
                AuthorizationDocumentProperty(documentId, "meteringPointId", "1234")
            )

            repository.insert(properties)

            repository.find(documentId) shouldContainExactlyInAnyOrder properties
        }

        test("find returns empty list when no properties exist for document") {
            val documentId = UUID.randomUUID()
            repository.find(documentId).shouldBeEmpty()
        }
    }
})
