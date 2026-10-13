package com.bookflow.service;

import com.bookflow.config.GmailServiceFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

/**
 * Sends transactional emails via the Gmail REST API using OAuth 2.0.
 *
 * <p>No SMTP connection or password is used here. The {@link GmailServiceFactory}
 * manages the OAuth credential lifecycle. This service builds a MIME email,
 * encodes it as base64url, and submits it through the Gmail
 * {@code users.messages.send} endpoint.
 *
 * <p>Credentials and tokens are never logged by this class.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final GmailServiceFactory gmailServiceFactory;

    @Value("${bookflow.mail.from}")
    private String fromAddress;

    @Value("${bookflow.app.base-url:}")
    private String configuredBaseUrl;

    @Value("${RAILWAY_PUBLIC_DOMAIN:}")
    private String railwayPublicDomain;

    @Value("${RAILWAY_STATIC_URL:}")
    private String railwayStaticUrl;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Constructs a safe, normalized email verification URL for the current environment.
     *
     * <p>Resolution strategy:
     * <ol>
     *   <li>If {@code bookflow.app.base-url} / {@code BOOKFLOW_APP_BASE_URL} is explicitly configured
     *       with a non-localhost URL (e.g. in production), that configured base URL is used.</li>
     *   <li>If the app is actively handling an HTTP request and base URL is default/localhost/blank,
     *       the scheme, host, port, and context-path are dynamically inferred from the incoming
     *       request (respecting {@code X-Forwarded-*} headers when behind a reverse proxy).</li>
     *   <li>Otherwise, falls back to the configured base URL or {@code http://localhost:<port>}.</li>
     * </ol>
     *
     * <p>Trailing slashes are trimmed, servlet context-path is preserved without duplication,
     * and the token parameter is UTF-8 URL encoded. The token is never logged.
     *
     * @param token the opaque verification token
     * @return fully-qualified verification URL
     */
    public String buildVerificationUrl(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token cannot be null or blank");
        }

        String base = resolveBaseUrl();

        // Strip any trailing slashes from the base URL
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        // Ensure servlet context-path is preserved if configured and not already included in base
        if (contextPath != null && !contextPath.isBlank() && !"/".equals(contextPath.trim())) {
            String cleanContext = contextPath.trim();
            if (!cleanContext.startsWith("/")) {
                cleanContext = "/" + cleanContext;
            }
            while (cleanContext.endsWith("/")) {
                cleanContext = cleanContext.substring(0, cleanContext.length() - 1);
            }
            if (!base.endsWith(cleanContext)) {
                base = base + cleanContext;
            }
        }

        String encodedToken = URLEncoder.encode(token.trim(), StandardCharsets.UTF_8);
        return base + "/verify-email?token=" + encodedToken;
    }

    private String resolveBaseUrl() {
        // 1. If base-url is configured with an explicit non-localhost domain (production/staging), prioritize it.
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            String trimmed = configuredBaseUrl.trim();
            if (!trimmed.toLowerCase().contains("localhost") && !trimmed.contains("127.0.0.1")) {
                return trimmed;
            }
        }

        // 2. If Railway provides RAILWAY_PUBLIC_DOMAIN or RAILWAY_STATIC_URL, use https://<domain>
        if (railwayPublicDomain != null && !railwayPublicDomain.isBlank()) {
            String domain = railwayPublicDomain.trim();
            return domain.startsWith("http://") || domain.startsWith("https://") ? domain : "https://" + domain;
        }
        if (railwayStaticUrl != null && !railwayStaticUrl.isBlank()) {
            String domain = railwayStaticUrl.trim();
            return domain.startsWith("http://") || domain.startsWith("https://") ? domain : "https://" + domain;
        }

        // 3. If an active HTTP request exists, dynamically infer scheme, host, port, and context from it
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null && attributes.getRequest() != null) {
                String requestBase = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
                if (requestBase != null && !requestBase.isBlank() && !requestBase.toLowerCase().contains("localhost")) {
                    return requestBase.trim();
                }
            }
        } catch (Exception ignored) {
            // Outside of a Servlet request context (e.g. background job, CLI, tests)
        }

        // 4. Fallback to configured base URL if present
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.trim();
        }

        // 5. Default local development URL
        String port = (serverPort != null && !serverPort.isBlank()) ? serverPort.trim() : "8080";
        return "http://localhost:" + port;
    }

    // Public setters for testing without full Spring injection
    public void setConfiguredBaseUrl(String configuredBaseUrl) {
        this.configuredBaseUrl = configuredBaseUrl;
    }

    public void setRailwayPublicDomain(String railwayPublicDomain) {
        this.railwayPublicDomain = railwayPublicDomain;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * Sends a college-email verification message containing a single-use link.
     *
     * @param toEmail the recipient's @pvppcoe.ac.in address
     * @param token   the UUID verification token
     * @throws RuntimeException wrapping any {@link IOException} or
     *                          {@link MessagingException} from the Gmail API
     */
    public void sendVerificationEmail(String toEmail, String token) {
        log.info("[EMAIL] sendVerificationEmail → to={}, from={}", toEmail, fromAddress);

        String verificationLink = buildVerificationUrl(token);

        String subject = "BookFlow — Verify your college email";
        String body = "Hello,\n\n"
                + "Thank you for registering with BookFlow.\n\n"
                + "Please click the link below to verify your college email address "
                + "and activate your account:\n\n"
                + verificationLink + "\n\n"
                + "This link expires in 24 hours and can only be used once.\n\n"
                + "If you did not register with BookFlow, please ignore this email.\n\n"
                + "— The BookFlow Team\n"
                + "PVPP College of Engineering";

        try {
            Message gmailMessage = buildGmailMessage(fromAddress, toEmail, subject, body);

            log.info("[EMAIL] Submitting message to Gmail API (users.messages.send)");
            Gmail service = gmailServiceFactory.getGmailService();
            // "me" is a special alias meaning the authenticated user's mailbox.
            Message sent = service.users().messages().send("me", gmailMessage).execute();
            log.info("[EMAIL] Gmail API accepted message — id={}", sent.getId());

        } catch (MessagingException e) {
            log.error("[EMAIL] Failed to build MIME message: {}", e.getMessage());
            throw new RuntimeException("Failed to build verification email", e);
        } catch (IOException e) {
            log.error("[EMAIL] Gmail API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to send verification email via Gmail API", e);
        }
    }

    /**
     * Builds a base64url-encoded {@link Message} suitable for the Gmail API.
     *
     * Uses the Jakarta Mail API only for constructing a standards-compliant
     * MIME message in memory — no SMTP connection is opened.
     */
    private Message buildGmailMessage(String from, String to, String subject, String bodyText)
            throws MessagingException, IOException {

        // A null-properties Session is enough for building an in-memory MIME message.
        Session session = Session.getInstance(new Properties());

        MimeMessage mime = new MimeMessage(session);
        mime.setFrom(new InternetAddress(from));
        mime.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        mime.setSubject(subject, "UTF-8");
        mime.setSentDate(new Date());
        mime.setText(bodyText, "UTF-8");

        // Serialize the MIME message to bytes, then encode as base64url (no padding).
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mime.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(buffer.toByteArray());

        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);
        return gmailMessage;
    }
}
