package no.elhub.auth.config

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
