package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.elhub.auth.common.config.withTransaction
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v1.common.auth.AgentType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable as AuthorizationAgentTable

class ExposedAgentRepositoryTest : FunSpec({
    extension(PostgresTestContainerExtension())
    val repository: AgentRepository = ExposedAgentRepository()

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    test("find reads an existing agent from the shared v0 table") {
        val id = withTransaction {
            AuthorizationAgentTable.insert {
                it[type] = AgentType.Person
                it[partyId] = "EXISTING_V0_AGENT"
            }[AuthorizationAgentTable.id].value
        }

        val found = repository.find(id)
        found.id shouldBe id
        found.type shouldBe AgentType.Person
        found.resourceId shouldBe "EXISTING_V0_AGENT"
        repository.findOrInsert(AgentType.Person, "EXISTING_V0_AGENT") shouldBe found
    }

    test("find missing UUID throws a typed exception") {
        val id = UUID.randomUUID()
        val exception = shouldThrow<RepositoryReadError.NotFoundError> { repository.find(id) }
        exception.message shouldBe "Agent not found: $id"
    }

    test("findByIds returns requested agents keyed by ID and omits missing IDs") {
        val first = repository.findOrInsert(AgentType.Person, "BATCH_PERSON")
        val second = repository.findOrInsert(AgentType.Organization, "BATCH_ORGANIZATION")
        repository.findOrInsert(AgentType.Person, "BATCH_UNRELATED")

        repository.findByIds(listOf(first.id, second.id, first.id, UUID.randomUUID())) shouldBe
            mapOf(first.id to first, second.id to second)
    }

    test("findByIds returns an empty map for empty input or missing IDs") {
        repository.findByIds(emptyList()) shouldBe emptyMap()
        repository.findByIds(listOf(UUID.randomUUID())) shouldBe emptyMap()
    }

    test("findOrInsert persists a new agent and returns the same record on repeated calls") {
        val first = repository.findOrInsert(AgentType.Person, "NEW_AGENT")

        first.type shouldBe AgentType.Person
        first.resourceId shouldBe "NEW_AGENT"
        repository.find(first.id) shouldBe first
        repository.findOrInsert(AgentType.Person, "NEW_AGENT") shouldBe first

        withTransaction {
            AuthorizationAgentTable.selectAll()
                .where { AuthorizationAgentTable.partyId eq "NEW_AGENT" }
                .count() shouldBe 1
        }
    }

    test("same identifier with different types identifies different agents") {
        val person = repository.findOrInsert(AgentType.Person, "SHARED_IDENTIFIER")
        val organization = repository.findOrInsert(AgentType.Organization, "SHARED_IDENTIFIER")

        person.id shouldNotBe organization.id
        repository.findOrInsert(AgentType.Person, "SHARED_IDENTIFIER") shouldBe person
        repository.findOrInsert(AgentType.Organization, "SHARED_IDENTIFIER") shouldBe organization
    }

    test("different identifiers with the same type identify different agents") {
        val first = repository.findOrInsert(AgentType.Person, "AGENT_A")
        val second = repository.findOrInsert(AgentType.Person, "AGENT_B")
        first.id shouldNotBe second.id
    }

    test("concurrent findOrInsert calls all return the same persisted agent") {
        val agents = coroutineScope {
            List(10) {
                async { repository.findOrInsert(AgentType.Person, "CONCURRENT_AGENT") }
            }.awaitAll()
        }

        agents.forEach { it shouldBe agents.first() }
        repository.find(agents.first().id) shouldBe agents.first()
        withTransaction {
            AuthorizationAgentTable.selectAll()
                .where {
                    (AuthorizationAgentTable.type eq AgentType.Person) and
                        (AuthorizationAgentTable.partyId eq "CONCURRENT_AGENT")
                }
                .count() shouldBe 1
        }
    }
})
