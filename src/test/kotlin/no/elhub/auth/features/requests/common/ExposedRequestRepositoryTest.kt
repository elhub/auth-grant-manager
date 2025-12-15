package no.elhub.auth.features.requests.common

import arrow.core.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.requests.AuthorizationRequest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ExposedRequestRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension())
    val partyRepo = ExposedPartyRepository()
    val requestRepo = ExposedRequestRepository(partyRepo)

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )

        transaction {
            SchemaUtils.create(AuthorizationPartyTable)
            SchemaUtils.create(AuthorizationRequestTable)
            SchemaUtils.create(AuthorizationScopeTable)
        }
    }

    afterTest {
        transaction {
            AuthorizationRequestTable.deleteAll()
            AuthorizationScopeTable.deleteAll()
            AuthorizationPartyTable.deleteAll()
        }
    }

    xtest("findAll returns all requests") {
        // / TODO implement
    }

    xtest("find returns correct request") {
        // / TODO implement
    }

    xtest("findScopeIds returns correct scope list") {
        // / TODO implement
    }

    xtest("confirm authorization request with properties") {
        // / TODO implement
    }

    test("confirm authorization request without properties") {
        val requestToConfirm = generateRequestWithoutProperties()

        transaction {
            val insertedRequest = requestRepo.insert(requestToConfirm)
                .getOrElse { error ->
                    fail("Inserted failed :$error")
                }

            insertedRequest.status shouldBe AuthorizationRequest.Status.Pending

            val updatedRequest = requestRepo.update(
                requestId = insertedRequest.id,
                newStatus = AuthorizationRequest.Status.Accepted,
                approvedBy = insertedRequest.requestedTo
            )

            updatedRequest.fold(
                ifLeft = { error ->
                    fail("Confirm failed: $error")
                },
                ifRight = { confirmedRequest ->
                    confirmedRequest.status shouldBe AuthorizationRequest.Status.Accepted
                }
            )
        }
    }
})

@OptIn(ExperimentalTime::class)
private fun generateRequestWithoutProperties(): AuthorizationRequest = AuthorizationRequest.create(
    type = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
    requestedBy = AuthorizationParty(type = PartyType.Person, resourceId = "12345"),
    requestedFrom = AuthorizationParty(type = PartyType.Person, resourceId = "56789"),
    requestedTo = AuthorizationParty(type = PartyType.Person, resourceId = "45567"),
    // validTo is set by the value stream team in production,
    // but we set it here for testing purposes
    validTo = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.plus(DatePeriod(days = 30)),
    properties = emptyMap(),
)
