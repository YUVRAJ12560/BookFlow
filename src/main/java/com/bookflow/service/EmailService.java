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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    @Value("${bookflow.app.base-url}")
    private String baseUrl;

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

        String verificationLink = baseUrl + "/verify-email?token=" + token;

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
