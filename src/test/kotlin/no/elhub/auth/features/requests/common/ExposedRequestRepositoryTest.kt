package no.elhub.auth.features.requests.common

import arrow.core.getOrElse
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.requests.AuthorizationRequest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ExposedRequestRepositoryTest : FunSpec({
    extensions(
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql")
    )
    val partyRepo = ExposedPartyRepository()
    val requestPropertiesRepo = ExposedRequestPropertiesRepository()
    val requestRepo = ExposedRequestRepository(partyRepo, requestPropertiesRepo)

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

    test("findAllBy returns all requests matching party") {
        val targetParty1 = AuthorizationParty(type = PartyType.Person, resourceId = "67652749875413695986")
        val targetParty2 = AuthorizationParty(type = PartyType.Person, resourceId = "17652749875413695986")
        val otherParty = AuthorizationParty(type = PartyType.Person, resourceId = "413695986")
        val numTargetRequests = 100
        val numOtherRequests = 50
        transaction {
            repeat(numTargetRequests) {
                val request = AuthorizationRequest.create(
                    type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    requestedBy = targetParty1,
                    requestedFrom = targetParty2,
                    requestedTo = targetParty2,
                    validTo = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.plus(DatePeriod(days = 30)),
                )
                requestRepo.insert(request)
            }

            repeat(numOtherRequests) {
                val request = AuthorizationRequest.create(
                    type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    requestedBy = otherParty,
                    requestedFrom = otherParty,
                    requestedTo = otherParty,
                    validTo = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.plus(DatePeriod(days = 30)),
                )
                requestRepo.insert(request)
            }

            val requestsOfTargetParty1 = requestRepo.findAllBy(targetParty1)
                .getOrElse { error ->
                    fail("findAllBy failed for target party 1")
                }
            requestsOfTargetParty1.size shouldBe numTargetRequests

            val requestsOfTargetParty2 = requestRepo.findAllBy(targetParty2)
                .getOrElse { error ->
                    fail("findAllBy failed for target party 2")
                }
            requestsOfTargetParty1.size shouldBe numTargetRequests
        }
    }

    test("find returns correct request") {
        val requests = List(10) {
            generateRequestWithoutProperties()
        }
        val targetId = requests[0].id
        val targetRequest = transaction {
            requests.forEach { requestRepo.insert(it) }
            requestRepo.find(targetId)
        }.getOrElse { error ->
            fail("find failed")
        }

        targetRequest.id shouldBe targetId
    }

    test("confirm authorization request with properties") {
        val request = generateRequestWithoutProperties()
        val acceptedRequest = transaction {
            val savedRequest = requestRepo
                .insert(request)
                .getOrElse {
                    fail("insert failed")
                }

            val properties = listOf(
                AuthorizationRequestProperty(savedRequest.id, "key1", "value1"),
                AuthorizationRequestProperty(savedRequest.id, "key2", "value2"),
                AuthorizationRequestProperty(savedRequest.id, "key3", "value3"),
            )

            requestPropertiesRepo.insert(properties)

            requestRepo.acceptRequest(savedRequest.id, AuthorizationParty("resourceId1", PartyType.Organization))
                .getOrElse {
                    fail("acceptRequest failed")
                }
        }
        acceptedRequest.properties.size shouldBe 3
        acceptedRequest.status shouldBe AuthorizationRequest.Status.Accepted
    }

    test("findScopeIds returns correct scope list") {
        val requestId = UUID.fromString("3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47")
        val scopeIds = transaction {
            requestRepo.findScopeIds(requestId)
        }
        scopeIds.shouldBeRight()
        scopeIds.value.size shouldBe 2
        scopeIds.value.shouldContainAll(
            listOf(UUID.fromString("e705af95-571d-47ea-9b1b-742aa598c85c"), UUID.fromString("c597482d-b013-400b-9362-35bb16724c8f"))
        )
    }

    test("reject authorization request without properties") {
        val request = generateRequestWithoutProperties()
        val rejectedRequest = transaction {
            val savedRequest = requestRepo
                .insert(request)
                .getOrElse {
                    fail("insert failed")
                }
            requestRepo.rejectAccept(savedRequest.id)
                .getOrElse {
                    fail("reject failed")
                }
        }
        rejectedRequest.properties.size shouldBe 0
        rejectedRequest.status shouldBe AuthorizationRequest.Status.Rejected
    }

    test("reject authorization request with properties") {
        val request = generateRequestWithoutProperties()

        val rejectedRequest = transaction {
            val savedRequest = requestRepo
                .insert(request)
                .getOrElse {
                    fail("insert failed")
                }

            val properties = listOf(
                AuthorizationRequestProperty(savedRequest.id, "key1", "value1"),
                AuthorizationRequestProperty(savedRequest.id, "key2", "value2"),
                AuthorizationRequestProperty(savedRequest.id, "key3", "value3"),
            )

            requestPropertiesRepo.insert(properties)

            requestRepo.acceptRequest(savedRequest.id, AuthorizationParty("resourceId1", PartyType.Organization))
                .getOrElse {
                    fail("acceptRequest failed")
                }
        }
        rejectedRequest.properties.size shouldBe 3
        rejectedRequest.status shouldBe AuthorizationRequest.Status.Accepted
    }

    test("confirm authorization request without properties") {
        val requestToConfirm = generateRequestWithoutProperties()

        transaction {
            val insertedRequest = requestRepo.insert(requestToConfirm)
                .getOrElse { error ->
                    fail("Inserted failed :$error")
                }

            insertedRequest.status shouldBe AuthorizationRequest.Status.Pending

            val updatedRequest = requestRepo.acceptRequest(
                requestId = insertedRequest.id,
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
    type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
    requestedBy = AuthorizationParty(type = PartyType.Person, resourceId = "12345"),
    requestedFrom = AuthorizationParty(type = PartyType.Person, resourceId = "56789"),
    requestedTo = AuthorizationParty(type = PartyType.Person, resourceId = "45567"),
    // validTo is set by the value stream team in production,
    // but we set it here for testing purposes
    validTo = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.plus(DatePeriod(days = 30)),
)
