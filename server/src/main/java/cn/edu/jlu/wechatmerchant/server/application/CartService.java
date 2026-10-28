package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.server.persistence.CartDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import java.sql.Connection;
import java.sql.SQLException;

public final class CartService {
    public static final class Rejected extends Exception {
        private static final long serialVersionUID = 1L;
        private final String code;
        public Rejected(String code) { super(code); this.code=code; }
        public String code() { return code; }
    }
    private final SqliteDatabase database;
    private final CartDao carts = new CartDao();
    private final PromotionPricing pricing;

    public CartService(SqliteDatabase database) { this(database, new PromotionPricing()); }
    public CartService(SqliteDatabase database, PromotionPricing pricing) { this.database=database; this.pricing=pricing; }

    public CartSnapshot list(long user) throws SQLException {
        try (Connection connection=database.openReadOnlyConnection()) {
            connection.setAutoCommit(false);
            CartSnapshot result=pricing.cart(connection,carts.read(connection,user));
            connection.commit();
            return result;
        }
    }

    public CartSnapshot add(long user,long product,int quantity,long version) throws SQLException,Rejected {
        return mutate(user,version,(connection,before) -> {
            if (product<=0 || quantity<=0) throw new Rejected("INVALID_ARGUMENT");
            var existing=before.items().stream().filter(i -> i.productId()==product).findFirst();
            if (existing.isEmpty() && before.items().size()>=CartSnapshot.MAX_ITEMS) throw new Rejected("CART_LIMIT");
            long total=(long)quantity+existing.map(CartSnapshot.Item::quantity).orElse(0);
            int stock=stock(connection,product);
            if (total>stock || total>Integer.MAX_VALUE) throw new Rejected("OUT_OF_STOCK");
            carts.saveQuantity(connection,user,product,(int)total);
        });
    }

    public CartSnapshot edit(long user,long item,Integer quantity,long version) throws SQLException,Rejected {
        return mutate(user,version,(connection,before) -> {
            var existing=before.items().stream().filter(i -> i.itemId()==item).findFirst().orElseThrow(() -> new Rejected("NOT_FOUND"));
            if (quantity==null) {
                if (carts.remove(connection,user,item)!=1) throw new Rejected("NOT_FOUND");
            } else {
                if (quantity<=0) throw new Rejected("INVALID_ARGUMENT");
                if (quantity>existing.quantity() && quantity>existing.stock()) throw new Rejected("OUT_OF_STOCK");
                carts.saveQuantity(connection,user,existing.productId(),quantity);
            }
        });
    }

    private CartSnapshot mutate(long user,long version,Change change) throws SQLException,Rejected {
        if (user<=0 || version<0) throw new Rejected("INVALID_ARGUMENT");
        try (Connection connection=database.openImmediateConnection()) {
            connection.setAutoCommit(false);
            try {
                CartSnapshot before=carts.read(connection,user);
                if (before.cartVersion()!=version || version==Long.MAX_VALUE) throw new Rejected("CART_CHANGED");
                // Establish the parent row before inserting an item; rollback also removes this row on failure.
                carts.advance(connection,user,version);
                change.apply(connection,before);
                carts.advance(connection,user,version+1);
                CartSnapshot result=pricing.cart(connection,carts.read(connection,user));
                connection.commit();
                return result;
            } catch (SQLException | Rejected | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }

    private static int stock(Connection connection,long product) throws SQLException,Rejected {
        try (var statement=connection.prepareStatement("SELECT stock FROM products WHERE product_id=?")) {
            statement.setLong(1,product);
            try (var rows=statement.executeQuery()) {
                if (!rows.next()) throw new Rejected("NOT_FOUND");
                return rows.getInt(1);
            }
        }
    }

    @FunctionalInterface private interface Change {
        void apply(Connection connection,CartSnapshot before) throws SQLException,Rejected;
    }
}
