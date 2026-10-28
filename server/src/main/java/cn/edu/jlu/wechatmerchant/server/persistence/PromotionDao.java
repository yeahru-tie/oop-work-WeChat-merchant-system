package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionConfiguration;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionTerms;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Uses the caller's snapshot/transaction, never commits independently. */
public final class PromotionDao {
    public Optional<PromotionConfiguration> find(Connection connection, long product) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM promotions WHERE product_id=?")) {
            statement.setLong(1, product);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                try {
                    return Optional.of(new PromotionConfiguration(product, rows.getLong("version"),
                            new PromotionTerms(rows.getLong("reduction_cents"), rows.getInt("discount_hundredths")),
                            Instant.ofEpochMilli(rows.getLong("starts_at")), Instant.ofEpochMilli(rows.getLong("ends_at")),
                            rows.getInt("enabled") == 1));
                } catch (RuntimeException exception) { throw new SQLException("Invalid stored promotion.", exception); }
            }
        }
    }

    public void save(Connection connection, PromotionConfiguration config) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO promotions(product_id,version,reduction_cents,discount_hundredths,starts_at,ends_at,enabled)
                VALUES(?,?,?,?,?,?,?) ON CONFLICT(product_id) DO UPDATE SET version=excluded.version,
                reduction_cents=excluded.reduction_cents,discount_hundredths=excluded.discount_hundredths,
                starts_at=excluded.starts_at,ends_at=excluded.ends_at,enabled=excluded.enabled
                """)) {
            statement.setLong(1, config.productId());
            statement.setLong(2, config.version());
            statement.setLong(3, config.terms().reductionCents());
            statement.setInt(4, config.terms().discountHundredths());
            statement.setLong(5, config.startsAt().toEpochMilli());
            statement.setLong(6, config.endsAt().toEpochMilli());
            statement.setInt(7, config.enabled() ? 1 : 0);
            statement.executeUpdate();
        }
    }
}
