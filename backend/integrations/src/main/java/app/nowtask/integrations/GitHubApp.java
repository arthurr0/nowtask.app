package app.nowtask.integrations;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class GitHubApp {

    private static final int CLOCK_SKEW_SECONDS = 60;
    private static final int JWT_LIFETIME_SECONDS = 540;

    private final String appId;
    private final String privateKeyPem;
    private final String webhookSecret;
    private final String clientId;
    private final String clientSecret;
    private final String slug;

    private volatile PrivateKey privateKey;

    GitHubApp(
            @Value("${nowtask.github.app-id:}") String appId,
            @Value("${nowtask.github.private-key:}") String privateKeyPem,
            @Value("${nowtask.github.webhook-secret:}") String webhookSecret,
            @Value("${nowtask.github.client-id:}") String clientId,
            @Value("${nowtask.github.client-secret:}") String clientSecret,
            @Value("${nowtask.github.slug:}") String slug) {
        this.appId = appId.trim();
        this.privateKeyPem = privateKeyPem.trim();
        this.webhookSecret = webhookSecret.trim();
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.slug = slug.trim();
    }

    boolean configured() {
        return !appId.isBlank() && !privateKeyPem.isBlank() && !webhookSecret.isBlank();
    }

    String webhookSecret() {
        return webhookSecret;
    }

    String clientId() {
        return clientId;
    }

    String clientSecret() {
        return clientSecret;
    }

    String slug() {
        return slug;
    }

    String installUrl() {
        return slug.isBlank() ? "" : "https://github.com/apps/" + slug + "/installations/new";
    }

    String jwt() {
        if (!configured()) {
            throw new IllegalStateException("The GitHub App is not configured on this instance");
        }

        long now = Instant.now().getEpochSecond();
        String header = GitHubKeys.base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = GitHubKeys.base64Url("{\"iat\":" + (now - CLOCK_SKEW_SECONDS)
                + ",\"exp\":" + (now + JWT_LIFETIME_SECONDS)
                + ",\"iss\":\"" + appId + "\"}");

        String signingInput = header + "." + payload;
        return signingInput + "." + GitHubKeys.base64Url(sign(signingInput));
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("The GitHub App token cannot be signed: " + e.getMessage(), e);
        }
    }

    private PrivateKey key() {
        PrivateKey resolved = privateKey;
        if (resolved == null) {
            synchronized (this) {
                resolved = privateKey;
                if (resolved == null) {
                    resolved = GitHubKeys.privateKey(privateKeyPem);
                    privateKey = resolved;
                }
            }
        }
        return resolved;
    }
}
