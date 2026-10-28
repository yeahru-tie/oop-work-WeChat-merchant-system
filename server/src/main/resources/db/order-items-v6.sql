CREATE TABLE order_items (
    order_id INTEGER NOT NULL REFERENCES orders(order_id),
    item_seq INTEGER NOT NULL CHECK(item_seq>=0),
    cart_item_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    price_cents INTEGER NOT NULL CHECK(price_cents>=0),
    quantity INTEGER NOT NULL CHECK(quantity>0),
    promotion_json TEXT NOT NULL,
    PRIMARY KEY(order_id,item_seq)
) STRICT
