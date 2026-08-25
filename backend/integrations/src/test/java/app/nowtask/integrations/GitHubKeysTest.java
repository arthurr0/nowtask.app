package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GitHubKeysTest {

    private static KeyPair pair;

    @BeforeAll
    static void generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
    }

    @Test
    void readsThePkcs8FormatJavaProduces() {
        PrivateKey key = GitHubKeys.privateKey(pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        assertThat(key.getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    }

    @Test
    void readsThePkcs1FormatGitHubHandsOut() {
        PrivateKey key = GitHubKeys.privateKey(pem("RSA PRIVATE KEY", pkcs1()));
        assertThat(key.getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    }

    @Test
    void readsAKeyWhoseNewlinesWereEscapedInAnEnvironmentVariable() {
        String escaped = pem("RSA PRIVATE KEY", pkcs1()).replace("\n", "\\n");
        assertThat(GitHubKeys.privateKey(escaped).getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    }

    @Test
    void refusesAnEmptyKey() {
        assertThatThrownBy(() -> GitHubKeys.privateKey("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void refusesNonsense() {
        assertThatThrownBy(() -> GitHubKeys.privateKey(pem("RSA PRIVATE KEY", new byte[] {1, 2, 3})))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    private static byte[] pkcs1() {
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) pair.getPrivate();

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(integer(BigInteger.ZERO));
        content.writeBytes(integer(key.getModulus()));
        content.writeBytes(integer(key.getPublicExponent()));
        content.writeBytes(integer(key.getPrivateExponent()));
        content.writeBytes(integer(key.getPrimeP()));
        content.writeBytes(integer(key.getPrimeQ()));
        content.writeBytes(integer(key.getPrimeExponentP()));
        content.writeBytes(integer(key.getPrimeExponentQ()));
        content.writeBytes(integer(key.getCrtCoefficient()));

        return element(0x30, content.toByteArray());
    }

    private static byte[] integer(BigInteger value) {
        return element(0x02, value.toByteArray());
    }

    private static byte[] element(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);

        if (value.length < 0x80) {
            out.write(value.length);
        } else {
            byte[] size = BigInteger.valueOf(value.length).toByteArray();
            int start = size.length > 1 && size[0] == 0 ? 1 : 0;
            out.write(0x80 | (size.length - start));
            out.write(size, start, size.length - start);
        }

        out.writeBytes(value);
        return out.toByteArray();
    }
}
