package no.elhub.auth.features.grants.common

import arrow.core.getOrElse
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.config.withTransaction
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.common.today
import no.elhub.auth.features.grants.AuthorizationGrant
import org.apache.ibatis.io.Resources
import org.apache.ibatis.jdbc.ScriptRunner
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.sql.DriverManager
import java.util.UUID

class ExposedGrantRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension())
    val metricsProvider = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val partyRepo = ExposedPartyRepository(metricsProvider)
    val grantPropertiesRepo = ExposedGrantPropertiesRepository(metricsProvider)
    val grantRepo = ExposedGrantRepository(partyRepo, grantPropertiesRepo, metricsProvider)
    val scopeIds = listOf(
        UUID.fromString("75ad606f-4ac9-4d4f-acd5-20d6862ec198"),
        UUID.fromString("0feefd01-36c7-403b-9bf1-c11d6458f639"),
    )
    val exampleGrantWithScopeIds = AuthorizationGrant.create(
        grantedBy = AuthorizationParty(type = PartyType.Person, id = "12345"),
        grantedFor = AuthorizationParty(type = PartyType.Person, id = "56789"),
        grantedTo = AuthorizationParty(type = PartyType.Person, id = "45567"),
        sourceType = AuthorizationGrant.SourceType.Request,
        sourceId = UUID.randomUUID(),
        scopeIds = scopeIds,
        validFrom = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
        validTo = today().plus(DatePeriod(years = 1)).toTimeZoneOffsetDateTimeAtStartOfDay()
    )

    val exampleGrantWithoutScopeIds = AuthorizationGrant.create(
        grantedBy = AuthorizationParty(type = PartyType.Person, id = "666"),
        grantedFor = AuthorizationParty(type = PartyType.Person, id = "999"),
        grantedTo = AuthorizationParty(type = PartyType.Person, id = "111"),
        sourceType = AuthorizationGrant.SourceType.Request,
        sourceId = UUID.randomUUID(),
        scopeIds = emptyList(),
        validFrom = today().toTimeZoneOffsetDateTimeAtStartOfDay(),
        validTo = today().plus(DatePeriod(years = 1)).toTimeZoneOffsetDateTimeAtStartOfDay()
    )

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )

        withTransaction {
            SchemaUtils.create(AuthorizationPartyTable)
            SchemaUtils.create(AuthorizationGrantTable)
            SchemaUtils.create(AuthorizationScopeTable)
        }
    }

    afterTest {
        withTransaction {
            AuthorizationGrantTable.deleteAll()
            AuthorizationScopeTable.deleteAll()
            AuthorizationGrantScopeTable.deleteAll()
            AuthorizationPartyTable.deleteAll()
        }
    }

    test("insert without scopes") {
        grantRepo.insert(exampleGrantWithoutScopeIds).getOrElse { error((it)) }

        withTransaction {
            // Should only have 1 grant in database
            AuthorizationGrantTable.selectAll().count() shouldBe 1
        }
    }

    test("update grant status") {
        // insert a grant
        grantRepo.insert(exampleGrantWithoutScopeIds).getOrElse { error((it)) }

        // update the grant
        val updated =
            grantRepo.update(exampleGrantWithoutScopeIds.id, AuthorizationGrant.Status.Revoked).getOrElse { error(it) }

        updated.grantStatus shouldBe AuthorizationGrant.Status.Revoked
    }

    test("insert with non-empty scope list") {
        insertTestData()
        withTransaction {
            AuthorizationGrantTable.deleteAll()
        }
        grantRepo.insert(exampleGrantWithScopeIds).getOrElse { error((it)) }
        withTransaction {
            AuthorizationGrantTable.selectAll().count() shouldBe 1
        }
    }

    test("findAll returns all grants for party") {
        insertTestData()
        val partyWithGrants = AuthorizationParty(type = PartyType.OrganizationEntity, id = "0107000000021")
        val partyWithoutGrants = AuthorizationParty(type = PartyType.Person, id = "666")

        val resultForPartyWithGrants = grantRepo.findAll(partyWithGrants).getOrElse {
            fail("Failed to read grants for party with grants")
        }
        resultForPartyWithGrants.size shouldBe 5

        val resultForPartyWithoutGrants = grantRepo.findAll(partyWithoutGrants).getOrElse {
            fail("Failed to read grants for party without grants")
        }
        resultForPartyWithoutGrants.size shouldBe 0
    }

    test("findBySource returns grant given sourceType and sourceId)") {
        insertTestData()
        val grant = grantRepo.findBySource(
            sourceType = AuthorizationGrant.SourceType.Request,
            sourceId = UUID.fromString("4f71d596-99e4-415e-946d-7252c1a40c50")
        ).getOrElse {
            fail("Failed to read grants by source")
        }
        grant.shouldNotBeNull()
    }

    test("findScopes returns correct number of scopes given grantId") {
        insertTestData()
        val scopes = grantRepo.findScopes(grantId = UUID.fromString("b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a"))
            .getOrElse {
                fail("Failed to read scopes by grant id")
            }

        scopes.size shouldBe 3
    }

    test("update should return grant with new status") {
        insertTestData()
        val grant = grantRepo.update(
            grantId = UUID.fromString("456e4567-e89b-12d3-a456-426614174000"),
            newStatus = AuthorizationGrant.Status.Exhausted
        ).getOrElse {
            fail("Failed to update grant")
        }

        grant.grantStatus shouldBe AuthorizationGrant.Status.Exhausted
    }
})

private fun insertTestData() {
    DriverManager.getConnection("jdbc:postgresql://localhost:5432/auth", "app", "app").use { conn ->
        val runner = ScriptRunner(conn)
        runner.runScript(Resources.getResourceAsReader("db/insert-authorization-party.sql"))
        runner.runScript(Resources.getResourceAsReader("db/insert-authorization-grants.sql"))
        runner.runScript(Resources.getResourceAsReader("db/insert-authorization-scopes.sql"))
        runner.runScript(Resources.getResourceAsReader("db/insert-authorization-grant-scopes.sql"))
    }
}
