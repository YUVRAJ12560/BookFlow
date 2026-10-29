package com.bookflow.config;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Resilient factory for the Google Gmail API service.
 *
 * <p>Supports:
 * <ul>
 *   <li>Railway / Cloud production mode via environment variables:
 *       {@code GOOGLE_OAUTH_CLIENT_JSON} (or {@code GOOGLE_OAUTH_CLIENT_ID} + {@code GOOGLE_OAUTH_CLIENT_SECRET})
 *       and {@code GOOGLE_OAUTH_REFRESH_TOKEN}.</li>
 *   <li>Local development mode via credentials file and stored tokens.</li>
 * </ul>
 *
 * <p><b>Lifecycle Resilience:</b> Startup is never aborted if Google OAuth token refresh
 * fails (e.g. invalid_grant, network timeout, or missing env vars). The bean initializes
 * gracefully in a degraded state and logs clear, actionable instructions without printing
 * any secret tokens. Email operations safely check availability at invocation time.
 */
@Component
public class GmailServiceFactory {

    private static final Logger log =
            LoggerFactory.getLogger(GmailServiceFactory.class);

    private static final List<String> SCOPES =
            List.of(GmailScopes.GMAIL_SEND);

    private static final String APPLICATION_NAME = "BookFlow";

    private static final GsonFactory JSON_FACTORY =
            GsonFactory.getDefaultInstance();

    @Value("${bookflow.gmail.credentials-file:secrets/client_secret_505281786758-fsblvfsurompm2rd7j77h4htir0aqb56.apps.googleusercontent.com.json}")
    private String credentialsFilePath;

    @Value("${bookflow.gmail.tokens-dir:secrets/tokens}")
    private String tokensDirPath;

    @Value("${GOOGLE_OAUTH_CLIENT_JSON:#{null}}")
    private String credentialsJson;

    @Value("${GOOGLE_OAUTH_REFRESH_TOKEN:#{null}}")
    private String refreshToken;

    @Value("${GOOGLE_OAUTH_CLIENT_ID:#{null}}")
    private String clientId;

    @Value("${GOOGLE_OAUTH_CLIENT_SECRET:#{null}}")
    private String clientSecret;

    private volatile Gmail gmailService;
    private volatile boolean available = false;
    private volatile String initializationError = null;

    @PostConstruct
    public void init() {
        log.info("[GMAIL] Initializing Gmail API service factory...");
        try {
            initializeGmailService();
        } catch (Exception e) {
            this.available = false;
            this.initializationError = "Unexpected initialization error: " + e.getMessage();
            log.error("[GMAIL] Unexpected error initializing Gmail client. Application startup will continue, "
                    + "but email delivery is disabled. Error: {}", e.getMessage());
        }
    }

    /**
     * Attempts to initialize the Gmail API client.
     * Guaranteed not to throw fatal exceptions during bean creation.
     */
    public synchronized void initializeGmailService() {
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // 1. Resolve environment-based credentials (Railway / Production)
            String effectiveJson = (credentialsJson != null && !credentialsJson.isBlank())
                    ? credentialsJson.trim()
                    : System.getenv("GOOGLE_OAUTH_CLIENT_JSON");

            String effectiveRefreshToken = (refreshToken != null && !refreshToken.isBlank())
                    ? refreshToken.trim()
                    : System.getenv("GOOGLE_OAUTH_REFRESH_TOKEN");

            if (effectiveRefreshToken != null) {
                effectiveRefreshToken = effectiveRefreshToken.trim();
                if ((effectiveRefreshToken.startsWith("\"") && effectiveRefreshToken.endsWith("\"")) ||
                    (effectiveRefreshToken.startsWith("'") && effectiveRefreshToken.endsWith("'"))) {
                    effectiveRefreshToken = effectiveRefreshToken.substring(1, effectiveRefreshToken.length() - 1).trim();
                }
            }

            String effectiveClientId = (clientId != null && !clientId.isBlank())
                    ? clientId.trim()
                    : System.getenv("GOOGLE_OAUTH_CLIENT_ID");

            String effectiveClientSecret = (clientSecret != null && !clientSecret.isBlank())
                    ? clientSecret.trim()
                    : System.getenv("GOOGLE_OAUTH_CLIENT_SECRET");

            // Extract client ID and secret from JSON if direct env vars are absent
            if ((effectiveClientId == null || effectiveClientSecret == null) && effectiveJson != null && !effectiveJson.isBlank()) {
                try {
                    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                            JSON_FACTORY, new StringReader(effectiveJson));
                    if (clientSecrets.getDetails() != null) {
                        if (effectiveClientId == null) {
                            effectiveClientId = clientSecrets.getDetails().getClientId();
                        }
                        if (effectiveClientSecret == null) {
                            effectiveClientSecret = clientSecrets.getDetails().getClientSecret();
                        }
                    }
                } catch (Exception e) {
                    log.warn("[GMAIL] Could not parse GOOGLE_OAUTH_CLIENT_JSON: {}", e.getMessage());
                }
            }

            /*
             * Case A: Environment credentials provided (Railway / Production)
             */
            if (effectiveClientId != null && !effectiveClientId.isBlank()
                    && effectiveClientSecret != null && !effectiveClientSecret.isBlank()
                    && effectiveRefreshToken != null && !effectiveRefreshToken.isBlank()) {

                log.info("[GMAIL] Environment OAuth credentials detected. Verifying token with Google...");

                Credential credential = new Credential.Builder(
                        BearerToken.authorizationHeaderAccessMethod())
                        .setTransport(httpTransport)
                        .setJsonFactory(JSON_FACTORY)
                        .setTokenServerUrl(new GenericUrl(GoogleOAuthConstants.TOKEN_SERVER_URL))
                        .setClientAuthentication(new ClientParametersAuthentication(
                                effectiveClientId, effectiveClientSecret))
                        .build();

                credential.setRefreshToken(effectiveRefreshToken);

                try {
                    // Attempt token refresh
                    credential.refreshToken();

                    this.gmailService = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                            .setApplicationName(APPLICATION_NAME)
                            .build();
                    this.available = true;
                    this.initializationError = null;
                    log.info("[GMAIL] Gmail service initialized successfully and verified via OAuth token refresh");
                    return;

                } catch (TokenResponseException e) {
                    this.available = false;
                    this.gmailService = null;
                    String error = (e.getDetails() != null && e.getDetails().getError() != null)
                            ? e.getDetails().getError() : "HTTP " + e.getStatusCode();
                    String desc = (e.getDetails() != null && e.getDetails().getErrorDescription() != null)
                            ? e.getDetails().getErrorDescription() : e.getMessage();

                    this.initializationError = "Google OAuth token refresh rejected: " + error + " (" + desc + ")";

                    log.error("[GMAIL] Google OAuth authentication rejected [{}]: {}. "
                            + "The application is running, but email delivery is disabled. "
                            + "Remedy: (1) Refresh tokens in Google Cloud 'Testing' mode expire after 7 days; "
                            + "(2) Ensure GOOGLE_OAUTH_REFRESH_TOKEN was issued for the same Client ID; "
                            + "(3) Update GOOGLE_OAUTH_REFRESH_TOKEN in Railway Dashboard.",
                            error, desc);
                    return;

                } catch (Exception e) {
                    this.available = false;
                    this.gmailService = null;
                    this.initializationError = "Network error connecting to Google OAuth endpoint: " + e.getMessage();
                    log.error("[GMAIL] Could not reach Google token server: {}. Application starting with email disabled.", e.getMessage());
                    return;
                }
            }

            /*
             * Case B: Local development mode fallback (credentials file on disk)
             */
            if (credentialsFilePath != null) {
                File credentialsFile = new File(credentialsFilePath);
                if (credentialsFile.exists()) {
                    log.info("[GMAIL] Using local OAuth credentials file: {}", credentialsFilePath);
                    try (FileReader reader = new FileReader(credentialsFile)) {
                        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
                        File tokensDir = new File(tokensDirPath != null ? tokensDirPath : "secrets/tokens");
                        if (!tokensDir.exists() && !tokensDir.mkdirs()) {
                            this.available = false;
                            this.initializationError = "Unable to create Gmail tokens directory: " + tokensDir.getAbsolutePath();
                            log.warn("[GMAIL] {}", this.initializationError);
                            return;
                        }

                        com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow flow =
                                new com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder(
                                        httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                                        .setAccessType("offline")
                                        .setDataStoreFactory(new com.google.api.client.util.store.FileDataStoreFactory(tokensDir))
                                        .build();

                        Credential credential = flow.loadCredential("user");
                        if (credential != null && credential.getRefreshToken() != null) {
                            try {
                                credential.refreshToken();
                                this.gmailService = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                                        .setApplicationName(APPLICATION_NAME)
                                        .build();
                                this.available = true;
                                this.initializationError = null;
                                log.info("[GMAIL] Gmail service ready using saved local credential in {}", tokensDirPath);
                                return;
                            } catch (TokenResponseException e) {
                                this.available = false;
                                this.initializationError = "Saved local token expired or invalid: " + e.getMessage();
                                log.warn("[GMAIL] Saved local token refresh failed: {}", e.getMessage());
                            }
                        }

                        // If headless or non-interactive, do not block startup with LocalServerReceiver
                        if (java.awt.GraphicsEnvironment.isHeadless() || System.console() == null) {
                            this.available = false;
                            this.initializationError = "Local interactive authorization required but environment is headless.";
                            log.warn("[GMAIL] {}", this.initializationError);
                            return;
                        }

                        com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp authorizationApp =
                                new com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(
                                        flow,
                                        new com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver.Builder()
                                                .setPort(-1)
                                                .build());

                        Credential authorizedCredential = authorizationApp.authorize("user");
                        this.gmailService = new Gmail.Builder(httpTransport, JSON_FACTORY, authorizedCredential)
                                .setApplicationName(APPLICATION_NAME)
                                .build();
                        this.available = true;
                        this.initializationError = null;
                        log.info("[GMAIL] Gmail service ready using local OAuth flow");
                        return;

                    } catch (Exception e) {
                        this.available = false;
                        this.initializationError = "Local OAuth initialization error: " + e.getMessage();
                        log.warn("[GMAIL] Could not initialize local OAuth flow: {}", e.getMessage());
                        return;
                    }
                }
            }

            // Neither valid environment credentials nor local file found
            this.available = false;
            this.initializationError = "No Google OAuth credentials configured. "
                    + "Set GOOGLE_OAUTH_CLIENT_JSON and GOOGLE_OAUTH_REFRESH_TOKEN in environment.";
            log.warn("[GMAIL] {}", this.initializationError);

        } catch (Exception e) {
            this.available = false;
            this.initializationError = "Unexpected failure: " + e.getMessage();
            log.error("[GMAIL] Failed to initialize GmailServiceFactory: {}", e.getMessage());
        }
    }

    /**
     * Returns the initialized Gmail service client.
     *
     * @return active {@link Gmail} service instance
     * @throws IllegalStateException if the service is not currently available
     */
    public Gmail getGmailService() {
        if (!isAvailable()) {
            // Attempt one retry in case credentials were updated or network restored
            initializeGmailService();
        }

        if (!isAvailable()) {
            String error = (initializationError != null)
                    ? initializationError
                    : "Gmail service is not available (unconfigured)";
            throw new IllegalStateException("Gmail service is unavailable: " + error);
        }

        return gmailService;
    }

    /**
     * Checks whether the Gmail service is authenticated and ready to send messages.
     *
     * @return true if Gmail client is operational, false otherwise
     */
    public boolean isAvailable() {
        return available && gmailService != null;
    }

    /**
     * Returns the last diagnostic error message if initialization failed.
     *
     * @return error description or null if healthy
     */
    public String getInitializationError() {
        return initializationError;
    }

    // Setters for unit testing and programmatic configuration
    public void setGmailService(Gmail gmailService) {
        this.gmailService = gmailService;
        this.available = (gmailService != null);
        if (this.available) {
            this.initializationError = null;
        }
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setInitializationError(String initializationError) {
        this.initializationError = initializationError;
    }

    public void setCredentialsJson(String credentialsJson) {
        this.credentialsJson = credentialsJson;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setCredentialsFilePath(String credentialsFilePath) {
        this.credentialsFilePath = credentialsFilePath;
    }

    public void setTokensDirPath(String tokensDirPath) {
        this.tokensDirPath = tokensDirPath;
    }
}