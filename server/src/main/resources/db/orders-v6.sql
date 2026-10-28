CREATE TABLE orders (
    order_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    checkout_key TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL,
    coupon_json TEXT,
    original_cents TEXT NOT NULL,
    product_reduction_cents TEXT NOT NULL,
    coupon_reduction_cents TEXT NOT NULL,
    paid_cents TEXT NOT NULL,
    UNIQUE(user_id,checkout_key)
) STRICT
