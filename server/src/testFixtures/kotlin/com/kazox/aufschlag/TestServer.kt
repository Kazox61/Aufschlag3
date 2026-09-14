package com.kazox.aufschlag

import com.kazox.aufschlag.config.AuthConfig
import com.kazox.aufschlag.mail.LoggingMailer
import com.kazox.aufschlag.mail.Mailer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket

val testAuthConfig = AuthConfig(jwtSecret = "test-secret-not-for-production-padding")

/**
 * A real server (Testcontainers Postgres via [TestDatabase] + Netty on a real socket) for
 * cross-module smoke tests — e.g. `:app:shared`'s `ClubFlowSmokeTest`, which needs a genuine
 * `HttpClient` (real sockets), not Ktor's in-process `testApplication` engine, to exercise its
 * production `HttpClientFactory`/`SecureStorage` stack end to end.
 */
class TestServer private constructor(val baseUrl: String, private val onStop: () -> Unit) {
    fun stop() = onStop()

    companion object {
        fun start(auth: AuthConfig = testAuthConfig, mailer: Mailer = LoggingMailer()): TestServer {
            val port = ServerSocket(0).use { it.localPort }
            val engine = embeddedServer(Netty, port = port, host = "127.0.0.1") {
                module(TestDatabase.dataSource, TestDatabase.database, auth, mailer)
            }.start(wait = false)
            return TestServer("http://127.0.0.1:$port") { engine.stop(gracePeriodMillis = 0, timeoutMillis = 1000) }
        }
    }
}
