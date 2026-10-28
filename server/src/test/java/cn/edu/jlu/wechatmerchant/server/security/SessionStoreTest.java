package cn.edu.jlu.wechatmerchant.server.security;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import java.time.Duration;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SessionStoreTest {
    private final UserIdentity buyer = new UserIdentity(1, "buyer_a", "买家 A", UserIdentity.Role.BUYER);

    @Test
    void tokensAreOpaqueUniqueIsolatedAndRevocable() {
        var store = new SessionStore();
        String one = store.create(buyer);
        var other = new UserIdentity(2, "buyer_b", "买家 B", UserIdentity.Role.BUYER);
        String two = store.create(other);
        assertTrue(one.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(one, two);
        assertEquals(buyer, store.resolve(one).orElseThrow());
        assertEquals(other, store.resolve(two).orElseThrow());
        assertTrue(store.resolve("buyer_a").isEmpty());
        assertTrue(store.resolve(null).isEmpty());
        store.revoke(one);
        assertTrue(store.resolve(one).isEmpty());
        assertTrue(store.resolve(two).isPresent());
        store.clear();
        assertTrue(store.resolve(two).isEmpty());
    }

    @Test
    void onlyAuthenticatedActivityExtendsIdleTimeAndPeekDoesNot() {
        var clock = new MutableClock();
        var store = new SessionStore(clock, Duration.ofMinutes(30));
        String token = store.create(buyer);
        clock.advance(Duration.ofMinutes(29));
        assertTrue(store.resolve(token).isPresent());
        clock.advance(Duration.ofMinutes(29));
        assertTrue(store.peek(token).isPresent());
        clock.advance(Duration.ofMinutes(1));
        assertTrue(store.resolve(token).isEmpty());
    }

    @Test
    void capacityIsBoundedAndExpiredEntriesReleaseIt() {
        var clock = new MutableClock();
        var store = new SessionStore(clock, Duration.ofMinutes(30));
        var tokens = new HashSet<String>();
        for (int i = 0; i < 256; i++) assertTrue(tokens.add(store.create(buyer)));
        assertThrows(IllegalStateException.class, () -> store.create(buyer));
        clock.advance(Duration.ofMinutes(30));
        assertNotNull(store.create(buyer));
    }
}
