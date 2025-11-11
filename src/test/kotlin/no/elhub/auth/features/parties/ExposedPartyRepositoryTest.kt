package no.elhub.auth.features.parties

import arrow.core.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.elhub.auth.features.common.AuthorizationPartyResourceType
import no.elhub.auth.features.common.AuthorizationPartyTable
import no.elhub.auth.features.common.ExposedPartyRepository
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RepositoryReadError
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ExposedPartyRepositoryTest : FunSpec({
    extension(PostgresTestContainerExtension)
    val partyRepo = ExposedPartyRepository()

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    test("find existing by id succeeds") {
        val rid = "FIND_ME"
        val inserted = partyRepo.findOrInsert(AuthorizationPartyResourceType.Person, rid).getOrElse { error(it) }
        val found = partyRepo.find(inserted.id).getOrElse { error(it) }
        found.id shouldBe inserted.id
        found.resourceId shouldBe rid
    }

    test("Insert first then idempotent second") {
        val resourceId = "12345678901"
        val first = partyRepo.findOrInsert(AuthorizationPartyResourceType.Person, resourceId).getOrElse { error(it) }
        val second = partyRepo.findOrInsert(AuthorizationPartyResourceType.Person, resourceId).getOrElse { error(it) }

        first.id shouldBe second.id
        first.resourceId shouldBe resourceId
    }

    test("Different type same resourceId produces two rows") {
        val rid = "DUPLICATE_KEY"
        val org = partyRepo.findOrInsert(AuthorizationPartyResourceType.Organization, rid).getOrElse { error(it) }
        val person = partyRepo.findOrInsert(AuthorizationPartyResourceType.Person, rid).getOrElse { error(it) }
        org.id shouldNotBe person.id
    }

    test("find missing returns NotFoundError") {
        val randomId = UUID.randomUUID()
        val res = partyRepo.find(randomId)
        res.isLeft() shouldBe true
        res.swap().getOrNull().shouldBeInstanceOf<RepositoryReadError.NotFoundError>()
    }

    test("Concurrent same insert yields single row") {
        val rid = "RACE_TEST"
        runBlocking {
            repeat(10) {
                launch {
                    partyRepo.findOrInsert(AuthorizationPartyResourceType.Person, rid)
                }
            }
        }

        transaction {
            AuthorizationPartyTable
                .selectAll()
                .where { (AuthorizationPartyTable.type eq AuthorizationPartyResourceType.Person) and (AuthorizationPartyTable.resourceId eq rid) }
                .count() shouldBe 1
        }
    }

    test("Two different resourceIds stay separate") {
        val rid1 = "RID_A"
        val rid2 = "RID_B"
        val a = partyRepo.findOrInsert(AuthorizationPartyResourceType.Person, rid1).getOrElse { error(it) }
        val b = partyRepo.findOrInsert(AuthorizationPartyResourceType.Person, rid2).getOrElse { error(it) }
        a.id shouldNotBe b.id
    }
})
