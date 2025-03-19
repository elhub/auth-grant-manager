package no.elhub.auth.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
        driverClassName = "org.postgresql.Driver"
        username = "postgres"
        password = "postgres"
        maximumPoolSize = 3
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
}
