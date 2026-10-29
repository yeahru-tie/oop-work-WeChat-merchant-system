CREATE TABLE refunds (
    after_sale_id INTEGER PRIMARY KEY REFERENCES after_sales(after_sale_id),
    order_id INTEGER NOT NULL UNIQUE REFERENCES orders(order_id),
    amount_cents TEXT NOT NULL,
    completed_at INTEGER NOT NULL,
    operator_id INTEGER NOT NULL REFERENCES users(user_id)
) STRICT
