package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;

/** Public identity only; contains neither a password hash nor a session token. */
public record UserIdentity(long userId, String username, String displayName, Role role) {
    public enum Role { BUYER, ADMIN }

    public UserIdentity {
        if (userId <= 0 || username == null || !username.matches("[a-z0-9_]{3,32}")
                || displayName == null || displayName.isBlank() || displayName.length() > 80 || role == null) {
            throw new IllegalArgumentException("Invalid user identity.");
        }
    }

    public static UserIdentity parse(JsonObject value) throws ProtocolException {
        try {
            if (!value.get("userId").isJsonPrimitive() || !value.get("userId").getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Invalid user ID.");
            }
            for (String key : new String[] {"username", "displayName", "role"}) {
                if (!value.get(key).isJsonPrimitive() || !value.get(key).getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Invalid user text.");
                }
            }
            return new UserIdentity(value.get("userId").getAsBigDecimal().longValueExact(), value.get("username").getAsString(),
                    value.get("displayName").getAsString(), Role.valueOf(value.get("role").getAsString()));
        } catch (RuntimeException exception) {
            throw new ProtocolException("Invalid public user identity.", exception);
        }
    }
}
