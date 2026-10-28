package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import java.sql.SQLException;
import java.util.Optional;

public final class UserDao {
    public record Account(UserIdentity identity, String passwordHash, boolean enabled) {
        @Override
        public String toString() {
            return "Account[identity=" + identity + ",enabled=" + enabled + ",passwordHash=<redacted>]";
        }
    }

    private final SqliteDatabase database;

    public UserDao(SqliteDatabase database) {
        this.database = database;
    }

    public Optional<Account> findByUsername(String username) throws SQLException {
        try (var connection = database.openReadOnlyConnection(); var statement = connection.prepareStatement(
                "SELECT user_id,username,display_name,password_hash,role,enabled FROM users WHERE username=?")) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                try {
                    var identity = new UserIdentity(rows.getLong("user_id"), rows.getString("username"),
                            rows.getString("display_name"), UserIdentity.Role.valueOf(rows.getString("role")));
                    return Optional.of(new Account(identity, rows.getString("password_hash"), rows.getInt("enabled") == 1));
                } catch (RuntimeException exception) {
                    throw new SQLException("Invalid stored account.", exception);
                }
            }
        }
    }
}
