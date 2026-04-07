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
import no.elhub.auth.config.withTransaction
import no.elhub.auth.features.common.CreateScopeData
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
    val metricsProvider = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val partyRepo = ExposedPartyRepository(metricsProvider)
    val requestPropertiesRepo = ExposedRequestPropertiesRepository(metricsProvider)
    val grantPropertiesRepository = ExposedGrantPropertiesRepository(metricsProvider)
    val grantRepository = ExposedGrantRepository(partyRepo, grantPropertiesRepository, metricsProvider)
    val requestRepo = ExposedRequestRepository(
        partyRepo,
        requestPropertiesRepo,
        grantRepository,
        grantPropertiesRepository,
        metricsProvider
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

        val result = requestRepo.findAllAndSortByCreatedAt(party)
            .getOrElse { throw AssertionError("Repository read failed: $it") }

        val createdAtList = result.map { it.createdAt }

        createdAtList shouldBe createdAtList.sortedDescending()
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
