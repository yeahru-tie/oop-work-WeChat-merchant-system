package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import cn.edu.jlu.wechatmerchant.server.application.ProductCatalog;
import cn.edu.jlu.wechatmerchant.server.application.ProductQueryHandler;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.Socket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10)
class ProductQueryIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void realSocketReturnsActualPersistedDataAndPagingMetadata() throws Exception {
        SqliteDatabase database = database();
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE products SET stock=7,price_cents=5000000001 WHERE product_id=1001");
        }
        SqliteDatabase reopened = new SqliteDatabase(database.file());
        reopened.initialize();
        try (ProbeServer server = server(reopened)) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                JsonObject response = exchange(socket, Messages.productList("list-1", 2, 0));
                assertEquals("list-1", response.get("requestId").getAsString());
                assertEquals(Messages.PRODUCTS, response.get("operation").getAsInt());
                assertEquals("OK", response.get("code").getAsString());
                JsonObject page = response.getAsJsonObject("body");
                assertTrue(page.get("hasMore").getAsBoolean());
                assertEquals(2, page.getAsJsonArray("items").size());
                JsonObject first = page.getAsJsonArray("items").get(0).getAsJsonObject();
                assertEquals("演示笔记本", first.get("name").getAsString());
                assertEquals(7, first.get("stock").getAsInt());
                assertEquals(5000000001L, first.get("priceCents").getAsLong());
                JsonObject last = exchange(socket, Messages.productList("list-2", 2, 2)).getAsJsonObject("body");
                assertEquals(1, last.getAsJsonArray("items").size());
                assertFalse(last.get("hasMore").getAsBoolean());
                assertEquals("OK", exchange(socket, Messages.ping("probe")).get("code").getAsString());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"limit\":0}", "{\"limit\":101}", "{\"offset\":-1}",
            "{\"limit\":\"1; DROP TABLE products\"}", "{\"limit\":1.5}", "{\"limit\":null}",
            "{\"offset\":2147483648}", "{\"productId\":1001}"})
    void invalidParametersCannotReachDatabaseOrBreakNextRequest(String json) throws Exception {
        SqliteDatabase database = database();
        try (ProbeServer server = server(database)) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                JsonObject request = Messages.productList("bad", 20, 0);
                request.add("body", JsonParser.parseString(json));
                assertEquals("INVALID_ARGUMENT", exchange(socket, request).get("code").getAsString());
                JsonObject response = exchange(socket, Messages.productList("good", 20, 0));
                assertEquals(3, response.getAsJsonObject("body").getAsJsonArray("items").size());
            }
        }
    }

    @Test
    void databaseFailureIsNotReturnedAsAnEmptySuccessfulCatalog() throws Exception {
        SqliteDatabase database = database();
        try (ProbeServer server = server(database)) {
            server.start();
            try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version=" + (SqliteDatabase.VERSION + 1));
            }
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                JsonObject response = exchange(socket, Messages.productList("failed", 20, 0));
                assertEquals("INTERNAL_ERROR", response.get("code").getAsString());
                assertEquals("商品查询暂不可用，请稍后重试。", response.get("message").getAsString());
                assertTrue(response.getAsJsonObject("body").isEmpty());
                assertEquals("OK", exchange(socket, Messages.ping("alive")).get("code").getAsString());
            }
        }
    }

    @Test
    void anEmptyExistingCatalogReturnsAnExplicitEmptyPage() throws Exception {
        SqliteDatabase database = database();
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM products");
        }
        database.initialize();
        try (ProbeServer server = server(database)) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                JsonObject response = exchange(socket, Messages.productList("empty", 20, 0));
                assertEquals("OK", response.get("code").getAsString());
                assertTrue(response.getAsJsonObject("body").getAsJsonArray("items").isEmpty());
                assertFalse(response.getAsJsonObject("body").get("hasMore").getAsBoolean());
            }
        }
    }

    private SqliteDatabase database() throws Exception {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("merchant.db"));
        database.initialize();
        return database;
    }

    private static ProbeServer server(SqliteDatabase database) {
        return new ProbeServer(0, new ProductQueryHandler(new ProductCatalog(new ProductDao(database))));
    }

    private static JsonObject exchange(Socket socket, JsonObject request) throws Exception {
        FrameCodec.write(socket.getOutputStream(), request);
        JsonObject response = FrameCodec.read(socket, 3000);
        assertNotNull(response);
        return response;
    }
}
