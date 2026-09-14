package com.kazox.aufschlag

import com.kazox.aufschlag.config.AuthConfig
import com.kazox.aufschlag.mail.LoggingMailer
import com.kazox.aufschlag.mail.Mailer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

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
            // port 0 lets the kernel pick a free port at bind time — no probe-then-bind gap for
            // a parallel test worker to grab the same port in
            val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
                module(TestDatabase.dataSource, TestDatabase.database, auth, mailer)
            }.start(wait = false)
            val port = runBlocking { server.engine.resolvedConnectors().single().port }
            return TestServer("http://127.0.0.1:$port") { server.stop(gracePeriodMillis = 0, timeoutMillis = 1000) }
        }
    }
}
