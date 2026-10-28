package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.server.domain.Product;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PriceBreakdown;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionCalculator;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionTerms;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import java.sql.SQLException;
import java.util.Objects;

/** Explicit draft trial, with public product data only; it neither saves nor activates a promotion. */
public final class PromotionPreviewService {
    public record Preview(Product product, PromotionTerms terms, PriceBreakdown breakdown) { }

    private final ProductDao products;
    private final PromotionCalculator calculator = new PromotionCalculator();

    public PromotionPreviewService(ProductDao products) { this.products = Objects.requireNonNull(products); }

    public Preview preview(long productId, int quantity, PromotionTerms terms) throws SQLException {
        Objects.requireNonNull(terms);
        if (quantity < 1) throw new IllegalArgumentException("试算数量须为正整数。");
        Product product = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("商品不存在。"));
        return new Preview(product, terms, calculator.calculate(product.priceCents(), quantity, terms));
    }
}
