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
import com.kazox.aufschlag.services.TokenMaintenance
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = AppConfig.fromEnv()
    val dataSource = createDataSource(config.database)
    runMigrations(dataSource)
    val database = Database.connect(dataSource)
    val mailer = config.mail.apiKey
        ?.let { ResendMailer(apiKey = it, from = config.mail.from) }
        ?: LoggingMailer()

    val server = embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(dataSource, database, config.auth, mailer, config.corsAllowedOrigins, config.trustProxyHeaders)
        // Resources created out here are released out here, after the application (and Koin)
        // have fully stopped — the pool must outlive the last in-flight transaction.
        monitor.subscribe(ApplicationStopped) {
            mailer.close()
            dataSource.close()
        }
    }
    // SIGTERM → stop accepting connections, give in-flight requests a moment, then exit.
    server.addShutdownHook { server.stop(gracePeriodMillis = 3_000, timeoutMillis = 10_000) }
    server.start(wait = true)
}

/** Wiring shared by main() and testApplication; tests pass a Testcontainers-backed
 *  DataSource, their own AuthConfig (short TTLs etc.) and a recording Mailer. */
fun Application.module(
    dataSource: DataSource,
    database: Database,
    auth: AuthConfig,
    mailer: Mailer,
    corsAllowedOrigins: List<String> = DEFAULT_CORS_ORIGINS,
    trustProxyHeaders: Boolean = false,
) {
    install(Koin) {
        slf4jLogger()
        modules(appModule(dataSource, database, auth, mailer))
    }
    install(CallLogging)
    configureProxyHeaders(trustProxyHeaders)
    configureCors(corsAllowedOrigins)
    configureSerialization()
    configureSecurity(auth)
    configureStatusPages()
    configureRouting()
    configureBackgroundWork()
}

/** Starts the periodic jobs and drains background work on shutdown: the maintenance loop is
 *  cancelled first (it would otherwise never finish), then in-flight mail sends get a few
 *  seconds to complete before the scope is torn down. */
private fun Application.configureBackgroundWork() {
    val background by inject<BackgroundScope>()
    val tokenMaintenance by inject<TokenMaintenance>()
    val maintenanceJob = tokenMaintenance.start(background)
    monitor.subscribe(ApplicationStopping) {
        maintenanceJob.cancel()
        runBlocking { background.shutdown(timeout = 5.seconds) }
        log.info("Background work drained")
    }
}
