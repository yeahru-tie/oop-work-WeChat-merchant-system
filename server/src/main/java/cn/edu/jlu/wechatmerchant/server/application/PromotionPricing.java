package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.PromotionPrice;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionCalculator;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionTerms;
import cn.edu.jlu.wechatmerchant.server.persistence.PromotionDao;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;

/** Current effective configuration only; drafts are never read here. */
public final class PromotionPricing {
    private final PromotionDao promotions = new PromotionDao();
    private final PromotionCalculator calculator = new PromotionCalculator();
    private final Clock clock;
    public PromotionPricing() { this(Clock.systemUTC()); }
    public PromotionPricing(Clock clock) { this.clock = clock; }
    public Instant now() { return clock.instant(); }

    public PromotionPrice price(Connection connection, long product, long cents, Instant now) throws SQLException {
        var configuration = promotions.find(connection, product);
        var effective = configuration.filter(config -> config.activeAt(now));
        var terms = effective.map(config -> config.terms()).orElse(new PromotionTerms(0, 1000));
        long estimated = calculator.calculate(cents, 1, terms).estimatedTotalCents().longValueExact();
        return new PromotionPrice(configuration.map(config -> config.version()).orElse(0L), cents,
                terms.reductionCents(), terms.discountHundredths(), cents - Math.min(cents, terms.reductionCents()), estimated);
    }

    public CartSnapshot cart(Connection connection, CartSnapshot snapshot) throws SQLException {
        return cart(connection,snapshot,now());
    }

    public CartSnapshot cart(Connection connection, CartSnapshot snapshot,Instant now) throws SQLException {
        var items = new ArrayList<CartSnapshot.Item>();
        for (var item : snapshot.items()) {
            items.add(new CartSnapshot.Item(item.itemId(), item.productId(), item.name(), item.priceCents(), item.stock(),
                    item.quantity(), price(connection,item.productId(),item.priceCents(),now)));
        }
        return new CartSnapshot(snapshot.cartVersion(),items);
    }
}
