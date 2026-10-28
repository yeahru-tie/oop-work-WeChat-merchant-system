CREATE TABLE products (
    product_id INTEGER PRIMARY KEY CHECK (product_id > 0),
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
    stock INTEGER NOT NULL CHECK (stock BETWEEN 0 AND 2147483647),
    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 500)
) STRICT;
