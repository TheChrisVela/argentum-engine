package com.wingedsheep.gameserver.auth

import com.wingedsheep.gameserver.config.AccountsProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

/**
 * Sends the magic-link email. Uses the auto-configured [JavaMailSender] (Mailgun SMTP by default)
 * when mail credentials are configured; otherwise logs the link to the console so the whole login
 * flow is testable in local dev with no email account.
 *
 * The actual SMTP send runs on a background thread: connecting to the mail host can take seconds (or
 * stall on the configured timeout if it's unreachable), and the sign-in request must not block on it
 * — the login token is already persisted by the time we're called, and `request-login` deliberately
 * returns 200 regardless of delivery. Send failures are logged, not surfaced to the caller.
 */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class EmailService(
    private val props: AccountsProperties,
    private val mailSender: ObjectProvider<JavaMailSender>,
    @Value("\${spring.mail.username:}") private val mailUsername: String,
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val sendExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "magic-link-mailer").apply { isDaemon = true }
    }

    private val canSend: Boolean get() = mailUsername.isNotBlank() && mailSender.ifAvailable != null

    fun sendMagicLink(toEmail: String, link: String) {
        if (!canSend) {
            logger.warn(
                "Mail not configured (set MAIL_USERNAME/MAIL_PASSWORD) — magic link for {} is: {}",
                toEmail, link,
            )
            return
        }
        val message = SimpleMailMessage().apply {
            from = props.auth.fromEmail
            setTo(toEmail)
            subject = "Your Argentum sign-in link"
            text = buildString {
                appendLine("Click the link below to sign in to Argentum:")
                appendLine()
                appendLine(link)
                appendLine()
                appendLine("This link expires in ${props.auth.loginTokenTtlMinutes} minutes and can be used once.")
                appendLine("If you didn't request this, you can ignore this email.")
            }
        }
        sendExecutor.submit {
            try {
                mailSender.ifAvailable?.send(message)
                logger.info("Sent magic-link email to {}", toEmail)
            } catch (e: Exception) {
                // Don't fail the (already-200) login request; a misconfigured/unreachable mail host
                // must not stall sign-in. Log loudly so the operator can fix delivery.
                logger.error("Failed to send magic-link email to {}: {}", toEmail, e.message, e)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        sendExecutor.shutdown()
    }
}
