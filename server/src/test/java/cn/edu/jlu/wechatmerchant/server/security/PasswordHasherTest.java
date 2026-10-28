package cn.edu.jlu.wechatmerchant.server.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordHasherTest {
    @Test
    void randomSaltsProduceDifferentHashesAndVerifyUnicodeWithoutTruncatingLongPasswords() {
        var hasher = new PasswordHasher();
        char[] password = ("测试口令" + "a".repeat(100)).toCharArray();
        String one = hasher.hash(password);
        String two = hasher.hash(password);
        assertNotEquals(one, two);
        assertFalse(one.contains(new String(password)));
        assertTrue(hasher.verify(password, one));
        assertTrue(hasher.verify(password, two));
        assertFalse(hasher.verify("wrong".toCharArray(), one));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sha256$secret", "pbkdf2-sha256$1$AAAA$BBBB", "pbkdf2-sha256$999999999999$AAAA$BBBB",
            "pbkdf2-sha256$600000$not-base64$AAAA", "pbkdf2-sha256$600000$AAAA$BBBB"})
    void malformedOrUnsafeHashesCannotAuthenticate(String encoded) {
        assertFalse(new PasswordHasher().verify("anything".toCharArray(), encoded));
    }

    @Test
    void invalidInputHasNoFallbackHash() {
        var hasher = new PasswordHasher();
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(new char[0]));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(new char[1025]));
        assertFalse(hasher.verify(null, "x"));
    }
}
