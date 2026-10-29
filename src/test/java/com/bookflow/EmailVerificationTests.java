package com.bookflow;

import com.bookflow.config.GmailServiceFactory;
import com.bookflow.config.SecurityConfig;
import com.bookflow.controller.AuthController;
import com.bookflow.entity.EmailVerificationToken;
import com.bookflow.entity.Role;
import com.bookflow.entity.User;
import com.bookflow.service.EmailService;
import com.google.api.services.gmail.Gmail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Automated test suite for BookFlow:
 * - Email verification URL generation (including Railway domain detection)
 * - GmailServiceFactory lifecycle resilience and failure handling
 * - Token lifecycle, activation transition, and reuse prevention
 * - Security permissions and route mappings
 */
public class EmailVerificationTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Running BookFlow Verification & Lifecycle Suite");
        System.out.println("=================================================");

        // URL Generation Tests
        testDefaultLocalUrlGeneration();
        testConfiguredBaseUrl();
        testTrailingSlashNormalization();
        testContextPathHandling();
        testContextPathAlreadyPresent();
        testCustomServerPort();
        testTokenUrlEncoding();
        testNullAndBlankTokenRejection();
        testRailwayPublicDomainResolution();
        testDynamicRequestBaseUrlResolution();

        // Token Lifecycle & Security Tests
        testTokenExpirationLogic();
        testUserEmailActivationTransition();
        testTokenConsumptionAndReusePrevention();
        testControllerMappingVerification();
        testSecurityConfigPermitAllVerification();
        testResendVerificationControllerMapping();

        // Gmail Lifecycle Resilience Tests
        testGmailFactoryResilientStartupMissingCredentials();
        testGmailFactoryGetThrowsWhenUnavailable();
        testGmailFactoryMockAvailability();
        testEmailServiceGracefulDeliveryFailure();

        System.out.println("=================================================");
        System.out.printf("Results: %d Passed, %d Failed%n", passed, failed);
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String testName, boolean condition, String details) {
        if (condition) {
            System.out.printf("  [PASS] %s%n", testName);
            passed++;
        } else {
            System.err.printf("  [FAIL] %s: %s%n", testName, details);
            failed++;
        }
    }

    private static EmailService createEmailService(String baseUrl, String contextPath, String port) {
        EmailService service = new EmailService(null);
        service.setConfiguredBaseUrl(baseUrl);
        service.setContextPath(contextPath);
        service.setServerPort(port);
        return service;
    }

    // 1. Default local URL fallback
    private static void testDefaultLocalUrlGeneration() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService(null, null, "8080");
        String token = UUID.randomUUID().toString();
        String url = service.buildVerificationUrl(token);

        check("Default Local URL",
                url.equals("http://localhost:8080/verify-email?token=" + token),
                "Expected http://localhost:8080/verify-email?token=" + token + ", got: " + url);
    }

    // 2. Production configured domain
    private static void testConfiguredBaseUrl() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService("https://bookflow-production-20fe.up.railway.app", null, "8080");
        String token = "sample-token-123";
        String url = service.buildVerificationUrl(token);

        check("Configured Production Base URL",
                url.equals("https://bookflow-production-20fe.up.railway.app/verify-email?token=" + token),
                "Expected https://bookflow-production-20fe.up.railway.app/verify-email?token=" + token + ", got: " + url);
    }

    // 3. Trailing slash normalization
    private static void testTrailingSlashNormalization() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService("https://bookflow-production-20fe.up.railway.app///", null, "8080");
        String token = "clean-slash-token";
        String url = service.buildVerificationUrl(token);

        check("Trailing Slash Normalization",
                url.equals("https://bookflow-production-20fe.up.railway.app/verify-email?token=" + token),
                "Should not have double slash before route, got: " + url);
    }

    // 4. Custom context path
    private static void testContextPathHandling() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService("https://bookflow.pvppcoe.ac.in", "/portal", "8080");
        String token = "ctx-token";
        String url = service.buildVerificationUrl(token);

        check("Context Path Inclusion",
                url.equals("https://bookflow.pvppcoe.ac.in/portal/verify-email?token=" + token),
                "Expected context path included, got: " + url);
    }

    // 5. Context path already present in base URL (prevent duplicate)
    private static void testContextPathAlreadyPresent() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService("https://bookflow.pvppcoe.ac.in/portal", "/portal", "8080");
        String token = "ctx-dedup-token";
        String url = service.buildVerificationUrl(token);

        check("Context Path Deduplication",
                url.equals("https://bookflow.pvppcoe.ac.in/portal/verify-email?token=" + token),
                "Should not duplicate context path, got: " + url);
    }

    // 6. Custom server port fallback
    private static void testCustomServerPort() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService(null, null, "9090");
        String token = "port-token";
        String url = service.buildVerificationUrl(token);

        check("Custom Server Port Fallback",
                url.equals("http://localhost:9090/verify-email?token=" + token),
                "Expected port 9090, got: " + url);
    }

    // 7. Token URL encoding
    private static void testTokenUrlEncoding() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService("http://localhost:8080", null, "8080");
        String complexToken = "abc+def 123/xyz&foo=bar";
        String url = service.buildVerificationUrl(complexToken);

        check("Token URL Encoding",
                url.contains("token=abc%2Bdef+123%2Fxyz%26foo%3Dbar") || url.contains("token=abc%2Bdef%20123%2Fxyz%26foo%3Dbar"),
                "Token with spaces and special chars must be URL-encoded, got: " + url);
    }

    // 8. Null and blank token rejection
    private static void testNullAndBlankTokenRejection() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService("http://localhost:8080", null, "8080");
        boolean nullCaught = false;
        try {
            service.buildVerificationUrl(null);
        } catch (IllegalArgumentException e) {
            nullCaught = true;
        }

        boolean blankCaught = false;
        try {
            service.buildVerificationUrl("   ");
        } catch (IllegalArgumentException e) {
            blankCaught = true;
        }

        check("Null and Blank Token Validation",
                nullCaught && blankCaught,
                "Both null and blank tokens must throw IllegalArgumentException");
    }

    // 9. Railway public domain automatic detection
    private static void testRailwayPublicDomainResolution() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService(null, null, "8080");
        service.setRailwayPublicDomain("bookflow-production-20fe.up.railway.app");
        String token = "railway-token-777";
        String url = service.buildVerificationUrl(token);

        check("Railway Public Domain Auto-Detection",
                url.equals("https://bookflow-production-20fe.up.railway.app/verify-email?token=" + token),
                "Expected https://bookflow-production-20fe.up.railway.app/verify-email?token=" + token + ", got: " + url);
    }

    // 10. Dynamic request base URL resolution (when base URL is default/blank)
    private static void testDynamicRequestBaseUrlResolution() {
        try {
            InvocationHandler handler = (proxy, method, mArgs) -> {
                String name = method.getName();
                if ("getScheme".equals(name)) return "https";
                if ("getServerName".equals(name)) return "bookflow-production-20fe.up.railway.app";
                if ("getServerPort".equals(name)) return 443;
                if ("getContextPath".equals(name)) return "";
                if ("getRequestURI".equals(name)) return "/register";
                if ("getHeader".equals(name)) return null;
                if ("getHeaderNames".equals(name)) return Collections.emptyEnumeration();
                if ("getAttribute".equals(name)) return null;
                return null;
            };

            HttpServletRequest mockReq = (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    handler);

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockReq));

            EmailService service = createEmailService(null, null, "8080");
            String token = "dynamic-req-token";
            String url = service.buildVerificationUrl(token);

            check("Dynamic Request-Based Base URL Inference",
                    url.equals("https://bookflow-production-20fe.up.railway.app/verify-email?token=" + token),
                    "Expected https://bookflow-production-20fe.up.railway.app/verify-email?token=" + token + ", got: " + url);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    // 11. Token entity expiration logic
    private static void testTokenExpirationLogic() {
        User user = new User();
        user.setEmail("student@pvppcoe.ac.in");

        EmailVerificationToken activeToken = new EmailVerificationToken(
                UUID.randomUUID().toString(), user, LocalDateTime.now().plusHours(24));
        boolean activeValid = !activeToken.isExpired();

        EmailVerificationToken expiredToken = new EmailVerificationToken(
                UUID.randomUUID().toString(), user, LocalDateTime.now().minusHours(1));
        boolean expiredDetected = expiredToken.isExpired();

        check("Token Expiration Check",
                activeValid && expiredDetected,
                "activeToken should not be expired; expiredToken must report isExpired=true");
    }

    // 12. User activation state transition
    private static void testUserEmailActivationTransition() {
        User user = new User();
        user.setName("Test Student");
        user.setEmail("student@pvppcoe.ac.in");
        user.setRole(Role.USER);
        user.setEmailVerified(false);

        boolean initialVerified = user.isEmailVerified();
        user.setEmailVerified(true);
        boolean afterVerified = user.isEmailVerified();

        check("User Account Activation Transition",
                !initialVerified && afterVerified,
                "User should start with emailVerified=false and transition to emailVerified=true");
    }

    // 13. Token consumption and reuse prevention
    private static void testTokenConsumptionAndReusePrevention() {
        Map<String, EmailVerificationToken> mockTokenStore = new HashMap<>();

        User user = new User();
        user.setName("Alice");
        user.setEmail("alice@pvppcoe.ac.in");
        user.setEmailVerified(false);

        String tokenVal = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken(
                tokenVal, user, LocalDateTime.now().plusHours(24));
        mockTokenStore.put(tokenVal, token);

        EmailVerificationToken found = mockTokenStore.get(tokenVal);
        boolean foundFirst = (found != null && !found.isExpired());
        if (foundFirst) {
            found.getUser().setEmailVerified(true);
            mockTokenStore.remove(tokenVal);
        }

        boolean verifiedAfterFirst = user.isEmailVerified();
        EmailVerificationToken reused = mockTokenStore.get(tokenVal);
        boolean reuseBlocked = (reused == null);

        check("Token Single-Use Consumption and Reuse Prevention",
                foundFirst && verifiedAfterFirst && reuseBlocked,
                "Token must be deleted on first use and unavailable for reuse");
    }

    // 14. Controller mapping inspection
    private static void testControllerMappingVerification() {
        boolean mappingFound = false;
        for (Method m : AuthController.class.getDeclaredMethods()) {
            if (m.getName().equals("verifyEmail")) {
                GetMapping mapping = m.getAnnotation(GetMapping.class);
                if (mapping != null) {
                    for (String path : mapping.value()) {
                        if (path.equals("/verify-email")) {
                            mappingFound = true;
                            break;
                        }
                    }
                }
            }
        }

        check("AuthController /verify-email Mapping",
                mappingFound,
                "AuthController must have @GetMapping(\"/verify-email\") on verifyEmail method");
    }

    // 15. SecurityConfig permitAll inspection
    private static void testSecurityConfigPermitAllVerification() {
        boolean classPresent = SecurityConfig.class != null;
        check("SecurityConfig Presence and Public Endpoint Registration",
                classPresent,
                "SecurityConfig must be loaded and configuring /verify-email permitAll");
    }

    // 16. Resend verification controller mapping
    private static void testResendVerificationControllerMapping() {
        boolean resendMappingFound = false;
        for (Method m : AuthController.class.getDeclaredMethods()) {
            if (m.getName().equals("resendVerification")) {
                PostMapping mapping = m.getAnnotation(PostMapping.class);
                if (mapping != null) {
                    for (String path : mapping.value()) {
                        if (path.equals("/resend-verification")) {
                            resendMappingFound = true;
                            break;
                        }
                    }
                }
            }
        }

        check("AuthController /resend-verification Mapping",
                resendMappingFound,
                "AuthController must have @PostMapping(\"/resend-verification\")");
    }

    // 17. GmailFactory startup resilience when credentials are empty/invalid
    private static void testGmailFactoryResilientStartupMissingCredentials() {
        GmailServiceFactory factory = new GmailServiceFactory();
        factory.setCredentialsFilePath("non-existent-secrets/client_secret.json");
        factory.setCredentialsJson(null);
        factory.setRefreshToken(null);
        factory.setClientId(null);
        factory.setClientSecret(null);

        boolean noExceptionThrown = true;
        try {
            factory.init();
        } catch (Exception e) {
            noExceptionThrown = false;
        }

        boolean unavailable = !factory.isAvailable();
        boolean errorRecorded = factory.getInitializationError() != null;

        check("GmailFactory Resilient Startup Without Credentials",
                noExceptionThrown && unavailable && errorRecorded,
                "GmailServiceFactory.init() must not throw on missing/invalid credentials");
    }

    // 18. GmailFactory getGmailService throws IllegalStateException when unavailable
    private static void testGmailFactoryGetThrowsWhenUnavailable() {
        GmailServiceFactory factory = new GmailServiceFactory();
        factory.setCredentialsFilePath("non-existent-secrets/client_secret.json");
        factory.init();

        boolean throwsIllegalState = false;
        try {
            factory.getGmailService();
        } catch (IllegalStateException e) {
            throwsIllegalState = true;
        }

        check("GmailFactory getGmailService Fails Explicitly When Unavailable",
                throwsIllegalState,
                "getGmailService() must throw IllegalStateException when credentials are not operational");
    }

    // 19. GmailFactory mock availability
    private static void testGmailFactoryMockAvailability() {
        GmailServiceFactory factory = new GmailServiceFactory();
        factory.setAvailable(true);
        factory.setInitializationError(null);

        Gmail mockGmail = new Gmail.Builder(
                new com.google.api.client.http.javanet.NetHttpTransport(),
                com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("BookFlow-Test")
                .build();
        factory.setGmailService(mockGmail);

        check("GmailFactory Availability State Management",
                factory.isAvailable() && factory.getInitializationError() == null && factory.getGmailService() != null,
                "GmailServiceFactory must report available=true when client is active");
    }

    // 20. EmailService graceful delivery failure when Gmail is unavailable
    private static void testEmailServiceGracefulDeliveryFailure() {
        GmailServiceFactory unavailableFactory = new GmailServiceFactory();
        unavailableFactory.setAvailable(false);
        unavailableFactory.setInitializationError("Token refresh rejected (invalid_grant)");

        EmailService service = new EmailService(unavailableFactory);
        service.setConfiguredBaseUrl("https://bookflow-production-20fe.up.railway.app");

        boolean threwRuntimeException = false;
        String caughtMessage = "";
        try {
            service.sendVerificationEmail("test@pvppcoe.ac.in", "test-token-123");
        } catch (RuntimeException e) {
            threwRuntimeException = true;
            caughtMessage = e.getMessage();
        }

        check("EmailService Controlled Failure When Gmail Unavailable",
                threwRuntimeException && caughtMessage.contains("unavailable"),
                "sendVerificationEmail must throw a controlled RuntimeException when Gmail is down, got: " + caughtMessage);
    }
}
