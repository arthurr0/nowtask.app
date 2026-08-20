package app.nowtask.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import app.nowtask.identity.api.ApiKeyAuthenticator;

final class ApiKeyTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PREFIX_BYTES = 4;
    private static final int SECRET_BYTES = 32;

    private ApiKeyTokens() {
    }

    static String generate() {
        byte[] prefixBytes = new byte[PREFIX_BYTES];
        byte[] secretBytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(prefixBytes);
        RANDOM.nextBytes(secretBytes);
        return ApiKeyAuthenticator.TOKEN_PREFIX
                + HexFormat.of().formatHex(prefixBytes)
                + "_"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    }

    static String prefixOf(String token) {
        int separator = token.indexOf('_', ApiKeyAuthenticator.TOKEN_PREFIX.length());
        if (separator < 0) {
            return null;
        }
        return token.substring(0, separator);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    static boolean matches(String storedHash, String presentedToken) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hash(presentedToken).getBytes(StandardCharsets.UTF_8));
    }

    static boolean looksLikeApiKey(String value) {
        return value != null
                && value.startsWith(ApiKeyAuthenticator.TOKEN_PREFIX)
                && prefixOf(value) != null
                && value.length() > ApiKeyAuthenticator.TOKEN_PREFIX.length() + PREFIX_BYTES * 2 + 1;
    }
}
