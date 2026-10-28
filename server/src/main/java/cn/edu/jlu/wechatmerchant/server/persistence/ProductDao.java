package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.server.domain.Product;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProductDao {
    private final SqliteDatabase database;

    public ProductDao(SqliteDatabase database) {
        this.database = database;
    }

    public SqliteDatabase database() { return database; }

    public List<Product> findPage(int limit, int offset) throws SQLException {
        if (limit < 1 || limit > 101 || offset < 0) {
            throw new IllegalArgumentException("Invalid DAO pagination.");
        }
        try (var connection = database.openReadOnlyConnection()) {
            return findPage(connection, limit, offset);
        }
    }

    public List<Product> findPage(Connection connection, int limit, int offset) throws SQLException {
        if (limit < 1 || limit > 101 || offset < 0) throw new IllegalArgumentException("Invalid pagination.");
        try (var statement = connection.prepareStatement(
                        "SELECT product_id,name,price_cents,stock,description FROM products ORDER BY product_id LIMIT ? OFFSET ?")) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (var rows = statement.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rows.next()) {
                    products.add(readProduct(rows));
                }
                return List.copyOf(products);
            }
        }
    }

    public Optional<Product> findById(long productId) throws SQLException {
        if (productId < 1) throw new IllegalArgumentException("商品编号须为正整数。");
        try (var connection = database.openReadOnlyConnection()) {
            return findById(connection, productId);
        }
    }

    public Optional<Product> findById(Connection connection, long productId) throws SQLException {
        try (var statement = connection.prepareStatement(
                        "SELECT product_id,name,price_cents,stock,description FROM products WHERE product_id=?")) {
            statement.setLong(1, productId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readProduct(rows)) : Optional.empty();
            }
        }
    }

    private static Product readProduct(ResultSet rows) throws SQLException {
        try {
            return new Product(rows.getLong("product_id"), rows.getString("name"), rows.getLong("price_cents"),
                    rows.getInt("stock"), rows.getString("description"));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid stored product data.", exception);
        }
    }
}
