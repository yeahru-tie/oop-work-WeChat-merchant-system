package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrameCodecTest {
    @Test
    void readsSplitFramesAndCountsUtf8Bytes() throws Exception {
        JsonObject message = new JsonObject();
        message.addProperty("text", "中文消息");
        byte[] bytes = encoded(message);
        assertEquals(bytes.length - 4, ByteBuffer.wrap(bytes).getInt());
        try (var input = new FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                return in.read(buffer, offset, Math.min(length, 1));
            }
        }) {
            assertEquals(message, FrameCodec.read(input));
            assertNull(FrameCodec.read(input));
        }
    }

    @Test
    void readsConsecutiveFramesWithoutMergingThem() throws Exception {
        var output = new ByteArrayOutputStream();
        FrameCodec.write(output, Messages.ping("first"));
        FrameCodec.write(output, Messages.ping("second"));
        var input = new ByteArrayInputStream(output.toByteArray());
        assertEquals("first", FrameCodec.read(input).get("requestId").getAsString());
        assertEquals("second", FrameCodec.read(input).get("requestId").getAsString());
        assertNull(FrameCodec.read(input));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1048577})
    void rejectsLengthBeforeAllocatingBody(int length) {
        var input = new ByteArrayInputStream(ByteBuffer.allocate(4).putInt(length).array());
        assertThrows(ProtocolException.class, () -> FrameCodec.read(input));
    }

    @Test
    void distinguishesCleanEofFromTruncatedHeaderAndBody() throws Exception {
        assertNull(FrameCodec.read(new ByteArrayInputStream(new byte[0])));
        assertThrows(EOFException.class, () -> FrameCodec.read(new ByteArrayInputStream(new byte[] {0, 0})));
        byte[] partial = ByteBuffer.allocate(5).putInt(2).put((byte) '{').array();
        assertThrows(EOFException.class, () -> FrameCodec.read(new ByteArrayInputStream(partial)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{", "{key:1}", "{} {}", "{\"x\":NaN}"})
    void rejectsInvalidOrNonObjectJson(String json) {
        assertThrows(ProtocolException.class, () -> FrameCodec.read(raw(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsMalformedUtf8() {
        byte[] invalid = {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'};
        assertThrows(ProtocolException.class, () -> FrameCodec.read(raw(invalid)));
    }

    @Test
    void refusesOversizeOutgoingMessage() {
        JsonObject message = new JsonObject();
        message.addProperty("text", "x".repeat(FrameCodec.MAX_BYTES));
        assertThrows(ProtocolException.class, () -> FrameCodec.write(new ByteArrayOutputStream(), message));
    }

    private static byte[] encoded(JsonObject object) throws IOException {
        var output = new ByteArrayOutputStream();
        FrameCodec.write(output, object);
        return output.toByteArray();
    }

    private static ByteArrayInputStream raw(byte[] bytes) {
        return new ByteArrayInputStream(ByteBuffer.allocate(4 + bytes.length).putInt(bytes.length).put(bytes).array());
    }
}
