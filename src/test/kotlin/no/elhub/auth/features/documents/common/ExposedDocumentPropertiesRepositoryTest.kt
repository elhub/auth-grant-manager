package no.elhub.auth.features.documents.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.grants.common.ExposedGrantPropertiesRepository
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class ExposedDocumentPropertiesRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension())
    val transactionContext = TransactionContext(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
    val repository = ExposedDocumentPropertiesRepository(transactionContext)
    val partyRepo = ExposedPartyRepository(transactionContext)
    val grantPropertiesRepository = ExposedGrantPropertiesRepository(transactionContext)
    val grantRepository = ExposedGrantRepository(partyRepo, grantPropertiesRepository, transactionContext)
    val documentRepository = ExposedDocumentRepository(
        partyRepo = partyRepo,
        grantRepo = grantRepository,
        documentPropertiesRepository = repository,
        grantPropertiesRepository = grantPropertiesRepository,
        transactionContext
    )

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    beforeTest {
        transactionContext.withTransaction {
            AuthorizationDocumentPropertyTable.deleteAll()
        }
    }

    val documentId = UUID.randomUUID()
    context("Document properties repository") {
        test("insert empty list should not create rows") {
            val properties = emptyList<AuthorizationDocumentProperty>()
            repository.insert(properties, documentId)
            transactionContext.withTransaction {
                AuthorizationDocumentPropertyTable.selectAll().count().shouldBe(0)
            }
        }

        test("insert persists all provided properties and find returns them") {
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

            documentRepository.insert(document, listOf())

            val properties = listOf(
                AuthorizationDocumentProperty("requestedFromName", "Ola Normann"),
                AuthorizationDocumentProperty("meteringPointId", "1234")
            )

            repository.insert(properties, document.id)
            repository.find(document.id) shouldContainExactlyInAnyOrder properties
        }

        test("find returns empty list when no properties exist for document") {
            repository.find(UUID.randomUUID()).shouldBeEmpty()
        }
    }
})
