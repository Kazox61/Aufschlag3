package com.kazox.aufschlag

import com.kazox.aufschlag.config.AppConfig
import com.kazox.aufschlag.config.AppConfig.Companion.DEFAULT_CORS_ORIGINS
import com.kazox.aufschlag.config.AuthConfig
import com.kazox.aufschlag.db.createDataSource
import com.kazox.aufschlag.db.runMigrations
import com.kazox.aufschlag.mail.LoggingMailer
import com.kazox.aufschlag.mail.Mailer
import com.kazox.aufschlag.mail.ResendMailer
import com.kazox.aufschlag.routes.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import javax.sql.DataSource

fun main() {
    val config = AppConfig.fromEnv()
    val dataSource = createDataSource(config.database)
    runMigrations(dataSource)
    val database = Database.connect(dataSource)
    val mailer = config.mail.apiKey
        ?.let { ResendMailer(apiKey = it, from = config.mail.from) }
        ?: LoggingMailer()

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(dataSource, database, config.auth, mailer, config.corsAllowedOrigins)
    }.start(wait = true)
}

/** Wiring shared by main() and testApplication; tests pass a Testcontainers-backed
 *  DataSource, their own AuthConfig (short TTLs etc.) and a recording Mailer. */
fun Application.module(
    dataSource: DataSource,
    database: Database,
    auth: AuthConfig,
    mailer: Mailer,
    corsAllowedOrigins: List<String> = DEFAULT_CORS_ORIGINS,
) {
    install(Koin) {
        slf4jLogger()
        modules(appModule(dataSource, database, auth, mailer))
    }
    install(CallLogging)
    configureCors(corsAllowedOrigins)
    configureSerialization()
    configureSecurity(auth)
    configureStatusPages()
    configureRouting()
}
