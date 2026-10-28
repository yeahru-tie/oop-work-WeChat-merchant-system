package cn.edu.jlu.wechatmerchant.server.security;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

public final class SessionStore {
    private record Session(UserIdentity identity, long touched) {
    }

    private static final int MAX_SESSIONS = 256;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new HashMap<>();
    private final Clock clock;
    private final long timeoutMillis;

    public SessionStore() {
        this(Clock.systemUTC(), Duration.ofMinutes(30));
    }

    public SessionStore(Clock clock, Duration timeout) {
        if (clock == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Invalid session configuration.");
        }
        this.clock = clock;
        timeoutMillis = timeout.toMillis();
        if (timeoutMillis < 1) throw new IllegalArgumentException("Session timeout is too short.");
    }

    public synchronized String create(UserIdentity identity) {
        cleanup();
        if (sessions.size() >= MAX_SESSIONS) throw new IllegalStateException("Session capacity reached.");
        byte[] bytes = new byte[32];
        String token;
        String verifier;
        do {
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            verifier = verifier(token);
        } while (sessions.containsKey(verifier));
        sessions.put(verifier, new Session(identity, clock.millis()));
        return token;
    }

    public synchronized Optional<UserIdentity> resolve(String token) {
        return find(token, true);
    }

    public synchronized Optional<UserIdentity> peek(String token) {
        return find(token, false);
    }

    private Optional<UserIdentity> find(String token, boolean touch) {
        cleanup();
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        String key = verifier(token);
        Session session = sessions.get(key);
        if (session == null) return Optional.empty();
        if (touch) sessions.put(key, new Session(session.identity(), clock.millis()));
        return Optional.of(session.identity());
    }

    public synchronized void revoke(String token) {
        if (token != null) sessions.remove(verifier(token));
    }

    public synchronized void clear() {
        sessions.clear();
    }

    private void cleanup() {
        long now = clock.millis();
        sessions.values().removeIf(session -> now - session.touched() >= timeoutMillis);
    }

    private static String verifier(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable.", exception);
        }
    }
}
