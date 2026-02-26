package no.elhub.auth.features.grants.common

import arrow.core.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.today
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.AuthorizationGrant
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class ExposedGrantRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension())
    val partyRepo = ExposedPartyRepository()
    val grantPropertiesRepo = ExposedGrantPropertiesRepository()
    val grantRepo = ExposedGrantRepository(partyRepo, grantPropertiesRepo)
    val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())

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
                grantedBy = AuthorizationParty(type = PartyType.Person, id = "12345"),
                grantedFor = AuthorizationParty(type = PartyType.Person, id = "56789"),
                grantedTo = AuthorizationParty(type = PartyType.Person, id = "45567"),
                sourceType = AuthorizationGrant.SourceType.Request,
                sourceId = UUID.randomUUID(),
                scopeIds = scopeIds,
                validFrom = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
                validTo = today().plus(DatePeriod(years = 1)).toTimeZoneOffsetDateTimeAtStartOfDay()
            )

            grantRepo.insert(grant, emptyList()).getOrElse { error((it)) }

            // Should only have 1 grant in database
            AuthorizationGrantTable.selectAll().count() shouldBe 1
        }
    }

    test("update grant status") {
        transaction {
            // insert a grant
            val grant = AuthorizationGrant.create(
                grantedBy = AuthorizationParty(type = PartyType.Person, id = "12345"),
                grantedFor = AuthorizationParty(type = PartyType.Person, id = "56789"),
                grantedTo = AuthorizationParty(type = PartyType.Person, id = "45567"),
                sourceType = AuthorizationGrant.SourceType.Request,
                sourceId = UUID.randomUUID(),
                scopeIds = scopeIds,
                validFrom = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
                validTo = today().plus(DatePeriod(years = 1)).toTimeZoneOffsetDateTimeAtStartOfDay()
            )

            grantRepo.insert(grant, emptyList()).getOrElse { error((it)) }

            // update the grant
            val updated = grantRepo.update(grant.id, AuthorizationGrant.Status.Revoked).getOrElse { error(it) }

            updated.grantStatus shouldBe AuthorizationGrant.Status.Revoked
        }
    }

    /*
    TODO write these tests
    - insert when scope list is not empty
    - findAll returns all grants for a specific sourceId
    - findById should return a specific scope
     */
})
