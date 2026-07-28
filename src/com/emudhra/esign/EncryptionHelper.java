package com.emudhra.esign;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.emcastle.util.encoders.UrlBase64;

public class EncryptionHelper {

    private EncryptionHelper() {

    }

    private static final String PBKDF2WITHHMACSHA1 = "PBKDF2WithHmacSHA1";
    private static final String AES_CBC_PKCS5PADDING = "AES/CBC/PKCS5Padding";
    private static final String AES = "AES";
    private static final byte[] SALT = new byte[]{0x49,0x76,0x61,0x6e,0x20,0x4d,0x65,0x64,0x76,0x65,0x64,0x65,0x76};
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String AES_GCM_NOPADDING = "AES/GCM/NoPadding";
    private static final byte VERSION_2 = 0x02;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITER = 1000;
    private static final int PBKDF2_KEYLEN_BITS = 384;

    private static byte[] deriveKeyMaterial(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2WITHHMACSHA1);
        PBEKeySpec pbeKeySpec = new PBEKeySpec(key.toCharArray(), SALT, PBKDF2_ITER, PBKDF2_KEYLEN_BITS);
        return factory.generateSecret(pbeKeySpec).getEncoded();
    }

    public static String getEncryptedData(String data, String keyValue) throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException {
        return encrypt(data, keyValue);
    }

    public static String getEncryptedData(String data) throws Exception {
        if (eSignUtility.isNullOrEmpty(data)) {
            throw new Exception("Invalid path data.");
        }
        return encrypt(data, eSignSettings.getEncryptionKey());
    }

    private static String encrypt(String data, String keyValue) {
        try {
            byte[] km = deriveKeyMaterial(keyValue);
            byte[] key1 = new byte[32];
            System.arraycopy(km, 0, key1, 0, 32);
            SecretKeySpec skeySpec = new SecretKeySpec(key1, AES);
            byte[] iv = new byte[GCM_IV_LEN];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NOPADDING);
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + iv.length + ct.length];
            out[0] = VERSION_2;
            System.arraycopy(iv, 0, out, 1, iv.length);
            System.arraycopy(ct, 0, out, 1 + iv.length, ct.length);
            return new String(UrlBase64.encode(out));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
            return null;
        }
    }

    public static String getDencryptedData(String encrypted, String key) {
        return decrypt(encrypted, key);
    }

    public static String getDencryptedData(String encrypted) throws Exception {
        if (eSignUtility.isNullOrEmpty(encrypted)) {
            throw new Exception("Encrypted data cannot be empty.");
        }
        return decrypt(encrypted, eSignSettings.getEncryptionKey());
    }

    private static String decrypt(String encrypted, String key) {
        try {
            byte[] all = UrlBase64.decode(encrypted);
            if (all.length > (1 + GCM_IV_LEN + GCM_TAG_BITS / 8) - 1 && all[0] == VERSION_2) {
                try {
                    byte[] km = deriveKeyMaterial(key);
                    byte[] key1 = new byte[32];
                    System.arraycopy(km, 0, key1, 0, 32);
                    byte[] iv = new byte[GCM_IV_LEN];
                    System.arraycopy(all, 1, iv, 0, GCM_IV_LEN);
                    byte[] ct = new byte[all.length - 1 - GCM_IV_LEN];
                    System.arraycopy(all, 1 + GCM_IV_LEN, ct, 0, ct.length);
                    Cipher cipher = Cipher.getInstance(AES_GCM_NOPADDING);
                    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key1, AES), new GCMParameterSpec(GCM_TAG_BITS, iv));
                    return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
                } catch (Exception gcmEx) {
                    // fall through to legacy v1 CBC read
                }
            }
            byte[] km = deriveKeyMaterial(key);
            byte[] key1 = new byte[32];
            byte[] iv = new byte[16];
            System.arraycopy(km, 0, key1, 0, 32);
            System.arraycopy(km, 32, iv, 0, 16);
            Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5PADDING);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key1, AES), new IvParameterSpec(iv));
            return new String(cipher.doFinal(all), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
            return null;
        }
    }
}
