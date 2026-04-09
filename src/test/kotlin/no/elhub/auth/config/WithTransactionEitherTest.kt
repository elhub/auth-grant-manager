package no.elhub.auth.config

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import org.jetbrains.exposed.v1.jdbc.Database

private sealed interface TestError {
    data class DomainError(val msg: String) : TestError
    data class UnexpectedError(val msg: String) : TestError
}

class WithTransactionEitherTest : FunSpec({
    extensions(PostgresTestContainerExtension())

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    test("returns Right when block succeeds") {
        val result = withTransactionEither({ TestError.UnexpectedError(it.message ?: "") }) {
            42
        }
        result shouldBe 42.right()
    }

    test("returns Left with domain error when block raises") {
        val result = withTransactionEither<TestError, Int>({ TestError.UnexpectedError(it.message ?: "") }) {
            raise(TestError.DomainError("not found"))
        }
        result shouldBe TestError.DomainError("not found").left()
    }

    test("returns Left mapped through onException when block throws") {
        val result = withTransactionEither({ e -> TestError.UnexpectedError(e.message ?: "") }) {
            error("db exploded")
        }
        result shouldBe TestError.UnexpectedError("db exploded").left()
    }
})
