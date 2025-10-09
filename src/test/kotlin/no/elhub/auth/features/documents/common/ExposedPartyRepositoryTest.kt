package no.elhub.auth.features.documents.common

import arrow.core.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedPartyRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension)
    val repository = ExposedPartyRepository()

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    beforeTest {
        transaction { AuthorizationPartyTable.deleteAll() }
    }

    test("findOrCreate returns new row when called twice with different resourceIds") {
        val firstResourceId = "4066e7d6-18e2-68f1-e063-34778d0a4876"
        val secondResourceId = "6920e7d6-20e2-68d1-s163-75283d1w4888"

        val first = repository.findOrCreate(ElhubResource.Person, firstResourceId)
            .getOrElse { error("First call failed: $it") }

        val second = repository.findOrCreate(ElhubResource.Person, secondResourceId)
            .getOrElse { error("Second call failed: $it") }

        second.id shouldNotBe first.id
        second.resourceId shouldNotBe first.resourceId

        val count = transaction {
            AuthorizationPartyTable.selectAll().count()
        }

        count shouldBe 2
    }

    test("findOrCreate returns the same row when called twice with the same resourceId") {
        val resourceId = "4066e7d6-18e2-68f1-e063-34778d0a4876"

        val first = repository.findOrCreate(ElhubResource.Person, resourceId)
            .getOrElse { error("First call failed: $it") }

        val second = repository.findOrCreate(ElhubResource.Person, resourceId)
            .getOrElse { error("Second call failed: $it") }

        second.id shouldBe first.id
        second.type shouldBe first.type
        second.resourceId shouldBe first.resourceId

        val count = transaction {
            AuthorizationPartyTable.selectAll().count()
        }

        count shouldBe 1
    }
})
