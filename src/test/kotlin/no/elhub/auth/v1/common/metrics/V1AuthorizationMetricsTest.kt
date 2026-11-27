package no.elhub.auth.v1.common.metrics

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.config.withTransaction
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationRequestTextReference
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.repositories.AuthorizationDocumentTable
import no.elhub.auth.v1.repositories.AuthorizationRequestTable
import no.elhub.auth.v1.repositories.DatabaseRequestStatus
import no.elhub.auth.v1.repositories.ExposedAgentRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

class V1AuthorizationMetricsTest : FunSpec({
    extension(PostgresTestContainerExtension())

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    val agentRepository = ExposedAgentRepository()

    suspend fun insertRequest(
        type: AuthorizationRequestType,
        approved: Boolean = false,
        rejected: Boolean = false,
        timestamped: Boolean = true,
        validUntil: Instant = now() + 1.days,
    ) = withTransaction {
        val party = agentRepository.findOrInsert(AgentType.Person, Uuid.random().toString()).id
        AuthorizationRequestTable.insert {
            it[requestType] = type
            it[textReference] = AuthorizationRequestTextReference.forRequestType(type)
            it[status] = if (rejected) DatabaseRequestStatus.Rejected else DatabaseRequestStatus.Pending
            it[requestedBy] = party
            it[requestedPrincipal] = party
            it[requestedOf] = party
            it[decidedBy] = if (approved) party else null
            it[approvedAt] = if (approved && timestamped) now() else null
            it[validTo] = validUntil
            it[meta] = JsonObject(emptyMap())
        }
    }

    suspend fun insertDocument(
        type: AuthorizationDocumentType,
        approved: Boolean = false,
        timestamped: Boolean = true,
        validUntil: Instant = now() + 1.days,
    ) = withTransaction {
        val party = agentRepository.findOrInsert(AgentType.Person, Uuid.random().toString()).id
        AuthorizationDocumentTable.insert {
            it[documentType] = type
            it[requestedBy] = party
            it[requestedPrincipal] = party
            it[requestedOf] = party
            it[decidedBy] = if (approved) party else null
            it[signedAt] = if (approved && timestamped) now() else null
            it[validTo] = validUntil
            it[meta] = JsonObject(emptyMap())
            it[file] = ByteArray(0)
        }
    }

    test("refresh publishes created totals and current status counts per type") {
        val supplierChange = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization
        val frameworkAgreement = AuthorizationDocumentType.EnergySupplierFrameworkAgreement
        val pastValidTo = now() - 1.days
        insertRequest(supplierChange)
        insertRequest(supplierChange, validUntil = pastValidTo)
        insertRequest(supplierChange, approved = true)
        insertRequest(supplierChange, approved = true, timestamped = false, validUntil = pastValidTo)
        insertRequest(supplierChange, rejected = true)
        insertRequest(supplierChange, approved = true, rejected = true, validUntil = pastValidTo)
        insertDocument(frameworkAgreement)
        insertDocument(frameworkAgreement, validUntil = pastValidTo)
        insertDocument(frameworkAgreement, approved = true)
        insertDocument(frameworkAgreement, approved = true, timestamped = false, validUntil = pastValidTo)

        val registry = SimpleMeterRegistry()
        val metrics = V1AuthorizationMetrics(registry)
        val lastSuccess = registry.get(LAST_SUCCESS_METRIC).gauge()
        registry.find(REQUESTS_METRIC).gauges().all { it.value().isNaN() } shouldBe true
        registry.find(DOCUMENTS_METRIC).gauges().all { it.value().isNaN() } shouldBe true
        lastSuccess.value().isNaN() shouldBe true

        val beforeRefresh = System.currentTimeMillis() / 1000.0
        metrics.refresh()
        (lastSuccess.value() in beforeRefresh..System.currentTimeMillis() / 1000.0) shouldBe true

        fun value(name: String, type: Enum<*>, state: MetricState) =
            registry.get(name).tag("type", type.name).tag("state", state.label).gauge().value().toLong()

        value(REQUESTS_METRIC, supplierChange, MetricState.Created) shouldBe 6
        value(REQUESTS_METRIC, supplierChange, MetricState.Approved) shouldBe 2
        value(REQUESTS_METRIC, supplierChange, MetricState.Rejected) shouldBe 2
        value(REQUESTS_METRIC, supplierChange, MetricState.Pending) shouldBe 1
        value(REQUESTS_METRIC, supplierChange, MetricState.Expired) shouldBe 1
        value(REQUESTS_METRIC, AuthorizationRequestType.EnergySupplierFrameworkAgreement, MetricState.Created) shouldBe 0
        value(REQUESTS_METRIC, AuthorizationRequestType.EnergySupplierFrameworkAgreement, MetricState.Pending) shouldBe 0
        value(REQUESTS_METRIC, AuthorizationRequestType.EnergySupplierFrameworkAgreement, MetricState.Expired) shouldBe 0
        value(DOCUMENTS_METRIC, frameworkAgreement, MetricState.Created) shouldBe 4
        value(DOCUMENTS_METRIC, frameworkAgreement, MetricState.Approved) shouldBe 2
        value(DOCUMENTS_METRIC, frameworkAgreement, MetricState.Pending) shouldBe 1
        value(DOCUMENTS_METRIC, frameworkAgreement, MetricState.Expired) shouldBe 1
        value(DOCUMENTS_METRIC, AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization, MetricState.Approved) shouldBe 0
        value(DOCUMENTS_METRIC, AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization, MetricState.Pending) shouldBe 0
        value(DOCUMENTS_METRIC, AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization, MetricState.Expired) shouldBe 0
    }
})
