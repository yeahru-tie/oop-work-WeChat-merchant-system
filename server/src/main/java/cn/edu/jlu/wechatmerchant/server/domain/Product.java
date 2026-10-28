package cn.edu.jlu.wechatmerchant.server.domain;

public record Product(long productId, String name, long priceCents, int stock, String description) {
    public Product {
        if (productId <= 0 || name == null || name.isBlank()
                || name.codePointCount(0, name.length()) > 120 || priceCents < 0 || stock < 0
                || description == null || description.codePointCount(0, description.length()) > 500) {
            throw new IllegalArgumentException("Invalid product data.");
        }
    }
}
