package no.elhub.auth.features.grants.common

import arrow.core.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ExposedGrantRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension())
    val partyRepo = ExposedPartyRepository()
    val grantRepo = ExposedGrantRepository(partyRepo)

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )

        transaction {
            SchemaUtils.create(AuthorizationPartyTable)
            SchemaUtils.create(AuthorizationGrantTable)
            SchemaUtils.create(AuthorizationScopeTable)
        }
    }

    afterTest {
        transaction {
            AuthorizationGrantTable.deleteAll()
            AuthorizationScopeTable.deleteAll()
            AuthorizationPartyTable.deleteAll()
        }
    }

    test("insert without scopes") {
        transaction {
            val grant = AuthorizationGrant.create(
                grantedBy = AuthorizationParty(type = PartyType.Person, resourceId = "12345"),
                grantedFor = AuthorizationParty(type = PartyType.Person, resourceId = "56789"),
                grantedTo = AuthorizationParty(type = PartyType.Person, resourceId = "45567"),
                sourceType = AuthorizationGrant.SourceType.Request,
                sourceId = UUID.randomUUID(),
            )

            grantRepo.insert(grant, emptyList()).getOrElse { error((it)) }

            // Should only have 1 grant in database
            AuthorizationGrantTable.selectAll().count() shouldBe 1
        }
    }

    /*
    TODO write these tests
    - insert when scope list is not empty
    - findAll returns all grants for a specific sourceId
    - findById should return a specific scope
    - updateStatus changes grant status
     */
})
