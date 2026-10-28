package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.server.domain.Product;
import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import java.sql.SQLException;
import java.util.List;

public final class ProductCatalog {
    public record Page(List<ProductPage.Item> items, int limit, int offset, boolean hasMore) {
        public Page {
            items = List.copyOf(items);
        }
    }

    private final ProductDao products;
    private final PromotionPricing pricing;

    public ProductCatalog(ProductDao products) {
        this(products, new PromotionPricing());
    }

    public ProductCatalog(ProductDao products, PromotionPricing pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    public Page list(int limit, int offset) throws SQLException {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid product pagination.");
        }
        try (var connection = products.database().openReadOnlyConnection()) {
            connection.setAutoCommit(false);
            List<Product> rows = products.findPage(connection, limit + 1, offset);
            boolean more = rows.size() > limit;
            var now = pricing.now();
            var items = new java.util.ArrayList<ProductPage.Item>();
            for (Product p : more ? rows.subList(0,limit) : rows) {
                items.add(new ProductPage.Item(p.productId(),p.name(),p.priceCents(),p.stock(),p.description(),
                        pricing.price(connection,p.productId(),p.priceCents(),now)));
            }
            connection.commit();
            return new Page(items, limit, offset, more);
        }
    }
}
