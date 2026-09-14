package no.elhub.auth.config

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("DatabaseTransaction")

class TransactionContext(private val meterRegistry: PrometheusMeterRegistry) {

    suspend operator fun <E, A> invoke(
        metricName: String,
        className: String,
        methodName: String,
        onException: (Throwable) -> E,
        block: suspend Raise<E>.() -> A
    ): Either<E, A> =
        // Run a db operation inside a transaction and record its elapsed time.
        // On exception, trigger a rollback before mapping left.
        meterRegistry.measureTransaction(metricName, className, methodName) {
            either<E, A> {
                Either.catch { withTransaction { block(this@either) } }
                    .onLeft { e ->
                        val sql = e as? SQLException
                        logger.error("Transaction error [class={}, sqlState={}, errorCode={}]", e::class.qualifiedName, sql?.sqlState, sql?.errorCode)
                    }
                    .mapLeft(onException)
                    .bind()
            }
        }
}

suspend fun <T> withTransaction(block: suspend JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
    suspendTransaction { block() }
}

private suspend fun <T> PrometheusMeterRegistry.measureTransaction(
    metricName: String,
    className: String,
    methodName: String,
    block: suspend () -> T
): T {
    val timer = timer(metricName, Tags.of("class", className, "method", methodName))
    val start = System.nanoTime()
    return try {
        block()
    } finally {
        timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
}
