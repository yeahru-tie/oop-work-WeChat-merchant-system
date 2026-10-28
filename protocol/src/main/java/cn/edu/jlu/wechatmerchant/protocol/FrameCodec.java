package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class FrameCodec {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT).serializeNulls().create();

    private FrameCodec() {
    }

    /** A null result means clean EOF before the next frame, not a partial frame. */
    public static JsonObject read(InputStream input) throws IOException {
        int first = input.read();
        if (first == -1) {
            return null;
        }
        byte[] tail = input.readNBytes(3);
        if (tail.length != 3) {
            throw new EOFException("Truncated frame header.");
        }
        int size = ByteBuffer.wrap(new byte[] {(byte) first, tail[0], tail[1], tail[2]}).getInt();
        if (size <= 0 || size > MAX_BYTES) {
            throw new ProtocolException("Invalid frame length.");
        }
        byte[] payload = input.readNBytes(size);
        if (payload.length != size) {
            throw new EOFException("Truncated frame body.");
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload)).toString();
            JsonElement value = GSON.fromJson(json, JsonElement.class);
            if (value == null || !value.isJsonObject()) {
                throw new ProtocolException("Frame body must be a JSON object.");
            }
            return value.getAsJsonObject();
        } catch (CharacterCodingException | JsonParseException exception) {
            throw new ProtocolException("Invalid UTF-8 or JSON.", exception);
        }
    }

    /** The entire header and body share one deadline, including slow partial reads. */
    public static JsonObject read(Socket socket, int timeoutMillis) throws IOException {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be positive.");
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        return read(new FilterInputStream(socket.getInputStream()) {
            private void updateTimeout() throws IOException {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new SocketTimeoutException("Frame deadline exceeded.");
                }
                socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            }

            @Override
            public int read() throws IOException {
                updateTimeout();
                return in.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                updateTimeout();
                return in.read(buffer, offset, length);
            }
        });
    }

    public static void write(OutputStream output, JsonObject value) throws IOException {
        if (value == null) {
            throw new ProtocolException("Frame body is required.");
        }
        byte[] payload = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_BYTES) {
            throw new ProtocolException("Frame body is too large.");
        }
        output.write(ByteBuffer.allocate(4).putInt(payload.length).array());
        output.write(payload);
        output.flush();
    }
}
