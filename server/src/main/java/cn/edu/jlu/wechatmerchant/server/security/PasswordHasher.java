package cn.edu.jlu.wechatmerchant.server.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    public static final int ITERATIONS = 600000;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(char[] password) {
        checkPassword(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt);
        try {
            return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(derived);
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    public boolean verify(char[] password, String encoded) {
        if (password == null || password.length < 1 || password.length > 1024 || encoded == null || encoded.length() > 160) {
            return false;
        }
        byte[] actual = null;
        try {
            String[] fields = encoded.split("\\$", -1);
            if (fields.length != 4 || !"pbkdf2-sha256".equals(fields[0])
                    || !Integer.toString(ITERATIONS).equals(fields[1])) return false;
            byte[] salt = Base64.getDecoder().decode(fields[2]);
            byte[] expected = Base64.getDecoder().decode(fields[3]);
            if (salt.length != 16 || expected.length != 32) return false;
            actual = derive(password, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        } finally {
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    private static void checkPassword(char[] password) {
        if (password == null || password.length < 1 || password.length > 1024) {
            throw new IllegalArgumentException("Password length is invalid.");
        }
    }

    private static byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Configured password hashing is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
