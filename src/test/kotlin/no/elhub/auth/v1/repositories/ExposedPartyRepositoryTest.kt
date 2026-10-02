package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
import no.elhub.auth.v0.features.common.party.PartyType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class ExposedPartyRepositoryTest : FunSpec({
    extension(PostgresTestContainerExtension())
    val repository: PartyRepository = ExposedPartyRepository()

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    test("find reads an existing party from the shared v0 table") {
        val id = withTransaction {
            AuthorizationPartyTable.insert {
                it[type] = PartyType.Person
                it[partyId] = "EXISTING_V0_PARTY"
            }[AuthorizationPartyTable.id].value
        }

        val found = repository.find(id)
        found.id shouldBe id
        found.type shouldBe PartyType.Person
        found.resourceId shouldBe "EXISTING_V0_PARTY"
        repository.findOrInsert(PartyType.Person, "EXISTING_V0_PARTY") shouldBe found
    }

    test("find missing UUID throws a typed exception") {
        val id = UUID.randomUUID()
        val exception = shouldThrow<PartyNotFoundException> { repository.find(id) }
        exception.id shouldBe id
        exception.message shouldBe "Party not found: $id"
    }

    test("findOrInsert persists a new party and returns the same record on repeated calls") {
        val first = repository.findOrInsert(PartyType.Person, "NEW_PARTY")

        first.type shouldBe PartyType.Person
        first.resourceId shouldBe "NEW_PARTY"
        repository.find(first.id) shouldBe first
        repository.findOrInsert(PartyType.Person, "NEW_PARTY") shouldBe first

        withTransaction {
            AuthorizationPartyTable.selectAll()
                .where { AuthorizationPartyTable.partyId eq "NEW_PARTY" }
                .count() shouldBe 1
        }
    }

    test("same identifier with different types identifies different parties") {
        val person = repository.findOrInsert(PartyType.Person, "SHARED_IDENTIFIER")
        val organization = repository.findOrInsert(PartyType.Organization, "SHARED_IDENTIFIER")

        person.id shouldNotBe organization.id
        repository.findOrInsert(PartyType.Person, "SHARED_IDENTIFIER") shouldBe person
        repository.findOrInsert(PartyType.Organization, "SHARED_IDENTIFIER") shouldBe organization
    }

    test("different identifiers with the same type identify different parties") {
        val first = repository.findOrInsert(PartyType.Person, "PARTY_A")
        val second = repository.findOrInsert(PartyType.Person, "PARTY_B")
        first.id shouldNotBe second.id
    }

    test("concurrent findOrInsert calls all return the same persisted party") {
        val parties = coroutineScope {
            List(10) {
                async { repository.findOrInsert(PartyType.Person, "CONCURRENT_PARTY") }
            }.awaitAll()
        }

        parties.forEach { it shouldBe parties.first() }
        repository.find(parties.first().id) shouldBe parties.first()
        withTransaction {
            AuthorizationPartyTable.selectAll()
                .where {
                    (AuthorizationPartyTable.type eq PartyType.Person) and
                        (AuthorizationPartyTable.partyId eq "CONCURRENT_PARTY")
                }
                .count() shouldBe 1
        }
    }
})
