package no.elhub.auth.features.requests.common

import arrow.core.getOrElse
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.config.withTransaction
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.grants.common.AuthorizationGrantPropertyTable
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.grants.common.ExposedGrantPropertiesRepository
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.ExperimentalTime

class ExposedRequestRepositoryTest : FunSpec({
    extensions(
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql"),
    )
    val transactionContext = TransactionContext(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
    val partyRepo = ExposedPartyRepository()
    val requestPropertiesRepo = ExposedRequestPropertiesRepository()
    val grantPropertiesRepository = ExposedGrantPropertiesRepository(transactionContext)
    val grantRepository = ExposedGrantRepository(partyRepo, grantPropertiesRepository, transactionContext)
    val requestRepo = ExposedRequestRepository(
        partyRepo,
        requestPropertiesRepo,
        grantRepository,
        grantPropertiesRepository,
        transactionContext
    )

    val scopes = listOf(
        CreateScopeData(
            authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
            authorizedResourceId = "1234",
            permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson
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

        repeat(numTargetRequests) {
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                requestedBy = targetParty1,
                requestedFrom = targetParty2,
                requestedTo = targetParty2,
                validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
            )
            requestRepo.insert(request, scopes)
        }

        repeat(numOtherRequests) {
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                requestedBy = otherParty,
                requestedFrom = otherParty,
                requestedTo = otherParty,
                validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
            )
            requestRepo.insert(request, scopes)
        }

        val requestsOfTargetParty1 =
            requestRepo.findAllAndSortByCreatedAt(targetParty1, Pagination(size = 200), listOf())
                .getOrElse { _ ->
                    fail("findAllAndSortByCreatedAt failed for target party 1")
                }
        requestsOfTargetParty1.items.size shouldBe numTargetRequests

        requestRepo.findAllAndSortByCreatedAt(targetParty2, Pagination(size = 200), listOf())
            .getOrElse { _ ->
                fail("findAllAndSortByCreatedAt failed for target party 2")
            }
        requestsOfTargetParty1.items.size shouldBe numTargetRequests
    }

    test("findAllAndSortByCreatedAt returns requests by createdAt DESC") {
        val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
        val numRequests = 10

        repeat(numRequests) {
            val request = AuthorizationRequest.create(
                type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                requestedBy = party,
                requestedFrom = party,
                requestedTo = party,
                validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
            )
            requestRepo.insert(request, scopes)
        }

        val result = requestRepo.findAllAndSortByCreatedAt(party, Pagination(size = 100), listOf())
            .getOrElse { throw AssertionError("Repository read failed: $it") }

        val createdAtList = result.items.map { it.createdAt }

        createdAtList shouldBe createdAtList.sortedDescending()
    }

    test("findAllAndSortByCreatedAt filters properly on status and party together") {
        val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
        val numRequestsPending = 10
        val numRequestsAccepted = 8
        val numRequestsExpired = 3
        val numRequestsRejected = 11

        val dummyRequest = AuthorizationRequest.create(
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            requestedBy = party,
            requestedFrom = party,
            requestedTo = party,
            validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
        )

        // insert some requests for other party that we expect not to get returned
        repeat(5) {
            val party2 = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
            val request = dummyRequest.copy(
                id = UUID.randomUUID(),
                requestedBy = party2,
                requestedFrom = party2,
                requestedTo = party2,
            )
            requestRepo.insert(request, scopes)
        }

        repeat(numRequestsPending) {
            val request = dummyRequest.copy(id = UUID.randomUUID(), status = AuthorizationRequest.Status.Pending)
            requestRepo.insert(request, scopes)
        }
        repeat(numRequestsAccepted) {
            val request = dummyRequest.copy(id = UUID.randomUUID(), status = AuthorizationRequest.Status.Accepted)
            requestRepo.insert(request, scopes)
        }
        repeat(numRequestsExpired) {
            val request = dummyRequest.copy(
                id = UUID.randomUUID(),
                status = AuthorizationRequest.Status.Pending,
                validTo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            )
            requestRepo.insert(request, scopes)
        }
        repeat(numRequestsRejected) {
            val request = dummyRequest.copy(id = UUID.randomUUID(), status = AuthorizationRequest.Status.Rejected)
            requestRepo.insert(request, scopes)
        }

        val resultPending =
            requestRepo.findAllAndSortByCreatedAt(
                party,
                Pagination(size = 100),
                listOf(AuthorizationRequest.Status.Pending)
            )
                .getOrElse { throw AssertionError("Repository read failed: $it") }
                .totalItems shouldBe numRequestsPending

        val resultAccepted =
            requestRepo.findAllAndSortByCreatedAt(
                party,
                Pagination(size = 100),
                listOf(AuthorizationRequest.Status.Accepted)
            )
                .getOrElse { throw AssertionError("Repository read failed: $it") }
                .totalItems shouldBe numRequestsAccepted

        val resultExpired =
            requestRepo.findAllAndSortByCreatedAt(
                party,
                Pagination(size = 100),
                listOf(AuthorizationRequest.Status.Expired)
            )
                .getOrElse { throw AssertionError("Repository read failed: $it") }
                .totalItems shouldBe numRequestsExpired

        val resultRejected =
            requestRepo.findAllAndSortByCreatedAt(
                party,
                Pagination(size = 100),
                listOf(AuthorizationRequest.Status.Rejected)
            )
                .getOrElse { throw AssertionError("Repository read failed: $it") }
                .totalItems shouldBe numRequestsRejected

        val resultPendingPlusExpired =
            requestRepo.findAllAndSortByCreatedAt(
                party,
                Pagination(size = 100),
                listOf(AuthorizationRequest.Status.Pending, AuthorizationRequest.Status.Expired)
            ).getOrElse { throw AssertionError("Repository read failed: $it") }
                .totalItems shouldBe numRequestsPending + numRequestsExpired

        val expectedTotal = numRequestsPending + numRequestsAccepted + numRequestsExpired + numRequestsRejected
        val resultAll =
            requestRepo.findAllAndSortByCreatedAt(party, Pagination(size = 100), listOf())
                .getOrElse { throw AssertionError("Repository read failed: $it") }
                .totalItems shouldBe expectedTotal
    }

    test("findAllAndSortByCreatedAt returns empty list for party with no requests") {
        val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
        val result = requestRepo.findAllAndSortByCreatedAt(party, Pagination(), listOf())
            .getOrElse { throw AssertionError("Repository read failed: $it") }
        result.items shouldBe emptyList()
    }

    context("pagination") {
        test("returns correct page size and totalItems") {
            val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
            repeat(5) {
                requestRepo.insert(
                    AuthorizationRequest.create(
                        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                        requestedBy = party,
                        requestedFrom = party,
                        requestedTo = party,
                        validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                    ),
                    scopes
                )
            }

            val page = requestRepo.findAllAndSortByCreatedAt(party, Pagination(page = 0, size = 2), listOf())
                .getOrElse { fail("findAllAndSortByCreatedAt failed") }

            page.items.size shouldBe 2
            page.totalItems shouldBe 5
            page.totalPages shouldBe 3
        }

        test("returns next page when page=1") {
            val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
            repeat(5) {
                requestRepo.insert(
                    AuthorizationRequest.create(
                        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                        requestedBy = party,
                        requestedFrom = party,
                        requestedTo = party,
                        validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                    ),
                    scopes
                )
            }

            val page = requestRepo.findAllAndSortByCreatedAt(party, Pagination(page = 1, size = 2), listOf())
                .getOrElse { fail("findAllAndSortByCreatedAt failed") }

            page.items.size shouldBe 2
            page.totalItems shouldBe 5
        }

        test("returns partial last page") {
            val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
            repeat(5) {
                requestRepo.insert(
                    AuthorizationRequest.create(
                        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                        requestedBy = party,
                        requestedFrom = party,
                        requestedTo = party,
                        validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                    ),
                    scopes
                )
            }

            val page = requestRepo.findAllAndSortByCreatedAt(party, Pagination(page = 2, size = 2), listOf())
                .getOrElse { fail("findAllAndSortByCreatedAt failed") }

            page.items.size shouldBe 1
            page.totalItems shouldBe 5
        }

        test("returns empty items but correct totalItems when page is beyond data") {
            val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
            repeat(5) {
                requestRepo.insert(
                    AuthorizationRequest.create(
                        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                        requestedBy = party,
                        requestedFrom = party,
                        requestedTo = party,
                        validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                    ),
                    scopes
                )
            }

            val page = requestRepo.findAllAndSortByCreatedAt(party, Pagination(page = 10, size = 2), listOf())
                .getOrElse { fail("findAllAndSortByCreatedAt failed") }

            page.items shouldBe emptyList()
            page.totalItems shouldBe 5
        }

        test("pages do not overlap") {
            val party = AuthorizationParty(type = PartyType.Person, id = UUID.randomUUID().toString())
            repeat(5) {
                requestRepo.insert(
                    AuthorizationRequest.create(
                        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                        requestedBy = party,
                        requestedFrom = party,
                        requestedTo = party,
                        validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                    ),
                    scopes
                )
            }

            val page0 = requestRepo.findAllAndSortByCreatedAt(party, Pagination(page = 0, size = 2), listOf())
                .getOrElse { fail("page 0 failed") }
            val page1 = requestRepo.findAllAndSortByCreatedAt(party, Pagination(page = 1, size = 2), listOf())
                .getOrElse { fail("page 1 failed") }

            (page0.items.map { it.id }.toSet() intersect page1.items.map { it.id }.toSet()) shouldBe emptySet()
        }
    }

    test("find returns correct request") {
        val requests = List(10) {
            generateRequestWithoutProperties()
        }
        val targetId = requests[0].id
        requests.forEach { requestRepo.insert(it, scopes) }
        val targetRequest = requestRepo.find(targetId)
            .getOrElse { _ ->
                fail("find failed")
            }

        targetRequest.id shouldBe targetId
    }

    test("findScopeIds returns correct scope list") {
        val requestId = UUID.fromString("3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47")
        val scopeIds = requestRepo.findScopeIds(requestId)
        scopeIds.shouldBeRight()
        scopeIds.value.size shouldBe 2
        scopeIds.value.shouldContainAll(
            listOf(
                UUID.fromString("e705af95-571d-47ea-9b1b-742aa598c85c"),
                UUID.fromString("c597482d-b013-400b-9362-35bb16724c8f")
            )
        )
    }

    test("reject authorization request without properties") {
        val request = generateRequestWithoutProperties()
        val savedRequest = requestRepo
            .insert(request, scopes)
            .getOrElse { fail("insert failed") }

        val rejectedRequest = requestRepo.rejectRequest(savedRequest.id)
            .getOrElse { fail("reject failed") }

        rejectedRequest.properties.size shouldBe 0
        rejectedRequest.status shouldBe AuthorizationRequest.Status.Rejected
    }

    test("reject authorization request with properties") {
        val request = generateRequestWithoutProperties()
        val savedRequest = requestRepo
            .insert(request, scopes)
            .getOrElse { fail("insert failed") }

        requestPropertiesRepo.insert(
            listOf(
                AuthorizationRequestProperty(savedRequest.id, "key1", "value1"),
                AuthorizationRequestProperty(savedRequest.id, "key2", "value2"),
            )
        )

        val rejectedRequest = requestRepo.rejectRequest(savedRequest.id)
            .getOrElse { fail("reject failed") }

        rejectedRequest.properties.size shouldBe 2
        rejectedRequest.status shouldBe AuthorizationRequest.Status.Rejected
    }

    context("acceptWithGrant") {
        test("atomically accepts request, creates grant and persists grant properties") {
            val requestedBy = AuthorizationParty(type = PartyType.Person, id = "accept-req-by")
            val requestedFrom = AuthorizationParty(type = PartyType.Person, id = "accept-req-from")
            val approvedBy = AuthorizationParty(type = PartyType.Person, id = "accept-approved-by")

            val savedRequest = requestRepo.insert(
                AuthorizationRequest.create(
                    type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = requestedBy,
                    requestedFrom = requestedFrom,
                    requestedTo = approvedBy,
                    validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                ),
                scopes
            ).getOrElse { fail("insert failed") }

            val scopeIds = requestRepo.findScopeIds(savedRequest.id)
                .getOrElse { fail("findScopeIds failed") }

            val grant = AuthorizationGrant.create(
                grantedFor = requestedFrom,
                grantedBy = approvedBy,
                grantedTo = requestedBy,
                sourceType = AuthorizationGrant.SourceType.Request,
                sourceId = savedRequest.id,
                scopeIds = scopeIds,
                validFrom = currentTimeUtc(),
                validTo = currentTimeUtc().plusDays(365),
            )
            val grantProperties = listOf(
                AuthorizationGrantProperty(grantId = grant.id, key = "meta-key", value = "meta-value"),
            )

            val result = requestRepo.acceptWithGrant(
                requestId = savedRequest.id,
                approvedBy = approvedBy,
                grant = grant,
                grantProperties = grantProperties,
            )

            result.shouldBeRight()
            val acceptedRequest = result.value
            acceptedRequest.status shouldBe AuthorizationRequest.Status.Accepted
            acceptedRequest.grantId shouldBe grant.id

            val createdGrant = grantRepository.find(grant.id).shouldBeRight()
            createdGrant.sourceId shouldBe savedRequest.id
            createdGrant.sourceType shouldBe AuthorizationGrant.SourceType.Request

            withTransaction {
                val storedProperties = AuthorizationGrantPropertyTable
                    .selectAll()
                    .where { AuthorizationGrantPropertyTable.grantId eq grant.id }
                    .map { it[AuthorizationGrantPropertyTable.key] to it[AuthorizationGrantPropertyTable.value] }
                storedProperties shouldContainExactlyInAnyOrder listOf("meta-key" to "meta-value")
            }
        }

        test("accepted request retains its properties") {
            val requestedBy = AuthorizationParty(type = PartyType.Person, id = "accept-props-req-by")
            val requestedFrom = AuthorizationParty(type = PartyType.Person, id = "accept-props-req-from")
            val approvedBy = AuthorizationParty(type = PartyType.Person, id = "accept-props-approved-by")

            val savedRequest = requestRepo.insert(
                AuthorizationRequest.create(
                    type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
                    requestedBy = requestedBy,
                    requestedFrom = requestedFrom,
                    requestedTo = approvedBy,
                    validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
                ),
                scopes
            ).getOrElse { fail("insert failed") }

            requestPropertiesRepo.insert(
                listOf(
                    AuthorizationRequestProperty(savedRequest.id, "prop-key1", "prop-val1"),
                    AuthorizationRequestProperty(savedRequest.id, "prop-key2", "prop-val2"),
                )
            )

            val grant = AuthorizationGrant.create(
                grantedFor = requestedFrom,
                grantedBy = approvedBy,
                grantedTo = requestedBy,
                sourceType = AuthorizationGrant.SourceType.Request,
                sourceId = savedRequest.id,
                scopeIds = emptyList(),
                validFrom = currentTimeUtc(),
                validTo = currentTimeUtc().plusDays(365),
            )

            val acceptedRequest = requestRepo.acceptWithGrant(
                requestId = savedRequest.id,
                approvedBy = approvedBy,
                grant = grant,
                grantProperties = emptyList(),
            ).getOrElse { fail("acceptWithGrant failed") }

            acceptedRequest.status shouldBe AuthorizationRequest.Status.Accepted
            acceptedRequest.properties.size shouldBe 2
            acceptedRequest.approvedBy shouldNotBe null
        }
    }
})

@OptIn(ExperimentalTime::class)
private fun generateRequestWithoutProperties(): AuthorizationRequest = AuthorizationRequest.create(
    type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
    requestedBy = AuthorizationParty(type = PartyType.Person, id = "12345"),
    requestedFrom = AuthorizationParty(type = PartyType.Person, id = "56789"),
    requestedTo = AuthorizationParty(type = PartyType.Person, id = "45567"),
    // validTo is set by the value stream team in production,
    // but we set it here for testing purposes
    validTo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
)
