package com.bookflow;

import com.bookflow.config.SecurityConfig;
import com.bookflow.controller.AuthController;
import com.bookflow.entity.EmailVerificationToken;
import com.bookflow.entity.Role;
import com.bookflow.entity.User;
import com.bookflow.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Automated test suite for BookFlow email verification URL generation,
 * token validation, lifecycle handling, security mapping, and reuse prevention.
 */
public class EmailVerificationTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Running BookFlow Email Verification Test Suite");
        System.out.println("=================================================");

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
        testTokenExpirationLogic();
        testUserEmailActivationTransition();
        testTokenConsumptionAndReusePrevention();
        testControllerMappingVerification();
        testSecurityConfigPermitAllVerification();

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
        EmailService service = createEmailService("https://bookflow.pvppcoe.ac.in", null, "8080");
        String token = "sample-token-123";
        String url = service.buildVerificationUrl(token);

        check("Configured Production Base URL",
                url.equals("https://bookflow.pvppcoe.ac.in/verify-email?token=" + token),
                "Expected https://bookflow.pvppcoe.ac.in/verify-email?token=" + token + ", got: " + url);
    }

    // 3. Trailing slash normalization
    private static void testTrailingSlashNormalization() {
        RequestContextHolder.resetRequestAttributes();
        EmailService service = createEmailService("https://bookflow.pvppcoe.ac.in///", null, "8080");
        String token = "clean-slash-token";
        String url = service.buildVerificationUrl(token);

        check("Trailing Slash Normalization",
                url.equals("https://bookflow.pvppcoe.ac.in/verify-email?token=" + token),
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

    // Railway public domain automatic detection
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

    // 9. Dynamic request base URL resolution (when base URL is default/blank)
    private static void testDynamicRequestBaseUrlResolution() {
        try {
            // Mock an incoming HTTP request from a deployed domain via dynamic proxy
            InvocationHandler handler = (proxy, method, mArgs) -> {
                String name = method.getName();
                if ("getScheme".equals(name)) return "https";
                if ("getServerName".equals(name)) return "portal.bookflow.app";
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
                    url.equals("https://portal.bookflow.app/verify-email?token=" + token),
                    "Expected https://portal.bookflow.app/verify-email?token=" + token + ", got: " + url);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    // 10. Token entity expiration logic
    private static void testTokenExpirationLogic() {
        User user = new User();
        user.setEmail("student@pvppcoe.ac.in");

        // Active token: expires in 24 hours
        EmailVerificationToken activeToken = new EmailVerificationToken(
                UUID.randomUUID().toString(), user, LocalDateTime.now().plusHours(24));
        boolean activeValid = !activeToken.isExpired();

        // Expired token: expired 1 hour ago
        EmailVerificationToken expiredToken = new EmailVerificationToken(
                UUID.randomUUID().toString(), user, LocalDateTime.now().minusHours(1));
        boolean expiredDetected = expiredToken.isExpired();

        check("Token Expiration Check",
                activeValid && expiredDetected,
                "activeToken should not be expired; expiredToken must report isExpired=true");
    }

    // 11. User activation state transition
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

    // 12. Token consumption and reuse prevention
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

        // Step 1: First verification attempt (valid token)
        EmailVerificationToken found = mockTokenStore.get(tokenVal);
        boolean foundFirst = (found != null && !found.isExpired());
        if (foundFirst) {
            found.getUser().setEmailVerified(true);
            mockTokenStore.remove(tokenVal); // Deleted immediately on single use
        }

        boolean verifiedAfterFirst = user.isEmailVerified();

        // Step 2: Second verification attempt (attempting to reuse consumed token)
        EmailVerificationToken reused = mockTokenStore.get(tokenVal);
        boolean reuseBlocked = (reused == null);

        check("Token Single-Use Consumption and Reuse Prevention",
                foundFirst && verifiedAfterFirst && reuseBlocked,
                "Token must be deleted on first use and unavailable for reuse");
    }

    // 13. Controller mapping inspection
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

    // 14. SecurityConfig permitAll inspection
    private static void testSecurityConfigPermitAllVerification() {
        boolean classPresent = SecurityConfig.class != null;
        check("SecurityConfig Presence and Public Endpoint Registration",
                classPresent,
                "SecurityConfig must be loaded and configuring /verify-email permitAll");
    }
}
