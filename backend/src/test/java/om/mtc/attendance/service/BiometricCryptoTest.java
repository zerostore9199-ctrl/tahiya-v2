package om.mtc.attendance.service;

import om.mtc.attendance.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiometricCryptoTest {

    private static BiometricCrypto crypto(byte fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        AppProperties props = new AppProperties("Asia/Muscat", null, null,
                new AppProperties.Biometric(Base64.getEncoder().encodeToString(key)), null, null, null);
        return new BiometricCrypto(props);
    }

    @Test
    void roundTripPreservesEmbedding() {
        BiometricCrypto c = crypto((byte) 7);
        float[] e = new float[512];
        for (int i = 0; i < e.length; i++) {
            e[i] = (float) Math.sin(i);
        }
        byte[] blob = c.encrypt(e, 42);
        assertThat(blob).hasSize(12 + 512 * 4 + 16);
        assertThat(c.decrypt(blob, 42)).containsExactly(e);
    }

    @Test
    void sameEmbeddingEncryptsDifferentlyEachTime() {
        BiometricCrypto c = crypto((byte) 1);
        float[] e = {1f, 2f, 3f};
        assertThat(c.encrypt(e, 1)).isNotEqualTo(c.encrypt(e, 1));
    }

    @Test
    void templateCannotBeMovedToAnotherSoldier() {
        BiometricCrypto c = crypto((byte) 3);
        byte[] blob = c.encrypt(new float[]{1f, 2f}, 10);
        assertThatThrownBy(() -> c.decrypt(blob, 11)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyOrTamperingIsDetected() {
        byte[] blob = crypto((byte) 3).encrypt(new float[]{1f, 2f}, 10);
        assertThatThrownBy(() -> crypto((byte) 4).decrypt(blob, 10)).isInstanceOf(IllegalStateException.class);
        blob[20] ^= 1;
        assertThatThrownBy(() -> crypto((byte) 3).decrypt(blob, 10)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void photoBytesRoundTripAndAreBoundToTheirOwner() {
        BiometricCrypto c = crypto((byte) 9);
        byte[] jpeg = new byte[5000];
        new java.util.Random(1).nextBytes(jpeg);
        byte[] blob = c.encryptBytes(jpeg, "photo:soldier:7");
        assertThat(blob).hasSize(12 + 5000 + 16);
        assertThat(c.decryptBytes(blob, "photo:soldier:7")).isEqualTo(jpeg);
        // another soldier, a user with the same id, or an embedding slot cannot open it
        assertThatThrownBy(() -> c.decryptBytes(blob, "photo:soldier:8")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.decryptBytes(blob, "photo:user:7")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.decryptBytes(blob, "soldier:7")).isInstanceOf(IllegalStateException.class);
    }
}
