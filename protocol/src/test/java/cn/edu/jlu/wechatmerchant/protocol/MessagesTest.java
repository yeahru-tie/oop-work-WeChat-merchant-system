package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessagesTest {
    @Test
    void matchesRequestAndResponseAndKeepsSessionExplicitlyNull() throws Exception {
        var request = Messages.parseRequest(Messages.ping("probe-1"));
        JsonObject body = new JsonObject();
        body.addProperty("service", "merchant-server");
        JsonObject response = Messages.response(request, "OK", "pong", body);
        assertEquals("merchant-server", Messages.validatePong(response, "probe-1"));
        assertTrue(response.get("sessionToken").isJsonNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "operation"})
    void numericFieldsCannotBeStringsFractionsOrMissing(String field) {
        JsonObject request = Messages.ping("id");
        request.addProperty(field, "1000");
        assertThrows(ProtocolException.class, () -> Messages.parseRequest(request));
        request.addProperty(field, 1.5);
        assertThrows(ProtocolException.class, () -> Messages.parseRequest(request));
        request.remove(field);
        assertThrows(ProtocolException.class, () -> Messages.parseRequest(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"requestId", "body", "sessionToken"})
    void requiredFieldsAreNotSilentlyDefaulted(String field) {
        JsonObject request = Messages.ping("id");
        request.remove(field);
        assertThrows(ProtocolException.class, () -> Messages.parseRequest(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "type", "requestId", "operation", "code", "service", "sessionToken"})
    void rejectsUnrelatedOrMalformedResponses(String field) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("service", "merchant-server");
        JsonObject response = Messages.response(Messages.parseRequest(Messages.ping("id")), "OK", "pong", body);
        switch (field) {
            case "version" -> response.addProperty(field, 2);
            case "type" -> response.addProperty(field, "REQUEST");
            case "requestId" -> response.addProperty(field, "another-request");
            case "operation" -> response.addProperty(field, 2001);
            case "code" -> response.addProperty(field, "INVALID_ARGUMENT");
            case "service" -> body.addProperty(field, "another-service");
            case "sessionToken" -> response.remove(field);
            default -> fail("Unexpected test input.");
        }
        assertThrows(ProtocolException.class, () -> Messages.validatePong(response, "id"));
    }
}
