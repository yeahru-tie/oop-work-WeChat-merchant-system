package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class ProbeServerTest {
    @Test
    void respondsToTwoConsecutiveRequestsOnOneRealConnection() throws Exception {
        try (ProbeServer server = new ProbeServer(0)) {
            server.start();
            try (Socket socket = connect(server)) {
                FrameCodec.write(socket.getOutputStream(), Messages.ping("one"));
                FrameCodec.write(socket.getOutputStream(), Messages.ping("two"));
                assertEquals("merchant-server", Messages.validatePong(FrameCodec.read(socket, 2000), "one"));
                assertEquals("merchant-server", Messages.validatePong(FrameCodec.read(socket, 2000), "two"));
            }
        }
    }

    @Test
    void servesIndependentConnectionsConcurrently() throws Exception {
        try (ProbeServer server = new ProbeServer(0);
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            server.start();
            var first = pool.submit(() -> exchange(server, "first"));
            var second = pool.submit(() -> exchange(server, "second"));
            assertEquals("merchant-server", first.get(3, TimeUnit.SECONDS));
            assertEquals("merchant-server", second.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void reportsUnsupportedOperationWithoutPretendingBusinessSuccess() throws Exception {
        try (ProbeServer server = new ProbeServer(0)) {
            server.start();
            try (Socket socket = connect(server)) {
                var request = Messages.ping("products");
                request.addProperty("operation", 1199);
                FrameCodec.write(socket.getOutputStream(), request);
                var response = FrameCodec.read(socket, 2000);
                assertEquals("OPERATION_UNSUPPORTED", response.get("code").getAsString());
                assertEquals(2199, response.get("operation").getAsInt());
                FrameCodec.write(socket.getOutputStream(), Messages.ping("still-open"));
                assertEquals("merchant-server", Messages.validatePong(FrameCodec.read(socket, 2000), "still-open"));
            }
        }
    }

    @Test
    void reportsUnsupportedVersionThenCloses() throws Exception {
        try (ProbeServer server = new ProbeServer(0)) {
            server.start();
            try (Socket socket = connect(server)) {
                var request = Messages.ping("old-version");
                request.addProperty("version", 2);
                FrameCodec.write(socket.getOutputStream(), request);
                assertEquals("VERSION_UNSUPPORTED", FrameCodec.read(socket, 2000).get("code").getAsString());
                assertNull(FrameCodec.read(socket, 2000));
            }
        }
    }

    @Test
    void rejectsUnexpectedPingArguments() throws Exception {
        try (ProbeServer server = new ProbeServer(0)) {
            server.start();
            try (Socket socket = connect(server)) {
                var request = Messages.ping("args");
                request.getAsJsonObject("body").addProperty("unexpected", true);
                FrameCodec.write(socket.getOutputStream(), request);
                assertEquals("INVALID_ARGUMENT", FrameCodec.read(socket, 2000).get("code").getAsString());
            }
        }
    }

    @Test
    void malformedFrameClosesOnlyThatConnection() throws Exception {
        try (ProbeServer server = new ProbeServer(0)) {
            server.start();
            try (Socket socket = connect(server)) {
                socket.getOutputStream().write(ByteBuffer.allocate(4).putInt(-1).array());
                socket.getOutputStream().flush();
                assertDropped(socket);
            }
            assertEquals("merchant-server", exchange(server, "healthy"));
        }
    }

    @Test
    void closesIdleConnectionsAndStopsWorkers() throws Exception {
        try (ProbeServer server = new ProbeServer(0, 150)) {
            server.start();
            try (Socket socket = connect(server)) {
                assertDropped(socket);
            }
            server.close();
            assertTrue(server.awaitStopped(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void shutdownClosesAnOpenSessionAndReleasesListener() throws Exception {
        try (ProbeServer server = new ProbeServer(0)) {
            server.start();
            int port = server.port();
            try (Socket socket = connect(server)) {
                FrameCodec.write(socket.getOutputStream(), Messages.ping("connected"));
                Messages.validatePong(FrameCodec.read(socket, 2000), "connected");
                server.close();
                assertDropped(socket);
                assertTrue(server.awaitStopped(2, TimeUnit.SECONDS));
                assertThrows(IOException.class, () -> {
                    try (Socket rejected = new Socket("127.0.0.1", port)) {
                        fail("Closed listener accepted a new connection.");
                    }
                });
            }
        }
    }

    private static String exchange(ProbeServer server, String id) throws Exception {
        try (Socket socket = connect(server)) {
            FrameCodec.write(socket.getOutputStream(), Messages.ping(id));
            return Messages.validatePong(FrameCodec.read(socket, 2000), id);
        }
    }

    private static Socket connect(ProbeServer server) throws IOException {
        return new Socket("127.0.0.1", server.port());
    }

    private static void assertDropped(Socket socket) throws IOException {
        try {
            assertNull(FrameCodec.read(socket, 2000));
        } catch (SocketException expected) {
            // A reset and a clean EOF both mean the peer closed this connection.
        }
    }
}
