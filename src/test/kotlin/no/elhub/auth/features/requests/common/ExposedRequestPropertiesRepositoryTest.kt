package no.elhub.auth.features.requests.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.config.withTransaction
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class ExposedRequestPropertiesRepositoryTest : FunSpec({

    extensions(
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql"),
    )
    val propertyRepo = ExposedRequestPropertiesRepository()

    val requestId = UUID.fromString("4f71d596-99e4-415e-946d-7252c1a40c51")

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )

        transaction {
            SchemaUtils.create(AuthorizationRequestPropertyTable)
            SchemaUtils.create(AuthorizationRequestTable)
        }
    }

    beforeTest {
        withTransaction {
            AuthorizationRequestPropertyTable.deleteAll()
        }
    }

    context("Request properties repository") {
        test("insert empty list should not persist any rows") {
            propertyRepo.insert(emptyList())
            withTransaction {
                AuthorizationRequestPropertyTable
                    .selectAll()
                    .where { AuthorizationRequestPropertyTable.requestId eq requestId }
                    .count() shouldBe 0
            }
        }

        test("insert properties should persist to database") {
            val properties = listOf(
                AuthorizationRequestProperty(requestId, "key1", JsonPrimitive("value1")),
                AuthorizationRequestProperty(requestId, "key2", JsonPrimitive("value2")),
            )

            propertyRepo.insert(properties)

            withTransaction {
                val stored = AuthorizationRequestPropertyTable
                    .selectAll()
                    .where { AuthorizationRequestPropertyTable.requestId eq requestId }
                    .map { resultRow ->
                        AuthorizationRequestProperty(
                            requestId = resultRow[AuthorizationRequestPropertyTable.requestId],
                            key = resultRow[AuthorizationRequestPropertyTable.key],
                            value = resultRow[AuthorizationRequestPropertyTable.value],
                        )
                    }
                stored.size shouldBe 2
                stored[0].value shouldBe JsonPrimitive("value1")
                stored[1].value shouldBe JsonPrimitive("value2")
            }
        }

        test("insert properties with special characters should persist correctly") {
            val properties = listOf(
                AuthorizationRequestProperty(requestId, "address", JsonPrimitive("Main Street 42, 5000 Bergen")),
                AuthorizationRequestProperty(requestId, "name", JsonPrimitive("Kari Normann AS")),
            )

            propertyRepo.insert(properties)

            withTransaction {
                val stored = AuthorizationRequestPropertyTable
                    .selectAll()
                    .where { AuthorizationRequestPropertyTable.requestId eq requestId }
                    .map { resultRow ->
                        AuthorizationRequestProperty(
                            requestId = resultRow[AuthorizationRequestPropertyTable.requestId],
                            key = resultRow[AuthorizationRequestPropertyTable.key],
                            value = resultRow[AuthorizationRequestPropertyTable.value],
                        )
                    }

                stored.size shouldBe 2
                stored[0].value shouldBe JsonPrimitive("Main Street 42, 5000 Bergen")
                stored[1].value shouldBe JsonPrimitive("Kari Normann AS")
            }
        }

        test("insert structured property should preserve its JSON type") {
            val meteringPoints = buildJsonArray {
                add(buildJsonObject { put("id", "707057500000000001") })
                add(buildJsonObject { put("id", "707057500000000002") })
            }

            propertyRepo.insert(
                listOf(AuthorizationRequestProperty(requestId, "meteringPoints", meteringPoints))
            )

            withTransaction {
                propertyRepo.findBy(requestId).single().value shouldBe meteringPoints
            }
        }
    }
})
