package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.persistence.UserDao;
import cn.edu.jlu.wechatmerchant.server.security.PasswordHasher;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.Semaphore;

public final class AuthenticationService {
    public static final class Busy extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private final UserDao users;
    private final PasswordHasher hasher = new PasswordHasher();
    private final Semaphore slots = new Semaphore(2);
    private final String dummyHash;

    public AuthenticationService(UserDao users) {
        this.users = users;
        // Unknown usernames still perform one normal-cost verification.
        dummyHash = hasher.hash("unknown-account-verification".toCharArray());
    }

    public Optional<UserIdentity> authenticate(String username, char[] password, UserIdentity.Role expectedRole)
            throws SQLException, Busy {
        if (username == null || !username.matches("[a-z0-9_]{3,32}") || password == null
                || password.length < 1 || password.length > 1024 || expectedRole == null) return Optional.empty();
        if (!slots.tryAcquire()) throw new Busy();
        try {
            var account = users.findByUsername(username);
            boolean valid = hasher.verify(password, account.map(UserDao.Account::passwordHash).orElse(dummyHash));
            if (!valid || account.isEmpty() || !account.get().enabled() || account.get().identity().role() != expectedRole) {
                return Optional.empty();
            }
            return account.map(UserDao.Account::identity);
        } finally {
            slots.release();
        }
    }

    public boolean isEnabled(UserIdentity identity) throws SQLException {
        return users.findByUsername(identity.username())
                .filter(account -> account.enabled() && account.identity().equals(identity)).isPresent();
    }
}
