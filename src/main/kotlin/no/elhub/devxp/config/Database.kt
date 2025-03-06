package no.elhub.devxp.config

import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

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
