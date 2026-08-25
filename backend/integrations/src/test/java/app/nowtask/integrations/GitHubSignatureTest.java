package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class GitHubSignatureTest {

    private static final String SECRET = "It's a Secret to Everybody";
    private static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsTheSignatureGitHubDocuments() {
        assertThat(GitHubSignature.matches(SECRET, BODY,
                "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17"))
                .isTrue();
    }

    @Test
    void rejectsATamperedBody() {
        assertThat(GitHubSignature.matches(SECRET, "Hello, world!".getBytes(StandardCharsets.UTF_8),
                "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17"))
                .isFalse();
    }

    @Test
    void rejectsAMissingOrMalformedHeader() {
        assertThat(GitHubSignature.matches(SECRET, BODY, null)).isFalse();
        assertThat(GitHubSignature.matches(SECRET, BODY, "")).isFalse();
        assertThat(GitHubSignature.matches(SECRET, BODY, "sha1=" + hmac(SECRET, BODY))).isFalse();
        assertThat(GitHubSignature.matches(SECRET, BODY, hmac(SECRET, BODY))).isFalse();
    }

    @Test
    void rejectsEverythingWhenNoSecretIsConfigured() {
        String signature = "sha256=" + hmac("x", BODY);
        assertThat(GitHubSignature.matches("", BODY, signature)).isFalse();
        assertThat(GitHubSignature.matches(null, BODY, signature)).isFalse();
    }

    @Test
    void acceptsASignatureItComputesItself() {
        assertThat(GitHubSignature.matches("topsecret", BODY, "sha256=" + hmac("topsecret", BODY))).isTrue();
    }

    private static String hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
