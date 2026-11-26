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
            it[meta] = JsonObject(emptyMap())
        }
    }

    suspend fun insertDocument(type: AuthorizationDocumentType, approved: Boolean = false, timestamped: Boolean = true) = withTransaction {
        val party = agentRepository.findOrInsert(AgentType.Person, Uuid.random().toString()).id
        AuthorizationDocumentTable.insert {
            it[documentType] = type
            it[requestedBy] = party
            it[requestedPrincipal] = party
            it[requestedOf] = party
            it[decidedBy] = if (approved) party else null
            it[signedAt] = if (approved && timestamped) now() else null
            it[meta] = JsonObject(emptyMap())
            it[file] = ByteArray(0)
        }
    }

    test("refresh publishes created, approved and rejected counts per type") {
        val supplierChange = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization
        insertRequest(supplierChange)
        insertRequest(supplierChange, approved = true)
        insertRequest(supplierChange, approved = true, timestamped = false)
        insertRequest(supplierChange, rejected = true)
        insertRequest(supplierChange, approved = true, rejected = true)
        insertDocument(AuthorizationDocumentType.EnergySupplierFrameworkAgreement, approved = true)
        insertDocument(AuthorizationDocumentType.EnergySupplierFrameworkAgreement, approved = true, timestamped = false)
        insertDocument(AuthorizationDocumentType.EnergySupplierFrameworkAgreement)

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

        val frameworkAgreement = AuthorizationDocumentType.EnergySupplierFrameworkAgreement
        value(REQUESTS_METRIC, supplierChange, MetricState.Created) shouldBe 5
        value(REQUESTS_METRIC, supplierChange, MetricState.Approved) shouldBe 2
        value(REQUESTS_METRIC, supplierChange, MetricState.Rejected) shouldBe 2
        value(REQUESTS_METRIC, AuthorizationRequestType.EnergySupplierFrameworkAgreement, MetricState.Created) shouldBe 0
        value(DOCUMENTS_METRIC, frameworkAgreement, MetricState.Created) shouldBe 3
        value(DOCUMENTS_METRIC, frameworkAgreement, MetricState.Approved) shouldBe 2
        value(DOCUMENTS_METRIC, AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization, MetricState.Approved) shouldBe 0
    }
})
