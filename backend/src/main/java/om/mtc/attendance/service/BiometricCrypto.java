package om.mtc.attendance.service;

import om.mtc.attendance.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption of face embeddings at rest.
 * The soldier id is bound as additional authenticated data, so a template copied
 * onto another soldier's row fails to decrypt.
 */
@Component
public class BiometricCrypto {
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public BiometricCrypto(AppProperties props) {
        byte[] raw = Base64.getDecoder().decode(props.biometric().encryptionKey().trim());
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(float[] embedding, long soldierId) {
        try {
            ByteBuffer plain = ByteBuffer.allocate(embedding.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float v : embedding) {
                plain.putFloat(v);
            }
            return seal(plain.array(), aad(soldierId));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Embedding encryption failed", e);
        }
    }

    /** Encrypt arbitrary bytes (profile photos). {@code aad} binds the ciphertext to its owner. */
    public byte[] encryptBytes(byte[] plain, String aad) {
        try {
            return seal(plain, aad.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] blob, String aad) {
        try {
            return open(blob, aad.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed (wrong key or tampered data)", e);
        }
    }

    private byte[] seal(byte[] plain, byte[] aad) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        c.updateAAD(aad);
        byte[] ct = c.doFinal(plain);
        return ByteBuffer.allocate(IV_LEN + ct.length).put(iv).put(ct).array();
    }

    private byte[] open(byte[] blob, byte[] aad) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN));
        c.updateAAD(aad);
        return c.doFinal(blob, IV_LEN, blob.length - IV_LEN);
    }

    public float[] decrypt(byte[] blob, long soldierId) {
        try {
            byte[] plain = open(blob, aad(soldierId));
            ByteBuffer bb = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN);
            float[] out = new float[plain.length / 4];
            for (int i = 0; i < out.length; i++) {
                out[i] = bb.getFloat();
            }
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Embedding decryption failed (wrong key or tampered data)", e);
        }
    }

    private static byte[] aad(long soldierId) {
        return ("soldier:" + soldierId).getBytes(StandardCharsets.UTF_8);
    }
}
