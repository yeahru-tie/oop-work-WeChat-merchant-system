package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.Messages;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;

public final class IdentityHandler {
    public record BuyerAccess(UserIdentity identity, JsonObject denial) { }
    private static final Gson GSON = new Gson();
    private final AuthenticationService authentication;
    private final SessionStore sessions;

    public IdentityHandler(AuthenticationService authentication, SessionStore sessions) {
        this.authentication = authentication;
        this.sessions = sessions;
    }

    public boolean handles(int operation) {
        return operation == Messages.LOGIN || operation == Messages.LOGOUT || operation == Messages.CURRENT_USER;
    }

    public JsonObject handle(Messages.Request request) {
        try {
            if (request.operation() == Messages.LOGIN) {
                if (!request.body().keySet().equals(Set.of("username", "password")) || request.sessionToken() != null) {
                    return error(request, "INVALID_ARGUMENT", "登录参数无效。");
                }
                String username = text(request.body(), "username", 32);
                char[] password = text(request.body(), "password", 1024).toCharArray();
                try {
                    var identity = authentication.authenticate(username, password, UserIdentity.Role.BUYER);
                    if (identity.isEmpty()) return error(request, "INVALID_CREDENTIALS", "账号或密码不正确。");
                    String token = sessions.create(identity.get());
                    return Messages.response(request, "OK", "ok", GSON.toJsonTree(identity.get()).getAsJsonObject(), token);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
            if (!request.body().isEmpty()) return error(request, "INVALID_ARGUMENT", "身份请求参数无效。");
            if (request.operation() == Messages.LOGOUT) {
                // Repeated logout is harmless and does not disclose whether a token existed.
                sessions.revoke(request.sessionToken());
                return Messages.response(request, "OK", "ok", new JsonObject());
            }
            JsonObject denied = authorizeBuyer(request);
            if (denied != null) return denied;
            var identity = sessions.peek(request.sessionToken());
            if (identity.isEmpty()) return error(request, "UNAUTHENTICATED", "登录已失效，请重新登录。");
            return Messages.response(request, "OK", "ok", GSON.toJsonTree(identity.get()).getAsJsonObject());
        } catch (IllegalArgumentException exception) {
            return error(request, "INVALID_ARGUMENT", "身份请求参数无效。");
        } catch (AuthenticationService.Busy | IllegalStateException exception) {
            return error(request, "BUSY_RETRY", "登录服务繁忙，请稍后重试。");
        } catch (SQLException exception) {
            System.err.println("Identity query failed, database code: " + exception.getErrorCode());
            return error(request, "INTERNAL_ERROR", "身份服务暂不可用，请稍后重试。");
        }
    }

    public JsonObject authorizeBuyer(Messages.Request request) {
        return requireBuyer(request).denial();
    }

    public BuyerAccess requireBuyer(Messages.Request request) {
        try {
            var identity = sessions.resolve(request.sessionToken());
            if (identity.isEmpty()) return new BuyerAccess(null, error(request, "UNAUTHENTICATED", "登录已失效，请重新登录。"));
            if (identity.get().role() != UserIdentity.Role.BUYER) return new BuyerAccess(null, error(request, "FORBIDDEN", "当前身份无权执行此操作。"));
            if (!authentication.isEnabled(identity.get())) {
                sessions.revoke(request.sessionToken());
                return new BuyerAccess(null, error(request, "UNAUTHENTICATED", "登录已失效，请重新登录。"));
            }
            return new BuyerAccess(identity.get(), null);
        } catch (SQLException exception) {
            return new BuyerAccess(null, error(request, "INTERNAL_ERROR", "身份服务暂不可用，请稍后重试。"));
        }
    }

    private static String text(JsonObject body, String key, int maxLength) {
        var value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isEmpty() || value.getAsString().length() > maxLength) {
            throw new IllegalArgumentException("Invalid credential field.");
        }
        return value.getAsString();
    }

    private static JsonObject error(Messages.Request request, String code, String message) {
        return Messages.response(request, code, message, new JsonObject());
    }
}
