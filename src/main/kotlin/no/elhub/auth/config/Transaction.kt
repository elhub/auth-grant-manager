package no.elhub.auth.config

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.concurrent.TimeUnit

class TransactionContext(private val meterRegistry: PrometheusMeterRegistry) {
    suspend fun <T> withTransaction(block: suspend JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        suspendTransaction { block() }
    }

    suspend operator fun <E, A> invoke(
        name: String,
        onException: (Throwable) -> E,
        block: suspend Raise<E>.() -> A
    ): Either<E, A> =
        meterRegistry.measureTransaction(name) {
            Either.catch { withTransaction { either<E, A> { block(this) } } }
                .mapLeft(onException)
                .fold({ it.left() }, { it })
        }
}

suspend fun <T> PrometheusMeterRegistry.measureTransaction(
    name: String,
    block: suspend () -> T
): T {
    val timer = timer(name)
    val start = System.nanoTime()
    return try {
        block()
    } finally {
        timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
}
