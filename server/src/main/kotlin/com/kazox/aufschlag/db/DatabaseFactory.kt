package com.kazox.aufschlag.db

import com.kazox.aufschlag.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun createDataSource(config: DatabaseConfig): DataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            // pgjdbc types setString params as varchar, which makes citext comparisons
            // case-SENSITIVE (citext = varchar degrades to text = text); untyped params
            // let Postgres resolve against the column type instead
            addDataSourceProperty("stringtype", "unspecified")
            validate()
        },
    )

fun runMigrations(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}
