package no.elhub.auth.bootstrap

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database

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
