package com.bookflow.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Builds and caches an authorized {@link Gmail} service instance using
 * the OAuth 2.0 "installed application" flow.
 *
 * <p>Credential handling:
 * <ul>
 *   <li>The OAuth client JSON ({@code secrets/google-oauth-client.json}) is
 *       read once at startup — never logged or exposed.</li>
 *   <li>After the first browser authorization the resulting refresh/access
 *       token is saved to {@code secrets/tokens/} by {@link FileDataStoreFactory}.
 *       Subsequent application restarts reuse the stored token automatically.</li>
 *   <li>The {@code secrets/} directory is excluded from Git via its own
 *       {@code .gitignore}.</li>
 * </ul>
 *
 * <p>First-run flow: when no stored token exists the factory launches a
 * local Jetty server on an ephemeral port and prints an authorization URL
 * to the console. The user opens the URL in a browser, grants access, and
 * the authorization code is exchanged for tokens that are immediately
 * persisted to disk.
 */
@Component
public class GmailServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(GmailServiceFactory.class);

    /** Scope required to send email on behalf of the authenticated user. */
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_SEND);

    private static final String APPLICATION_NAME = "BookFlow";
    private static final GsonFactory JSON_FACTORY  = GsonFactory.getDefaultInstance();

    @Value("${bookflow.gmail.credentials-file}")
    private String credentialsFilePath;

    @Value("${bookflow.gmail.tokens-dir}")
    private String tokensDirPath;

    private Gmail gmailService;

    /**
     * Initializes the Gmail service on application startup.
     * If no stored token exists this will block until the user completes
     * the browser authorization — which is expected on first run.
     */
    @PostConstruct
    public void init() throws IOException, GeneralSecurityException {
        log.info("[GMAIL] Initializing Gmail API client (credentials={})", credentialsFilePath);

        File credentialsFile = new File(credentialsFilePath);
        if (!credentialsFile.exists()) {
            throw new IllegalStateException(
                    "Google OAuth credentials file not found: " + credentialsFilePath
                    + " — place the JSON downloaded from Google Cloud Console at that path.");
        }

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Load client secrets from the downloaded JSON — credentials are never printed.
        GoogleClientSecrets clientSecrets;
        try (FileReader reader = new FileReader(credentialsFile)) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        }

        // Tokens are persisted in the configured directory so subsequent
        // startups skip the browser flow.
        File tokensDir = new File(tokensDirPath);
        tokensDir.mkdirs();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokensDir))
                .setAccessType("offline")   // request a refresh token
                .build();

        // LocalServerReceiver starts a Jetty server on an available port and
        // handles the OAuth callback automatically.
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(-1)    // -1 means pick any available port
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");

        // Credential object holds access + refresh tokens. The token value is
        // intentionally NOT logged.
        log.info("[GMAIL] OAuth credential obtained. Token will expire at: {}",
                credential.getExpirationTimeMilliseconds());

        gmailService = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        log.info("[GMAIL] Gmail service ready.");
    }

    /**
     * Returns the authorized {@link Gmail} service instance.
     * Call sites must NOT cache this reference across requests — always
     * retrieve it from the factory so token refresh is handled transparently.
     */
    public Gmail getGmailService() {
        return gmailService;
    }
}
