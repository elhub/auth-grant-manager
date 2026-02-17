package no.elhub.auth.features.requests.common

import arrow.core.getOrElse
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeSortedDescending
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.requests.AuthorizationRequest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.fail
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

    val scopes = listOf(
        CreateScopeData(
            authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
            authorizedResourceId = "1234",
            permissionType = AuthorizationScope.PermissionType.ChangeOfEnergySupplierForPerson
        )
    )

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

    test("findAllAndSortByCreatedAt returns all requests matching party") {
        val targetParty1 = AuthorizationParty(type = PartyType.Person, id = "67652749875413695986")
        val targetParty2 = AuthorizationParty(type = PartyType.Person, id = "17652749875413695986")
        val otherParty = AuthorizationParty(type = PartyType.Person, id = "413695986")
        val numTargetRequests = 100
        val numOtherRequests = 50
        transaction {
            repeat(numTargetRequests) {
                val request = AuthorizationRequest.create(
                    type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    requestedBy = targetParty1,
                    requestedFrom = targetParty2,
                    requestedTo = targetParty2,
                    validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                )
                requestRepo.insert(request, scopes)
            }

            repeat(numOtherRequests) {
                val request = AuthorizationRequest.create(
                    type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    requestedBy = otherParty,
                    requestedFrom = otherParty,
                    requestedTo = otherParty,
                    validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                )
                requestRepo.insert(request, scopes)
            }

            val requestsOfTargetParty1 = requestRepo.findAllAndSortByCreatedAt(targetParty1)
                .getOrElse { _ ->
                    fail("findAllAndSortByCreatedAt failed for target party 1")
                }
            requestsOfTargetParty1.size shouldBe numTargetRequests

            requestRepo.findAllAndSortByCreatedAt(targetParty2)
                .getOrElse { _ ->
                    fail("findAllAndSortByCreatedAt failed for target party 2")
                }
            requestsOfTargetParty1.size shouldBe numTargetRequests
        }
    }

    test("findAllAndSortByCreatedAt returns requests by createdAt DESC") {
        val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
        val numRequests = 10

        transaction {
            repeat(numRequests) {
                val request = AuthorizationRequest.create(
                    type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                    requestedBy = party,
                    requestedFrom = party,
                    requestedTo = party,
                    validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                )
                requestRepo.insert(request, scopes)
            }

            val result = requestRepo.findAllAndSortByCreatedAt(party)
                .getOrElse { throw AssertionError("Repository read failed: $it") }

            val createdAtList = result.map { it.createdAt }

            createdAtList.requireNoNulls().shouldBeSortedDescending()
        }
    }

    test("find returns correct request") {
        val requests = List(10) {
            generateRequestWithoutProperties()
        }
        val targetId = requests[0].id
        val targetRequest = transaction {
            requests.forEach { requestRepo.insert(it, scopes) }
            requestRepo.find(targetId)
        }.getOrElse { _ ->
            fail("find failed")
        }

        targetRequest.id shouldBe targetId
    }

    test("confirm authorization request with properties") {
        val request = generateRequestWithoutProperties()
        val acceptedRequest = transaction {
            val savedRequest = requestRepo
                .insert(request, scopes)
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
                .insert(request, scopes)
                .getOrElse {
                    fail("insert failed")
                }
            requestRepo.rejectRequest(savedRequest.id)
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
                .insert(request, scopes)
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
            val insertedRequest = requestRepo.insert(requestToConfirm, scopes)
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
    requestedBy = AuthorizationParty(type = PartyType.Person, id = "12345"),
    requestedFrom = AuthorizationParty(type = PartyType.Person, id = "56789"),
    requestedTo = AuthorizationParty(type = PartyType.Person, id = "45567"),
    // validTo is set by the value stream team in production,
    // but we set it here for testing purposes
    validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
)
