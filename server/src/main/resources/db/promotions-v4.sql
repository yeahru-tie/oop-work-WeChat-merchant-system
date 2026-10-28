CREATE TABLE promotions (
    product_id INTEGER PRIMARY KEY REFERENCES products(product_id),
    version INTEGER NOT NULL CHECK(version > 0),
    reduction_cents INTEGER NOT NULL CHECK(reduction_cents >= 0),
    discount_hundredths INTEGER NOT NULL CHECK(discount_hundredths BETWEEN 1 AND 1000),
    starts_at INTEGER NOT NULL,
    ends_at INTEGER NOT NULL CHECK(ends_at > starts_at),
    enabled INTEGER NOT NULL CHECK(enabled IN (0,1))
) STRICT
