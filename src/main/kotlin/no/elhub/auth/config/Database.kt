package no.elhub.auth.config

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

fun Application.configureDatabase(): HikariDataSource {
    val config = HikariConfig().apply {
        jdbcUrl = environment.config.propertyOrNull("ktor.database.url")?.getString()
        driverClassName = environment.config.propertyOrNull("ktor.database.driverClass")?.getString()
        username = environment.config.propertyOrNull("ktor.database.username")?.getString()
        password = environment.config.propertyOrNull("ktor.database.password")?.getString()
        maximumPoolSize = environment.config.propertyOrNull("ktor.database.hikari.maximumPoolSize")?.getString()?.toInt() ?: 3
        schema = "auth"
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
    return dataSource
}

suspend fun <T> withTransaction(block: suspend JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
    suspendTransaction { block() }
}

/**
 * Runs [block] inside a transaction, catching any exception as [onException].
 * Replaces the verbose triple-nesting pattern:
 *   `either { Either.catch { withTransaction { either<E,A> { } } }.mapLeft{}.bind().bind() }`
 */
suspend fun <E, A> withTransactionEither(
    onException: (Throwable) -> E,
    block: suspend Raise<E>.() -> A
): Either<E, A> =
    Either.catch { withTransaction { either<E, A> { block(this) } } }
        .mapLeft(onException)
        .fold({ it.left() }, { it })
