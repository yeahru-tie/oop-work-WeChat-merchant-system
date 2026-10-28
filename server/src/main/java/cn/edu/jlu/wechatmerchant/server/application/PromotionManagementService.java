package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.domain.Product;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Local administrator application service, protected independently from the window gate. */
public final class PromotionManagementService {
    public static final class Conflict extends Exception {
        private static final long serialVersionUID = 1L;
        public Conflict() { super("配置已被修改，请重新载入后再编辑。"); }
    }
    public record Page(List<Product> products, int offset, boolean hasMore) {
        public Page { products = List.copyOf(products); }
    }
    public record Editor(Product product, PromotionConfiguration configuration) { }
    private final SqliteDatabase database;
    private final SessionStore sessions;
    private final ProductDao products;
    private final PromotionDao promotions = new PromotionDao();
    private final PromotionCalculator calculator = new PromotionCalculator();

    public PromotionManagementService(SqliteDatabase database, SessionStore sessions) {
        this.database = database; this.sessions = sessions; this.products = new ProductDao(database);
    }

    public Page list(String token, int offset) throws SQLException {
        if (offset < 0) throw new IllegalArgumentException("分页无效。");
        try (var connection = database.openReadOnlyConnection()) {
            connection.setAutoCommit(false);
            requireAdmin(connection,token);
            var rows = products.findPage(connection,21,offset);
            var result = new Page(rows.size() > 20 ? rows.subList(0,20) : rows,offset,rows.size() > 20);
            connection.commit();
            return result;
        }
    }

    public Editor load(String token, long product) throws SQLException {
        try (var connection = database.openReadOnlyConnection()) {
            connection.setAutoCommit(false);
            requireAdmin(connection,token);
            var result = new Editor(product(connection,product),promotions.find(connection,product).orElse(null));
            connection.commit();
            return result;
        }
    }

    public PromotionPreviewService.Preview preview(String token, long product, int quantity, PromotionTerms terms) throws SQLException {
        try (var connection = database.openReadOnlyConnection()) {
            connection.setAutoCommit(false);
            requireAdmin(connection,token);
            Product current = product(connection,product);
            var result = new PromotionPreviewService.Preview(current,terms,calculator.calculate(current.priceCents(),quantity,terms));
            connection.commit();
            return result;
        }
    }

    public Editor save(String token, PromotionConfiguration draft) throws SQLException, Conflict {
        try (var connection = database.openImmediateConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdmin(connection,token);
                Product current = product(connection,draft.productId());
                long version = promotions.find(connection,draft.productId()).map(c -> c.version()).orElse(0L);
                if (draft.version() != version || version == Long.MAX_VALUE) throw new Conflict();
                var saved = new PromotionConfiguration(draft.productId(),version+1,draft.terms(),draft.startsAt(),draft.endsAt(),draft.enabled());
                promotions.save(connection,saved);
                connection.commit();
                return new Editor(current,saved);
            } catch (SQLException | Conflict | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }

    private Product product(Connection connection, long id) throws SQLException {
        return products.findById(connection,id).orElseThrow(() -> new IllegalArgumentException("商品不存在，请刷新列表。"));
    }

    private void requireAdmin(Connection connection, String token) throws SQLException {
        var identity = sessions.resolve(token).orElseThrow(() -> new SecurityException("管理身份已失效，请重新登录。"));
        if (identity.role() != UserIdentity.Role.ADMIN) throw new SecurityException("仅管理员可维护活动。");
        try (var statement = connection.prepareStatement("SELECT username,display_name,role,enabled FROM users WHERE user_id=?")) {
            statement.setLong(1,identity.userId());
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt("enabled") != 1 || !"ADMIN".equals(rows.getString("role"))
                        || !identity.username().equals(rows.getString("username"))
                        || !identity.displayName().equals(rows.getString("display_name"))) {
                    sessions.revoke(token);
                    throw new SecurityException("管理账号不可用，请重新登录。");
                }
            }
        }
    }
}
