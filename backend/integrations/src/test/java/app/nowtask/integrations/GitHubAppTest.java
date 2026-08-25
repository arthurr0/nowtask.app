package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GitHubAppTest {

    private static KeyPair pair;

    @BeforeAll
    static void generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
    }

    @Test
    void countsAsConfiguredOnlyWithAnIdKeyAndSecret() {
        assertThat(app("", key(), "secret").configured()).isFalse();
        assertThat(app("12345", "", "secret").configured()).isFalse();
        assertThat(app("12345", key(), "").configured()).isFalse();
        assertThat(app("12345", key(), "secret").configured()).isTrue();
    }

    @Test
    void signsATokenThePublicKeyVerifies() throws Exception {
        String token = app("12345", key(), "secret").jwt();
        String[] parts = token.split("\\.");

        assertThat(parts).hasSize(3);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(pair.getPublic());
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));

        assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void statesTheAppAsIssuerAndStaysWithinTenMinutes() {
        String token = app("12345", key(), "secret").jwt();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(payload).contains("\"iss\":\"12345\"");

        long issuedAt = Long.parseLong(payload.replaceAll(".*\"iat\":(\\d+).*", "$1"));
        long expiresAt = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));
        long now = Instant.now().getEpochSecond();

        assertThat(issuedAt).isLessThanOrEqualTo(now);
        assertThat(expiresAt - issuedAt).isLessThanOrEqualTo(600);
        assertThat(expiresAt).isGreaterThan(now);
    }

    @Test
    void refusesToSignWhenTheInstanceHasNoApp() {
        assertThatThrownBy(() -> app("", "", "").jwt())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void buildsTheInstallUrlOnlyFromASlug() {
        assertThat(app("1", key(), "s", "nowtask").installUrl())
                .isEqualTo("https://github.com/apps/nowtask/installations/new");
        assertThat(app("1", key(), "s", "").installUrl()).isEmpty();
    }

    private static GitHubApp app(String appId, String privateKey, String secret) {
        return app(appId, privateKey, secret, "nowtask");
    }

    private static GitHubApp app(String appId, String privateKey, String secret, String slug) {
        return new GitHubApp(appId, privateKey, secret, "client", "client-secret", slug);
    }

    private static String key() {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }
}
