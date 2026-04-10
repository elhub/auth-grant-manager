package no.elhub.auth.config

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.concurrent.TimeUnit

class TransactionContext(private val meterRegistry: PrometheusMeterRegistry) {

    suspend operator fun <E, A> invoke(
        metricName: String,
        className: String,
        methodName: String,
        onException: (Throwable) -> E,
        block: suspend Raise<E>.() -> A
    ): Either<E, A> =
        meterRegistry.measureTransaction(metricName, className, methodName) {
            Either.catch { withTransaction { either<E, A> { block(this) } } }
                .mapLeft(onException)
                .fold({ it.left() }, { it })
        }
}
suspend fun <T> withTransaction(block: suspend JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
    suspendTransaction { block() }
}

suspend fun <T> PrometheusMeterRegistry.measureTransaction(
    metricName: String,
    className: String,
    methodName: String,
    block: suspend () -> T
): T {
    val timer = timer(metricName, Tags.of(className, methodName))
    val start = System.nanoTime()
    return try {
        block()
    } finally {
        timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
}
