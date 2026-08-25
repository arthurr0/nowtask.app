package app.nowtask.integrations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class GitHubSignature {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private GitHubSignature() {
    }

    static boolean matches(String secret, byte[] body, String header) {
        if (secret == null || secret.isBlank() || header == null || !header.startsWith(PREFIX)) {
            return false;
        }

        byte[] expected = (PREFIX + hex(secret, body)).getBytes(StandardCharsets.UTF_8);
        byte[] actual = header.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("The GitHub signature cannot be computed: " + e.getMessage(), e);
        }
    }
}
