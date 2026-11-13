package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.common.config.withTransaction
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Instant

class ExposedDocumentRepositoryTest : FunSpec({
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
    val grantRepository = mockk<GrantRepository>()
    val repository = ExposedDocumentRepository(agentRepository, grantRepository)

    beforeTest { clearMocks(grantRepository) }
    val createdAt = Instant.parse("2025-01-01T00:00:00Z")
    val future = Instant.parse("2100-01-01T00:00:00Z")

    fun agent(type: AgentType = AgentType.Person) = AuthorizationAgent(UUID.randomUUID().toString(), type)

    fun document() = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = Json.parseToJsonElement("""{"nested":{"values":[null,false,1.5]}}""").jsonObject,
        validTo = future,
        requestedBy = agent(AgentType.Organization),
        requestedPrincipal = agent(AgentType.Organization),
        requestedOf = agent(),
        pdfBytes = byteArrayOf(0, 1, 127, -128, -1),
    ).copy(validFrom = createdAt + 2.days, createdAt = createdAt, updatedAt = createdAt + 1.days)

    fun grant(document: AuthorizationDocument, signatory: AuthorizationAgent) = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.fromString(document.id),
        principal = document.requestedPrincipal,
        grantedBy = document.requestedOf,
        grantedTo = document.requestedBy,
        approvedBy = signatory,
        validFrom = createdAt,
        validTo = future,
        scopes = emptyList(),
    )

    suspend fun seed(document: AuthorizationDocument): UUID = withTransaction {
        suspend fun agentId(agent: AuthorizationAgent) = agentRepository.findOrInsert(agent.type, agent.id).id
        val requestedBy = agentId(document.requestedBy)
        val requestedPrincipal = agentId(document.requestedPrincipal)
        val requestedOf = agentId(document.requestedOf)
        val approvedBy = document.approvedBy?.let { agentId(it) }
        val documentId = UUID.fromString(document.id)
        AuthorizationDocumentTable.insert {
            it[id] = documentId
            it[documentType] = document.documentType
            it[AuthorizationDocumentTable.requestedBy] = requestedBy
            it[AuthorizationDocumentTable.requestedPrincipal] = requestedPrincipal
            it[AuthorizationDocumentTable.requestedOf] = requestedOf
            it[AuthorizationDocumentTable.approvedBy] = approvedBy
            it[externalReference] = document.externalReference
            it[meta] = document.meta
            it[file] = document.pdfBytes
            it[AuthorizationDocumentTable.requestedGrantValidFrom] = document.requestedGrantValidFrom
            it[AuthorizationDocumentTable.requestedGrantValidTo] = document.requestedGrantValidTo
            it[validFrom] = document.validFrom
            it[validTo] = document.validTo
            it[AuthorizationDocumentTable.createdAt] = document.createdAt
            it[updatedAt] = document.updatedAt
        }
        document.requestedScopes.forEach { scope ->
            val scopeId = AuthorizationDocumentScopeTable.insert {
                it[AuthorizationDocumentScopeTable.documentId] = documentId
                it[resourceType] = scope.resourceType
            }[AuthorizationDocumentScopeTable.id].value
            scope.constraints.forEach { constraint ->
                AuthorizationDocumentScopeConstraintTable.insert {
                    it[AuthorizationDocumentScopeConstraintTable.scopeId] = scopeId
                    it[kind] = constraint.constraintKind
                    it[attribute] = constraint.attribute
                    it[value] = when (val value = constraint.value) {
                        is ResourceConstraint.MeteringPoints -> value.ids.map { it.value }
                        is ResourceConstraint.ContractTypes -> value.types.map { it.name }
                        is ResourceConstraint.ValidFrom -> listOf(value.value.toString())
                    }
                }
            }
        }
        documentId
    }

    test("find maps the stored document") {
        val constraints = listOf(
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AppliesTo,
                AuthorizationScopeConstraintAttribute.MeteringPointId,
                ResourceConstraint.MeteringPoints(
                    setOf(MeteringPointId.create("707057500000000001"), MeteringPointId.create("707057500000000002")),
                ),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ContractType,
                ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ValidFrom,
                ResourceConstraint.ValidFrom(createdAt),
            ),
        )
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val expected = document().copy(
            status = AuthorizationDocumentStatus.Approved,
            approvedBy = agent(),
            externalReference = "contract-123",
            requestedScopes = listOf(RequestedAuthorizationScope(ResourceType.MeteringPointContract, constraints), unconstrained),
        )
        val id = seed(expected)
        seed(document().copy(requestedScopes = expected.requestedScopes))

        val result = repository.find(id)

        result.copy(requestedScopes = emptyList(), pdfBytes = expected.pdfBytes) shouldBe expected.copy(requestedScopes = emptyList())
        result.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
        result.requestedScopes.size shouldBe 2
        result.requestedScopes.single { it.constraints.isEmpty() } shouldBe unconstrained
        val constrained = result.requestedScopes.single { it.constraints.isNotEmpty() }
        constrained.resourceType shouldBe ResourceType.MeteringPointContract
        constrained.constraints.shouldContainExactlyInAnyOrder(constraints)
    }

    test("find maps a document without optional relationships") {
        val expected = document().copy(documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization)
        val id = seed(expected)

        val result = repository.find(id)

        result.copy(pdfBytes = expected.pdfBytes) shouldBe expected
        result.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
    }

    test("find derives status with approval taking precedence over expiry") {
        val past = Instant.parse("2000-01-01T00:00:00Z")
        val expected = listOf(
            document() to AuthorizationDocumentStatus.Pending,
            document().copy(validTo = past) to AuthorizationDocumentStatus.Expired,
            document().copy(approvedBy = agent()) to AuthorizationDocumentStatus.Approved,
            document().copy(approvedBy = agent(), validTo = past) to AuthorizationDocumentStatus.Approved,
        )

        expected.forEach { (document, status) ->
            val id = seed(document)
            repository.find(id).status shouldBe status
        }
    }

    test("find throws a modeled not found error for an unknown document ID") {
        val id = UUID.randomUUID()

        val error = shouldThrow<RepositoryReadError.NotFoundError> { repository.find(id) }

        error.detail shouldBe "Document not found: $id"
    }

    test("listing includes requester or requestedPrincipal but excludes other roles and agent types") {
        val agent = agent(AgentType.Organization)
        val by = seed(document().copy(requestedBy = agent))
        val requestedPrincipal = seed(document().copy(requestedPrincipal = agent))
        val both = seed(document().copy(requestedBy = agent, requestedPrincipal = agent))
        seed(document().copy(requestedOf = agent))
        seed(document().copy(approvedBy = agent))
        seed(document().copy(requestedBy = agent.copy(type = AgentType.Person)))
        seed(document())

        val result = repository.findAndSortByCreatedAt(agent, Pagination(), emptyList())

        result.items.map { it.id }.shouldContainExactlyInAnyOrder(by.toString(), requestedPrincipal.toString(), both.toString())
        result.totalItems shouldBe 3L
    }

    test("listing sorts by creation time then UUID descending with consistent page totals") {
        val agent = agent()
        val ids = listOf(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002",
            "00000000-0000-0000-0000-000000000003",
            "00000000-0000-0000-0000-000000000004",
            "00000000-0000-0000-0000-000000000005",
        )
        val times = listOf(createdAt + 2.days, createdAt, createdAt + 2.days, createdAt + 3.days, createdAt + 1.days)
        ids.zip(times).forEach { (id, time) -> seed(document().copy(id = id, requestedBy = agent, createdAt = time)) }
        val expectedPages = listOf(listOf(ids[3], ids[2]), listOf(ids[0], ids[4]), listOf(ids[1]), emptyList())

        expectedPages.forEachIndexed { page, expected ->
            val pagination = Pagination(page = page, size = 2)
            val result = repository.findAndSortByCreatedAt(agent, pagination, emptyList())
            result.items.map { it.id } shouldBe expected
            result.totalItems shouldBe 5L
            result.pagination shouldBe pagination
        }
    }

    test("listing filters derived document statuses including combined and empty filters") {
        val agent = agent()
        val past = Instant.parse("2000-01-01T00:00:00Z")
        val expected = listOf(
            document() to AuthorizationDocumentStatus.Pending,
            document().copy(validTo = past) to AuthorizationDocumentStatus.Expired,
            document().copy(approvedBy = agent()) to AuthorizationDocumentStatus.Approved,
            document().copy(approvedBy = agent(), validTo = past) to AuthorizationDocumentStatus.Approved,
        ).associate { (document, status) -> seed(document.copy(requestedBy = agent)).toString() to status }
        seed(document().copy(validTo = past))
        seed(document().copy(approvedBy = agent()))
        val filters = AuthorizationDocumentStatus.entries.map { listOf(it) } + listOf(
            listOf(AuthorizationDocumentStatus.Pending, AuthorizationDocumentStatus.Expired),
            AuthorizationDocumentStatus.entries.toList(),
            emptyList(),
        )

        filters.forEach { statuses ->
            val matching = expected.filterValues { statuses.isEmpty() || it in statuses }
            val result = repository.findAndSortByCreatedAt(agent, Pagination(), statuses)
            result.items.associate { it.id to it.status } shouldBe matching
            result.totalItems shouldBe matching.size.toLong()
        }
    }

    test("listing returns empty pages when no agent or status matches") {
        val agent = agent()
        seed(document().copy(requestedBy = agent))

        val noAgent = repository.findAndSortByCreatedAt(agent(), Pagination(), emptyList())
        val noStatus = repository.findAndSortByCreatedAt(agent, Pagination(), listOf(AuthorizationDocumentStatus.Approved))

        listOf(noAgent, noStatus).forEach {
            it.items shouldBe emptyList()
            it.totalItems shouldBe 0L
            it.pagination shouldBe Pagination()
        }
    }

    test("listing maps each document with its own agents and scopes") {
        val agent = agent(AgentType.Organization)
        val constraints = listOf(
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AppliesTo,
                AuthorizationScopeConstraintAttribute.MeteringPointId,
                ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001"))),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ContractType,
                ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ValidFrom,
                ResourceConstraint.ValidFrom(createdAt),
            ),
        )
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val constrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, constraints)
        val documents = listOf(
            document().copy(
                requestedBy = agent,
                approvedBy = agent(),
                status = AuthorizationDocumentStatus.Approved,
                externalReference = "listed-contract",
                requestedScopes = listOf(constrained, unconstrained),
            ),
            document().copy(requestedPrincipal = agent, requestedScopes = listOf(unconstrained)),
            document().copy(requestedBy = agent, documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization),
        )
        documents.forEach { seed(it) }
        seed(document().copy(requestedScopes = listOf(constrained)))

        val result = repository.findAndSortByCreatedAt(agent, Pagination(), emptyList())

        result.totalItems shouldBe 3L
        val items = result.items.associateBy { it.id }
        documents.forEach { expected ->
            val actual = items.getValue(expected.id)
            actual.copy(requestedScopes = emptyList(), pdfBytes = expected.pdfBytes) shouldBe expected.copy(requestedScopes = emptyList())
            actual.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
            actual.requestedScopes.size shouldBe expected.requestedScopes.size
            actual.requestedScopes.filter { it.constraints.isEmpty() } shouldBe expected.requestedScopes.filter { it.constraints.isEmpty() }
            if (expected.requestedScopes.any { it.constraints.isNotEmpty() }) {
                actual.requestedScopes.single { it.constraints.isNotEmpty() }.constraints.shouldContainExactlyInAnyOrder(constraints)
            }
        }
    }

    test("insert, find and list preserve validFrom independently of createdAt and requested scopes") {
        val constraints = listOf(
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AppliesTo,
                AuthorizationScopeConstraintAttribute.MeteringPointId,
                ResourceConstraint.MeteringPoints(
                    setOf(MeteringPointId.create("707057500000000001"), MeteringPointId.create("707057500000000002")),
                ),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ContractType,
                ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ValidFrom,
                ResourceConstraint.ValidFrom(createdAt),
            ),
        )
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val expected = document().copy(
            externalReference = "inserted-contract",
            requestedGrantValidFrom = createdAt,
            requestedGrantValidTo = future,
            requestedScopes = listOf(RequestedAuthorizationScope(ResourceType.MeteringPointContract, constraints), unconstrained),
        )

        val inserted = repository.insert(expected)
        val found = repository.find(UUID.fromString(expected.id))
        val page = repository.findAndSortByCreatedAt(expected.requestedBy, Pagination(), emptyList())

        page.totalItems shouldBe 1L
        listOf(inserted, found, page.items.single()).forEach { actual ->
            actual.copy(requestedScopes = emptyList(), pdfBytes = expected.pdfBytes) shouldBe expected.copy(requestedScopes = emptyList())
            actual.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
            actual.requestedScopes.size shouldBe 2
            actual.requestedScopes.single { it.constraints.isEmpty() } shouldBe unconstrained
            val scope = actual.requestedScopes.single { it.constraints.isNotEmpty() }
            scope.resourceType shouldBe ResourceType.MeteringPointContract
            scope.constraints.shouldContainExactlyInAnyOrder(constraints)
        }
    }

    test("insert reuses agents for a document without optional fields") {
        val agent = agent(AgentType.Organization)
        val existing = agentRepository.findOrInsert(agent.type, agent.id)
        val expected = document().copy(
            documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
            requestedBy = agent,
            requestedPrincipal = agent,
            requestedOf = agent,
            meta = JsonObject(emptyMap()),
        )

        val inserted = repository.insert(expected)
        val found = repository.find(UUID.fromString(expected.id))

        listOf(inserted, found).forEach { actual ->
            actual.copy(pdfBytes = expected.pdfBytes) shouldBe expected
            actual.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
        }
        agentRepository.findOrInsert(agent.type, agent.id).id shouldBe existing.id
    }

    test("insert returns persisted derived status rather than the input status") {
        val past = Instant.parse("2000-01-01T00:00:00Z")
        val cases = listOf(
            document().copy(status = AuthorizationDocumentStatus.Approved) to AuthorizationDocumentStatus.Pending,
            document().copy(validTo = past) to AuthorizationDocumentStatus.Expired,
            document().copy(validTo = past, approvedBy = agent()) to AuthorizationDocumentStatus.Approved,
        )

        cases.forEach { (document, status) ->
            val inserted = repository.insert(document)
            val found = repository.find(UUID.fromString(document.id))
            listOf(inserted, found).forEach { actual ->
                actual.copy(pdfBytes = document.pdfBytes) shouldBe document.copy(status = status)
                actual.pdfBytes.toList() shouldBe document.pdfBytes.toList()
            }
        }
    }

    test("confirm persists signed document fields and inserts the grant") {
        val original = document().copy(
            externalReference = "confirmed-contract",
            requestedScopes = listOf(RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())),
        )
        val id = seed(original)
        val signatory = agent()
        val grant = grant(original, signatory)
        val storedGrant = grant.copy(updatedAt = createdAt)
        val signedFile = byteArrayOf(42, -128, 0, -1)
        coEvery { grantRepository.insert(grant) } returns storedGrant
        val before = now()

        val result = repository.confirm(id, signedFile, signatory, listOf(grant))

        val after = now()
        result.copy(pdfBytes = original.pdfBytes) shouldBe original.copy(
            status = AuthorizationDocumentStatus.Approved,
            approvedBy = signatory,
            updatedAt = result.updatedAt,
            authorizationGrants = listOf(storedGrant),
        )
        result.pdfBytes.toList() shouldBe signedFile.toList()
        (result.updatedAt in before..after) shouldBe true
        val found = repository.find(id)
        found.copy(pdfBytes = result.pdfBytes) shouldBe result.copy(authorizationGrants = null)
        found.pdfBytes.toList() shouldBe signedFile.toList()
        withTransaction {
            val row = AuthorizationDocumentTable.selectAll().where { AuthorizationDocumentTable.id eq id }.single()
            row[AuthorizationDocumentTable.signedAt] shouldBe result.updatedAt
        }
        coVerify(exactly = 1) { grantRepository.insert(grant) }
    }

    test("confirm persists multiple grants and returns all inserted grants") {
        val storedGrantRepository = ExposedGrantRepository(agentRepository)
        val documentRepository = ExposedDocumentRepository(agentRepository, storedGrantRepository)
        val original = document()
        val id = seed(original)
        val signatory = agent()
        val grants = listOf(
            grant(original, signatory),
            grant(original, signatory).copy(type = AuthorizationGrantType.Persistent),
        )
        val signedFile = byteArrayOf(42, -128, 0, -1)

        val result = documentRepository.confirm(id, signedFile, signatory, grants)

        result.status shouldBe AuthorizationDocumentStatus.Approved
        result.approvedBy shouldBe signatory
        result.pdfBytes.toList() shouldBe signedFile.toList()
        val insertedGrants = result.authorizationGrants ?: error("Confirmed document must contain its inserted grants")
        val fetchedGrants = storedGrantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(id))[id].orEmpty()
        insertedGrants.map { it.id }.shouldContainExactlyInAnyOrder(grants.map { it.id })
        fetchedGrants.map { it.id }.shouldContainExactlyInAnyOrder(grants.map { it.id })
        grants.forEach { expected ->
            val inserted = insertedGrants.single { it.id == expected.id }
            val fetched = fetchedGrants.single { it.id == expected.id }
            listOf(inserted, fetched).forEach { actual ->
                actual.copy(createdAt = expected.createdAt, updatedAt = expected.updatedAt) shouldBe expected
                ((actual.createdAt - expected.createdAt).absoluteValue <= 1.microseconds) shouldBe true
                ((actual.updatedAt - expected.updatedAt).absoluteValue <= 1.microseconds) shouldBe true
            }
            storedGrantRepository.find(expected.id) shouldBe fetched
        }
    }

    test("confirm rejects an empty grant list without changing the document") {
        val original = document()
        val id = seed(original)
        val before = repository.find(id)

        val error = shouldThrow<RepositoryWriteError.UnexpectedError> {
            repository.confirm(id, byteArrayOf(1), agent(), emptyList())
        }

        error.detail shouldBe "Cannot confirm document $id without grants"
        val after = repository.find(id)
        after.copy(pdfBytes = before.pdfBytes) shouldBe before
        after.pdfBytes.toList() shouldBe before.pdfBytes.toList()
        withTransaction {
            AuthorizationDocumentTable.selectAll().where { AuthorizationDocumentTable.id eq id }
                .single()[AuthorizationDocumentTable.signedAt] shouldBe null
        }
        coVerify(exactly = 0) { grantRepository.insert(any()) }
    }

    test("confirm rejects unknown documents without inserting a grant") {
        val original = document()
        val id = UUID.fromString(original.id)
        val signatory = agent()

        val error = shouldThrow<RepositoryWriteError.NotFoundError> {
            repository.confirm(id, byteArrayOf(1), signatory, listOf(grant(original, signatory)))
        }

        error.detail shouldBe "Document not found: $id"
        coVerify(exactly = 0) { grantRepository.insert(any()) }
    }

    test("confirm rejects expired documents without changing them") {
        val original = document().copy(validTo = Instant.parse("2000-01-01T00:00:00Z"))
        val id = seed(original)
        val before = repository.find(id)
        val signatory = agent()

        val error = shouldThrow<RepositoryWriteError.ExpiredError> {
            repository.confirm(id, byteArrayOf(1), signatory, listOf(grant(original, signatory)))
        }

        error.detail shouldBe "Document $id has expired"
        val after = repository.find(id)
        after.copy(pdfBytes = before.pdfBytes) shouldBe before
        after.pdfBytes.toList() shouldBe before.pdfBytes.toList()
        coVerify(exactly = 0) { grantRepository.insert(any()) }
    }

    test("confirm rejects accepted documents") {
        listOf(future, Instant.parse("2000-01-01T00:00:00Z")).forEach { validTo ->
            val original = document().copy(approvedBy = agent(), validTo = validTo)
            val id = seed(original)
            val before = repository.find(id)
            val signatory = agent()

            val error = shouldThrow<RepositoryWriteError.ConflictError> {
                repository.confirm(id, byteArrayOf(1), signatory, listOf(grant(original, signatory)))
            }

            error.detail shouldBe "Invalid transition for document $id from Approved to Approved"
            val after = repository.find(id)
            after.copy(pdfBytes = before.pdfBytes) shouldBe before
            after.pdfBytes.toList() shouldBe before.pdfBytes.toList()
        }
        coVerify(exactly = 0) { grantRepository.insert(any()) }
    }

    test("confirm rolls back the document and earlier grants when a later grant insertion fails") {
        val original = document()
        val id = seed(original)
        val before = repository.find(id)
        val signatory = agent()
        val firstGrant = grant(original, signatory)
        val secondGrant = grant(original, signatory)
        val storedGrantRepository = ExposedGrantRepository(agentRepository)
        val failure = IllegalStateException("Grant insertion failed")
        coEvery { grantRepository.insert(firstGrant) } coAnswers { storedGrantRepository.insert(firstGrant) }
        coEvery { grantRepository.insert(secondGrant) } coAnswers {
            withTransaction {
                val row = AuthorizationDocumentTable.selectAll().where { AuthorizationDocumentTable.id eq id }.single()
                (row[AuthorizationDocumentTable.approvedBy] != null) shouldBe true
            }
            storedGrantRepository.find(firstGrant.id).id shouldBe firstGrant.id
            throw failure
        }

        val error = shouldThrow<IllegalStateException> {
            repository.confirm(id, byteArrayOf(1), signatory, listOf(firstGrant, secondGrant))
        }

        error.message shouldBe failure.message
        val after = repository.find(id)
        after.copy(pdfBytes = before.pdfBytes) shouldBe before
        after.pdfBytes.toList() shouldBe before.pdfBytes.toList()
        withTransaction {
            AuthorizationDocumentTable.selectAll().where { AuthorizationDocumentTable.id eq id }
                .single()[AuthorizationDocumentTable.signedAt] shouldBe null
        }
        storedGrantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(id))[id].orEmpty() shouldBe emptyList()
        coVerify(exactly = 1) { grantRepository.insert(firstGrant) }
        coVerify(exactly = 1) { grantRepository.insert(secondGrant) }
    }

    test("concurrent confirmations produce one success and one conflict") {
        val original = document()
        val id = seed(original)
        val signatory = agent()
        val grant = grant(original, signatory)
        coEvery { grantRepository.insert(grant) } returns grant

        val results = coroutineScope {
            val start = CompletableDeferred<Unit>()
            val attempts = List(2) {
                async {
                    start.await()
                    runCatching { repository.confirm(id, byteArrayOf(1), signatory, listOf(grant)) }
                }
            }
            start.complete(Unit)
            attempts.awaitAll()
        }

        results.count { it.isSuccess } shouldBe 1
        (results.single { it.isFailure }.exceptionOrNull() is RepositoryWriteError.ConflictError) shouldBe true
        repository.find(id).status shouldBe AuthorizationDocumentStatus.Approved
        coVerify(exactly = 1) { grantRepository.insert(grant) }
    }
})
