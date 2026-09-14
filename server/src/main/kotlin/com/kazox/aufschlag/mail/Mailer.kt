package com.kazox.aufschlag.mail

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** Transactional email. Semantic methods so templates live behind the interface. */
interface Mailer {
    suspend fun sendPasswordReset(to: String, resetToken: String)

    /** Mitgliedsantrag decision — without this, the apply→approve loop silently stalls for
     *  the applicant. */
    suspend fun sendApplicationDecision(to: String, clubName: String, approved: Boolean)

    /** Releases any underlying HTTP client; called once on server shutdown. */
    fun close() {}
}

/** Dev fallback when no MAIL_API_KEY is configured: logs instead of sending. */
class LoggingMailer : Mailer {
    private val log = LoggerFactory.getLogger(LoggingMailer::class.java)

    override suspend fun sendPasswordReset(to: String, resetToken: String) {
        log.info("[dev mail] password reset for {}: token={}", to, resetToken)
    }

    override suspend fun sendApplicationDecision(to: String, clubName: String, approved: Boolean) {
        log.info("[dev mail] application decision for {}: club={} approved={}", to, clubName, approved)
    }
}

/** Resend (resend.com) — one POST per mail. Send failures are logged, never thrown:
 *  the reset endpoint answers 204 regardless (no enumeration), the user just retries. */
class ResendMailer(
    private val apiKey: String,
    private val from: String,
) : Mailer {
    private val log = LoggerFactory.getLogger(ResendMailer::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun sendPasswordReset(to: String, resetToken: String) {
        send(
            to = to,
            subject = "Aufschlag: Passwort zurücksetzen",
            text = "Dein Code zum Zurücksetzen des Passworts (30 Minuten gültig):\n\n" +
                "$resetToken\n\n" +
                "Falls du das nicht angefordert hast, ignoriere diese E-Mail.",
        )
    }

    override suspend fun sendApplicationDecision(to: String, clubName: String, approved: Boolean) {
        send(
            to = to,
            subject = "Aufschlag: Dein Mitgliedsantrag bei $clubName",
            text = if (approved) {
                "Dein Mitgliedsantrag bei $clubName wurde angenommen. Du kannst dich jetzt einloggen und Plätze buchen."
            } else {
                "Dein Mitgliedsantrag bei $clubName wurde leider abgelehnt."
            },
        )
    }

    override fun close() = client.close()

    private suspend fun send(to: String, subject: String, text: String) {
        val response = runCatching {
            client.post("https://api.resend.com/emails") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(SendEmailRequest(from = from, to = listOf(to), subject = subject, text = text))
            }
        }.getOrElse {
            log.error("Failed to send mail to {}: {}", to, subject, it)
            return
        }
        if (!response.status.isSuccess()) {
            log.error("Resend rejected mail to {}: {} ({})", to, subject, response.status)
        }
    }

    @Serializable
    private data class SendEmailRequest(
        val from: String,
        val to: List<String>,
        val subject: String,
        val text: String,
    )
}
