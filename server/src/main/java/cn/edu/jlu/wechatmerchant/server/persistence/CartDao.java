package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

/** All methods use the caller's connection and never commit independently. */
public final class CartDao {
    public long version(Connection connection, long user) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT version FROM carts WHERE user_id=?")) {
            statement.setLong(1,user);
            try (var rows=statement.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; }
        }
    }

    public CartSnapshot read(Connection connection, long user) throws SQLException {
        long version = version(connection,user);
        var items = new ArrayList<CartSnapshot.Item>();
        try (var statement = connection.prepareStatement("""
                SELECT i.item_id,i.product_id,p.name,p.price_cents,p.stock,i.quantity
                FROM cart_items i JOIN products p ON p.product_id=i.product_id
                WHERE i.user_id=? ORDER BY i.item_id LIMIT 101
                """)) {
            statement.setLong(1,user);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) items.add(new CartSnapshot.Item(rows.getLong(1),rows.getLong(2),rows.getString(3),
                        rows.getLong(4),rows.getInt(5),rows.getInt(6)));
            }
        }
        try { return new CartSnapshot(version,items); }
        catch (IllegalArgumentException exception) { throw new SQLException("Invalid stored cart.",exception); }
    }

    public void saveQuantity(Connection connection,long user,long product,int quantity) throws SQLException {
        try (var statement=connection.prepareStatement("""
                INSERT INTO cart_items(user_id,product_id,quantity) VALUES(?,?,?)
                ON CONFLICT(user_id,product_id) DO UPDATE SET quantity=excluded.quantity
                """)) {
            statement.setLong(1,user); statement.setLong(2,product); statement.setInt(3,quantity);
            statement.executeUpdate();
        }
    }

    public int remove(Connection connection,long user,long item) throws SQLException {
        try (var statement=connection.prepareStatement("DELETE FROM cart_items WHERE user_id=? AND item_id=?")) {
            statement.setLong(1,user); statement.setLong(2,item); return statement.executeUpdate();
        }
    }

    public void advance(Connection connection,long user,long version) throws SQLException {
        try (var statement=connection.prepareStatement("""
                INSERT INTO carts(user_id,version) VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET version=excluded.version
                """)) {
            statement.setLong(1,user); statement.setLong(2,version); statement.executeUpdate();
        }
    }
}
