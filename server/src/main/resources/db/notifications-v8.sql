CREATE TABLE notifications (
    message_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER REFERENCES users(user_id),
    event_key TEXT NOT NULL UNIQUE,
    kind TEXT NOT NULL CHECK (kind IN ('ORDER','RETURN','ANNOUNCEMENT')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at INTEGER NOT NULL CHECK (created_at >= 0),
    order_id INTEGER REFERENCES orders(order_id),
    product_id INTEGER REFERENCES products(product_id),
    CHECK ((kind='ANNOUNCEMENT' AND user_id IS NULL AND order_id IS NULL) OR (kind IN ('ORDER','RETURN') AND user_id IS NOT NULL AND order_id IS NOT NULL AND product_id IS NULL))
)
