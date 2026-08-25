package app.nowtask.integrations;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

final class GitHubKeys {

    private static final String PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final byte[] RSA_ALGORITHM = {
        0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7,
        0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };

    private GitHubKeys() {
    }

    static PrivateKey privateKey(String pem) {
        String text = pem == null ? "" : pem.trim().replace("\\n", "\n");
        if (text.isBlank()) {
            throw new IllegalStateException("The GitHub App private key is not configured");
        }

        byte[] der = decodeBody(text);
        byte[] pkcs8 = text.contains(PKCS1_HEADER) ? wrapPkcs1(der) : der;

        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException("The GitHub App private key cannot be read: " + e.getMessage(), e);
        }
    }

    private static byte[] decodeBody(String pem) {
        if (!pem.contains(PKCS1_HEADER) && !pem.contains(PKCS8_HEADER)) {
            return Base64.getDecoder().decode(pem.replaceAll("\\s", ""));
        }

        String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] octetString = derElement(0x04, pkcs1);
        byte[] version = {0x02, 0x01, 0x00};

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(version);
        content.writeBytes(RSA_ALGORITHM);
        content.writeBytes(octetString);

        return derElement(0x30, content.toByteArray());
    }

    private static byte[] derElement(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);

        int length = value.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            byte[] size = trimLeadingZeros(new byte[] {
                (byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length
            });
            out.write(0x80 | size.length);
            out.writeBytes(size);
        }

        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] trimLeadingZeros(byte[] value) {
        int start = 0;
        while (start < value.length - 1 && value[start] == 0) {
            start++;
        }

        byte[] trimmed = new byte[value.length - start];
        System.arraycopy(value, start, trimmed, 0, trimmed.length);
        return trimmed;
    }

    static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    static String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }
}
