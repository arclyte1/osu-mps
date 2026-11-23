package osu.mps.app.plugins

import osu.mps.config.AppConfig
import osu.mps.data.AllTables
import osu.mps.data.SchemaPostStatements
import osu.mps.data.SchemaPreStatements
import osu.mps.data.migration.DatabaseMigrations
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    val config = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = AppConfig.jdbcUrl()
        username = AppConfig.dbUser()
        password = AppConfig.dbPassword()
        maximumPoolSize = 16
        minimumIdle = 2
        // Exposed opens/commits transactions itself; false leaves open tx on pooled connections.
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        connectionTimeout = 30_000
        validationTimeout = 5_000
        idleTimeout = 600_000
        maxLifetime = 1_800_000
        keepaliveTime = 30_000
        addDataSourceProperty("tcpKeepAlive", "true")
        validate()
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    transaction {
        SchemaPreStatements.forEach { exec(it.createStatement().single()) }
        DatabaseMigrations.run()
        SchemaUtils.createMissingTablesAndColumns(*AllTables)
        SchemaPostStatements.forEach { exec(it.createStatement().single()) }
    }

}
