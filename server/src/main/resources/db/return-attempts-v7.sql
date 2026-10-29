CREATE TABLE return_attempts (
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    return_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    state TEXT NOT NULL CHECK(state IN ('PENDING','SUCCESS','FAILED')),
    after_sale_id INTEGER REFERENCES after_sales(after_sale_id),
    failure_code TEXT,
    PRIMARY KEY(user_id,return_key),
    CHECK((state='PENDING' AND after_sale_id IS NULL AND failure_code IS NULL)
       OR (state='SUCCESS' AND after_sale_id IS NOT NULL AND failure_code IS NULL)
       OR (state='FAILED' AND after_sale_id IS NULL AND failure_code IS NOT NULL))
) STRICT
