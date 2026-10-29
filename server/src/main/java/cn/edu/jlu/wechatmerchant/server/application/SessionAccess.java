package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.sql.Connection;
import java.sql.SQLException;

/** Role and current account state checked inside the caller's transaction. */
public final class SessionAccess {
    private final SessionStore sessions;
    public SessionAccess(SessionStore sessions) { this.sessions=sessions; }
    public UserIdentity require(Connection connection,String token,UserIdentity.Role role) throws SQLException {
        return require(connection,token,role,true);
    }
    public UserIdentity require(Connection connection,String token,UserIdentity.Role role,boolean touch) throws SQLException {
        var identity=(touch?sessions.resolve(token):sessions.peek(token)).orElseThrow(() -> new SecurityException("UNAUTHENTICATED"));
        if(identity.role()!=role) throw new SecurityException("FORBIDDEN");
        try(var statement=connection.prepareStatement("SELECT username,display_name,role,enabled FROM users WHERE user_id=?")) {
            statement.setLong(1,identity.userId());
            try(var rows=statement.executeQuery()) {
                if(!rows.next() || rows.getInt("enabled")!=1 || !identity.username().equals(rows.getString("username"))
                        || !identity.displayName().equals(rows.getString("display_name")) || !role.name().equals(rows.getString("role"))) {
                    sessions.revoke(token); throw new SecurityException("UNAUTHENTICATED");
                }
            }
        }
        return identity;
    }
}
