package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.server.domain.Product;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.sqlite.SQLiteConfig;

public final class SqliteDatabase {
    public static final int APPLICATION_ID = 0x4D435331;
    public static final int VERSION = 8;
    private final Path file;
    private final List<Product> seedOverride;

    public SqliteDatabase(Path file) {
        this(file, null);
    }

    SqliteDatabase(Path file, List<Product> seedOverride) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        this.seedOverride = seedOverride == null ? null : List.copyOf(seedOverride);
    }

    public static Path configuredPath() {
        return Path.of(System.getProperty("merchant.database", "data/merchant.db")).toAbsolutePath().normalize();
    }

    public Path file() {
        return file;
    }

    public void initialize() throws SQLException {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException exception) {
            throw new SQLException("Cannot prepare database directory.", exception);
        }
        try (Connection connection = openWriteConnection()) {
            int originalVersion = pragma(connection, "user_version");
            if (pragma(connection, "application_id") == APPLICATION_ID && originalVersion >= 1 && originalVersion <= 7) {
                validateProducts(connection);
                if (originalVersion >= 2) validateUsers(connection);
                if (originalVersion >= 3) validateCarts(connection);
                if (originalVersion >= 4) validatePromotions(connection);
                if (originalVersion >= 5) validateCoupons(connection);
                if (originalVersion >= 6) validateOrders(connection);
                if (originalVersion >= 7) validateAfterSales(connection);
                Path backup = file.resolveSibling(file.getFileName() + ".v" + originalVersion + "-" + UUID.randomUUID() + ".db");
                try (var statement = connection.prepareStatement("VACUUM INTO ?")) {
                    statement.setString(1, backup.toString());
                    statement.execute();
                }
            }
            connection.setAutoCommit(false);
            try {
                int version = pragma(connection, "user_version");
                int owner = pragma(connection, "application_id");
                if (version == 0 && owner == 0 && isEmpty(connection)) {
                    try (var statement = connection.createStatement()) {
                        statement.execute(resource("/db/schema-v1.sql"));
                    }
                    insertSeeds(connection, seedOverride == null ? defaultSeeds() : seedOverride);
                    initializeUsers(connection);
                    initializeCarts(connection);
                    initializePromotions(connection);
                    initializeCoupons(connection);
                    initializeOrders(connection);
                    initializeAfterSales(connection);
                    initializeNotifications(connection);
                    try (var statement = connection.createStatement()) {
                        statement.execute("PRAGMA application_id=" + APPLICATION_ID);
                        statement.execute("PRAGMA user_version=" + VERSION);
                    }
                } else if (owner == APPLICATION_ID && version >= 1 && version <= 7) {
                    validateProducts(connection);
                    if (version == 1) initializeUsers(connection);
                    else validateUsers(connection);
                    if (version < 3) initializeCarts(connection);
                    else validateCarts(connection);
                    if(version<4) initializePromotions(connection);
                    else validatePromotions(connection);
                    if(version<5)initializeCoupons(connection);
                    else validateCoupons(connection);
                    if(version<6)initializeOrders(connection);
                    else validateOrders(connection);
                    if(version<7)initializeAfterSales(connection);
                    else validateAfterSales(connection);
                    initializeNotifications(connection);
                    try (var statement = connection.createStatement()) {
                        statement.execute("PRAGMA user_version=" + VERSION);
                    }
                } else {
                    validate(connection);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }

    public Connection openWriteConnection() throws SQLException {
        return connect(false);
    }

    /** Calling setAutoCommit(false) acquires the SQLite write reservation before any cart reads. */
    public Connection openImmediateConnection() throws SQLException {
        Connection connection = connect(false, true);
        try {
            validate(connection);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    public Connection openReadOnlyConnection() throws SQLException {
        Connection connection = connect(true);
        try {
            validate(connection);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private Connection connect(boolean readOnly) throws SQLException {
        return connect(readOnly, false);
    }

    private Connection connect(boolean readOnly, boolean immediate) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(readOnly);
        config.setBusyTimeout(3000);
        config.enforceForeignKeys(true);
        if (immediate) config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return DriverManager.getConnection("jdbc:sqlite:" + file, config.toProperties());
    }

    private static void validate(Connection connection) throws SQLException {
        if (pragma(connection, "application_id") != APPLICATION_ID || pragma(connection, "user_version") != VERSION) {
            throw new SQLException("Unsupported database identity or schema version.");
        }
        validateProducts(connection);
        validateUsers(connection);
        validateCarts(connection);
        validatePromotions(connection);
        validateCoupons(connection);
        validateOrders(connection);
        validateAfterSales(connection);
        try(var s=connection.createStatement()){
            try(var r=s.executeQuery("SELECT message_id,user_id,event_key,kind,title,body,created_at,order_id,product_id FROM notifications LIMIT 0")){}
            try(var r=s.executeQuery("SELECT message_id,user_id,read_at FROM message_reads LIMIT 0")){}
        }
    }

    private static void validateAfterSales(Connection connection) throws SQLException {
        try(var s=connection.createStatement()){
            try(var r=s.executeQuery("SELECT after_sale_id,order_id,user_id,return_key,version,status,reason,review_note,applied_at,reviewed_at,completed_at,refund_cents,reviewer_id FROM after_sales LIMIT 0")){}
            try(var r=s.executeQuery("SELECT user_id,return_key,request_hash,state,after_sale_id,failure_code FROM return_attempts LIMIT 0")){}
            try(var r=s.executeQuery("SELECT after_sale_id,order_id,amount_cents,completed_at,operator_id FROM refunds LIMIT 0")){}
            try(var r=s.executeQuery("SELECT 1 FROM sqlite_master WHERE type='index' AND name='one_live_return'")){if(!r.next())throw new SQLException("Missing after-sale constraint.");}
        }
    }

    private static void validateOrders(Connection connection) throws SQLException {
        try(var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT order_id,user_id,checkout_key,created_at,status,coupon_json,original_cents,product_reduction_cents,coupon_reduction_cents,paid_cents FROM orders LIMIT 0")){}
            try(var rows=statement.executeQuery("SELECT order_id,item_seq,cart_item_id,product_id,name,price_cents,quantity,promotion_json FROM order_items LIMIT 0")){}
            try(var rows=statement.executeQuery("SELECT user_id,checkout_key,request_hash,state,order_id,failure_code FROM checkout_attempts LIMIT 0")){}
            try(var rows=statement.executeQuery("SELECT used_order_id FROM user_coupons LIMIT 0")){}
        }
    }

    private static void initializeAfterSales(Connection c) throws SQLException {
        try(var s=c.createStatement()){
            s.execute(resource("/db/after-sales-v7.sql"));s.execute(resource("/db/return-attempts-v7.sql"));s.execute(resource("/db/refunds-v7.sql"));
            s.execute("CREATE UNIQUE INDEX one_live_return ON after_sales(order_id) WHERE status IN ('REQUESTED','APPROVED','COMPLETED')");
        }
    }

    private static void initializeNotifications(Connection c) throws SQLException {
        try(var s=c.createStatement()){
            s.execute(resource("/db/notifications-v8.sql"));s.execute(resource("/db/message-reads-v8.sql"));
            s.execute("CREATE INDEX notifications_recipient ON notifications(user_id,message_id DESC)");
        }
    }

    private static void initializeOrders(Connection connection) throws SQLException {
        try(var statement=connection.createStatement()){
            statement.execute(resource("/db/orders-v6.sql"));statement.execute(resource("/db/order-items-v6.sql"));statement.execute(resource("/db/checkout-attempts-v6.sql"));
            statement.execute("ALTER TABLE user_coupons ADD COLUMN used_order_id INTEGER REFERENCES orders(order_id)");
        }
    }

    private static void validateCoupons(Connection connection) throws SQLException {
        try(var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT campaign_id,create_key,version,name,threshold_cents,discount_cents,starts_at,ends_at,total,claimed,published,paused FROM coupon_campaigns LIMIT 0")) { }
            try(var rows=statement.executeQuery("SELECT coupon_id,user_id,campaign_id,claimed_at,used FROM user_coupons LIMIT 0")) { }
            try(var rows=statement.executeQuery("SELECT 1 FROM sqlite_schema WHERE type='trigger' AND name='coupon_published_terms_immutable'")) {
                if(!rows.next()) throw new SQLException("Missing coupon integrity trigger.");
            }
        }
    }

    private static void initializeCoupons(Connection connection) throws SQLException {
        try(var statement=connection.createStatement()) {
            statement.execute(resource("/db/coupon-campaigns-v5.sql"));
            statement.execute(resource("/db/user-coupons-v5.sql"));
            statement.execute(resource("/db/coupon-lock-v5.sql"));
        }
    }

    private static void validatePromotions(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT product_id,version,reduction_cents,discount_hundredths,starts_at,ends_at,enabled FROM promotions LIMIT 0")) { }
    }

    private static void initializePromotions(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(resource("/db/promotions-v4.sql")); }
    }

    private static void validateCarts(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT user_id,version FROM carts LIMIT 0")) { }
            try (var rows = statement.executeQuery("SELECT item_id,user_id,product_id,quantity FROM cart_items LIMIT 0")) { }
        }
    }

    private static void validateUsers(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT user_id,username,display_name,password_hash,role,enabled FROM users LIMIT 0")) {
            // Required account columns must exist; no seed data is reapplied.
        }
    }

    private static void validateProducts(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT product_id,name,price_cents,stock,description FROM products LIMIT 0")) {
            // Preparing the query checks the required table and column names without modifying data.
        }
    }

    private record UserSeed(long userId, String username, String displayName, String passwordHash, String role) {
    }

    private static void initializeCarts(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(resource("/db/carts-v3.sql"));
            statement.execute(resource("/db/cart-items-v3.sql"));
        }
    }

    private static void initializeUsers(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(resource("/db/users-v2.sql"));
        }
        final UserSeed[] users;
        try {
            users = new GsonBuilder().setStrictness(Strictness.STRICT).create()
                    .fromJson(resource("/db/demo-users.json"), UserSeed[].class);
            if (users == null || users.length != 3) throw new IllegalArgumentException("Invalid demo accounts.");
        } catch (RuntimeException exception) {
            throw new SQLException("Invalid packaged demo accounts.", exception);
        }
        try (var statement = connection.prepareStatement(
                "INSERT INTO users(user_id,username,display_name,password_hash,role) VALUES(?,?,?,?,?)")) {
            for (UserSeed user : users) {
                statement.setLong(1, user.userId());
                statement.setString(2, user.username());
                statement.setString(3, user.displayName());
                statement.setString(4, user.passwordHash());
                statement.setString(5, user.role());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static int pragma(Connection connection, String name) throws SQLException {
        // Only internal constant pragma names reach this method.
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA " + name)) {
            if (!rows.next()) {
                throw new SQLException("Missing database metadata.");
            }
            return rows.getInt(1);
        }
    }

    private static boolean isEmpty(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT 1 FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%' LIMIT 1")) {
            return !rows.next();
        }
    }

    private static void insertSeeds(Connection connection, List<Product> products) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO products(product_id,name,price_cents,stock,description) VALUES(?,?,?,?,?)")) {
            for (Product product : products) {
                statement.setLong(1, product.productId());
                statement.setString(2, product.name());
                statement.setLong(3, product.priceCents());
                statement.setInt(4, product.stock());
                statement.setString(5, product.description());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<Product> defaultSeeds() throws SQLException {
        try {
            Product[] products = new GsonBuilder().setStrictness(Strictness.STRICT).create()
                    .fromJson(resource("/db/demo-products.json"), Product[].class);
            if (products == null) {
                throw new SQLException("Demo product resource is empty.");
            }
            return List.of(products);
        } catch (RuntimeException exception) {
            throw new SQLException("Invalid packaged demo products.", exception);
        }
    }

    private static String resource(String name) throws SQLException {
        try (var stream = SqliteDatabase.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new SQLException("Missing packaged database resource.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SQLException("Cannot read packaged database resource.", exception);
        }
    }
}
