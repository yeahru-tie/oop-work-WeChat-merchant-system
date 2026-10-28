CREATE TABLE cart_items (
    item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES carts(user_id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 2147483647),
    UNIQUE (user_id, product_id)
) STRICT;
