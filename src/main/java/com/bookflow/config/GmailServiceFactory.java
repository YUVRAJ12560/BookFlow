package com.bookflow.config;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
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

@Component
public class GmailServiceFactory {

    private static final Logger log =
            LoggerFactory.getLogger(GmailServiceFactory.class);

    private static final List<String> SCOPES =
            List.of(GmailScopes.GMAIL_SEND);

    private static final String APPLICATION_NAME = "BookFlow";

    private static final GsonFactory JSON_FACTORY =
            GsonFactory.getDefaultInstance();

    @Value("${bookflow.gmail.credentials-file}")
    private String credentialsFilePath;

    @Value("${bookflow.gmail.tokens-dir}")
    private String tokensDirPath;

    private Gmail gmailService;

    @PostConstruct
    public void init() throws IOException, GeneralSecurityException {

        log.info("[GMAIL] Initializing Gmail API client");

        NetHttpTransport httpTransport =
                GoogleNetHttpTransport.newTrustedTransport();

        String credentialsJson =
                System.getenv("GOOGLE_OAUTH_CLIENT_JSON");

        String refreshToken =
                System.getenv("GOOGLE_OAUTH_REFRESH_TOKEN");

        /*
         * Railway / production mode.
         *
         * Uses credentials and refresh token supplied through environment
         * variables. No local browser authorization is required.
         */
        if (credentialsJson != null && !credentialsJson.isBlank()
                && refreshToken != null && !refreshToken.isBlank()) {

            log.info("[GMAIL] Using environment-based OAuth credentials");

            GoogleClientSecrets clientSecrets =
                    GoogleClientSecrets.load(
                            JSON_FACTORY,
                            new StringReader(credentialsJson)
                    );

            String clientId =
                    clientSecrets.getDetails().getClientId();

            String clientSecret =
                    clientSecrets.getDetails().getClientSecret();

            Credential credential = new Credential.Builder(
                    BearerToken.authorizationHeaderAccessMethod())
                    .setTransport(httpTransport)
                    .setJsonFactory(JSON_FACTORY)
                    .setTokenServerUrl(
                            new GenericUrl(GoogleOAuthConstants.TOKEN_SERVER_URL))
                    .setClientAuthentication(
                            new ClientParametersAuthentication(
                                    clientId,
                                    clientSecret))
                    .build();

            credential.setRefreshToken(refreshToken);

            // Force the first refresh so configuration problems fail clearly.
            credential.refreshToken();

            gmailService = new Gmail.Builder(
                    httpTransport,
                    JSON_FACTORY,
                    credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            log.info("[GMAIL] Gmail service ready using environment credentials");
            return;
        }

        /*
         * Local development mode.
         *
         * Preserves the existing browser-based OAuth flow.
         */
        log.info("[GMAIL] Using local OAuth credentials file: {}",
                credentialsFilePath);

        File credentialsFile = new File(credentialsFilePath);

        if (!credentialsFile.exists()) {
            throw new IllegalStateException(
                    "Google OAuth credentials file not found: "
                            + credentialsFilePath
                            + " — configure GOOGLE_OAUTH_CLIENT_JSON and "
                            + "GOOGLE_OAUTH_REFRESH_TOKEN for production."
            );
        }

        com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow flow;

        GoogleClientSecrets clientSecrets;

        try (FileReader reader = new FileReader(credentialsFile)) {
            clientSecrets =
                    GoogleClientSecrets.load(JSON_FACTORY, reader);
        }

        File tokensDir = new File(tokensDirPath);

        if (!tokensDir.exists() && !tokensDir.mkdirs()) {
            throw new IOException(
                    "Unable to create Gmail token directory: "
                            + tokensDir.getAbsolutePath());
        }

        flow =
                new com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
                        .Builder(
                                httpTransport,
                                JSON_FACTORY,
                                clientSecrets,
                                SCOPES)
                        .setAccessType("offline")
                        .setDataStoreFactory(
                                new com.google.api.client.util.store.FileDataStoreFactory(
                                        tokensDir))
                        .build();

        com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
                authorizationApp =
                new com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(
                        flow,
                        new com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
                                .Builder()
                                .setPort(-1)
                                .build());

        Credential credential =
                authorizationApp.authorize("user");

        gmailService =
                new Gmail.Builder(
                        httpTransport,
                        JSON_FACTORY,
                        credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();

        log.info("[GMAIL] Gmail service ready using local OAuth flow");
    }

    public Gmail getGmailService() {
        return gmailService;
    }
}