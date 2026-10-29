package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class Messages {
    public static final int VERSION = 1;
    public static final int PING = 1000;
    public static final int PONG = 2000;
    public static final int LIST_PRODUCTS = 1001;
    public static final int PRODUCTS = 2001;
    public static final int LOGIN = 1006;
    public static final int LOGOUT = 1011;
    public static final int CURRENT_USER = 1012;
    public static final int ADD_TO_CART = 1002;
    public static final int LIST_CART = 1007;
    public static final int EDIT_CART = 1008;
    public static final int QUOTE_CART = 1009;
    public static final int LIST_COUPONS = 1013;
    public static final int CLAIM_COUPON = 1014;
    public static final int CHECKOUT = 1003;
    public static final int QUERY_ORDERS = 1010;
    public static final int APPLY_RETURN = 1004;
    public static final int QUERY_RETURNS = 1015;
    public static final int LIST_MESSAGES = 1016;
    public static final int READ_MESSAGE = 1017;

    public record Request(int version, String requestId, int operation, JsonObject body, String sessionToken) {
        @Override
        public String toString() {
            return "Request[version=" + version + ",requestId=" + requestId + ",operation=" + operation
                    + ",body=<redacted>,sessionToken=<redacted>]";
        }
    }

    public record Response(String code, String message, JsonObject body, String sessionToken) {
        @Override
        public String toString() {
            return "Response[code=" + code + ",body=<redacted>,sessionToken=<redacted>]";
        }
    }

    private Messages() {
    }

    public static JsonObject ping(String requestId) {
        JsonObject request = envelope("REQUEST", requestId, PING, new JsonObject());
        request.add("sessionToken", JsonNull.INSTANCE);
        return request;
    }

    public static JsonObject productList(String requestId, int limit, int offset) {
        JsonObject body = new JsonObject();
        body.addProperty("limit", limit);
        body.addProperty("offset", offset);
        JsonObject request = envelope("REQUEST", requestId, LIST_PRODUCTS, body);
        request.add("sessionToken", JsonNull.INSTANCE);
        return request;
    }

    public static JsonObject login(String id, String username, char[] password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", new String(password));
        return authenticatedRequest(id, LOGIN, null, body);
    }

    public static JsonObject authenticatedRequest(String id, int operation, String token, JsonObject body) {
        JsonObject request = envelope("REQUEST", id, operation, body);
        request.add("sessionToken", token == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(token));
        return request;
    }

    public static Request parseRequest(JsonObject value) throws ProtocolException {
        if (!"REQUEST".equals(string(value, "type", 16))) {
            throw new ProtocolException("Expected request.");
        }
        int version = integer(value, "version");
        int operation = integer(value, "operation");
        if (version < 1 || operation < 1000 || operation > 1999) {
            throw new ProtocolException("Invalid version or operation.");
        }
        validateSessionToken(value);
        return new Request(version, string(value, "requestId", 128), operation, body(value), token(value));
    }

    public static JsonObject response(Request request, String code, String message, JsonObject body) {
        return response(request, code, message, body, null);
    }

    public static JsonObject response(Request request, String code, String message, JsonObject body, String token) {
        JsonObject response = envelope("RESPONSE", request.requestId(), request.operation() + 1000, body);
        response.add("sessionToken", token == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(token));
        response.addProperty("code", code);
        response.addProperty("message", message);
        return response;
    }

    public static String validatePong(JsonObject response, String requestId) throws ProtocolException {
        Response parsed = validateResponse(response, requestId, PONG);
        if (!"OK".equals(parsed.code())) {
            throw new ProtocolException("Probe rejected by server.");
        }
        String service = string(parsed.body(), "service", 64);
        if (!"merchant-server".equals(service)) {
            throw new ProtocolException("Unexpected service.");
        }
        return service;
    }

    public static Response validateResponse(JsonObject response, String requestId, int operation) throws ProtocolException {
        if (response == null || integer(response, "version") != VERSION
                || !"RESPONSE".equals(string(response, "type", 16))
                || !requestId.equals(string(response, "requestId", 128))
                || integer(response, "operation") != operation) {
            throw new ProtocolException("Response correlation or version mismatch.");
        }
        validateSessionToken(response);
        return new Response(string(response, "code", 64), string(response, "message", 512), body(response), token(response));
    }

    private static String token(JsonObject value) {
        JsonElement token = value.get("sessionToken");
        return token.isJsonNull() ? null : token.getAsString();
    }

    private static void validateSessionToken(JsonObject value) throws ProtocolException {
        JsonElement token = value.get("sessionToken");
        if (token == null || (!token.isJsonNull() && (!token.isJsonPrimitive()
                || !token.getAsJsonPrimitive().isString() || token.getAsString().length() > 4096))) {
            throw new ProtocolException("Invalid session token.");
        }
    }

    private static JsonObject envelope(String type, String id, int operation, JsonObject body) {
        JsonObject value = new JsonObject();
        value.addProperty("version", VERSION);
        value.addProperty("type", type);
        value.addProperty("requestId", id);
        value.addProperty("operation", operation);
        value.add("body", body);
        return value;
    }

    private static JsonObject body(JsonObject value) throws ProtocolException {
        JsonElement body = value.get("body");
        if (body == null || !body.isJsonObject()) {
            throw new ProtocolException("Object body is required.");
        }
        return body.getAsJsonObject();
    }

    private static String string(JsonObject object, String key, int maxLength) throws ProtocolException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ProtocolException("String field required: " + key);
        }
        String text = value.getAsString();
        if (text.isBlank() || text.length() > maxLength) {
            throw new ProtocolException("Invalid string length: " + key);
        }
        return text;
    }

    private static int integer(JsonObject object, String key) throws ProtocolException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new ProtocolException("Integer field required: " + key);
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ProtocolException("Invalid integer: " + key, exception);
        }
    }
}
