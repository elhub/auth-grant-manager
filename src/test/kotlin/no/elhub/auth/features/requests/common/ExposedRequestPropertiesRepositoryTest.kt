package no.elhub.auth.features.requests.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ExposedRequestPropertiesRepositoryTest : FunSpec({

    extensions(
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql")
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
        transaction {
            AuthorizationRequestPropertyTable.deleteAll()
        }
    }

    context("Request properties repository") {
        test("insert empty list should return success ") {
            val result = propertyRepo.insert(emptyList())
            result.isRight() shouldBe true
        }

        test("insert properties should persist to database") {
            val properties = listOf(
                AuthorizationRequestProperty(requestId, "key1", "value1"),
                AuthorizationRequestProperty(requestId, "key2", "value2"),
            )

            val insertResult = propertyRepo.insert(properties)
            insertResult.isRight() shouldBe true

            transaction {
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
                stored[0].value shouldBe "value1"
                stored[1].value shouldBe "value2"

            }
        }

        test("insert properties with special characters should persist correctly") {
            val properties = listOf(
                AuthorizationRequestProperty(requestId, "address", "Main Street 42, 5000 Bergen"),
                AuthorizationRequestProperty(requestId, "name", "Kari Normann AS"),
            )

            val insertResult = propertyRepo.insert(properties)
            insertResult.isRight() shouldBe true

            transaction {
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
                stored[0].value shouldBe "Main Street 42, 5000 Bergen"
                stored[1].value shouldBe "Kari Normann AS"
            }
        }
    }
})
