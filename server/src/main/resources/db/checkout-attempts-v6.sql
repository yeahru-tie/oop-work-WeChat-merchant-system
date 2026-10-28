CREATE TABLE checkout_attempts (
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    checkout_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    state TEXT NOT NULL CHECK(state IN ('PENDING','SUCCESS','FAILED')),
    order_id INTEGER REFERENCES orders(order_id),
    failure_code TEXT,
    PRIMARY KEY(user_id,checkout_key),
    CHECK((state='PENDING' AND order_id IS NULL AND failure_code IS NULL)
       OR (state='SUCCESS' AND order_id IS NOT NULL AND failure_code IS NULL)
       OR (state='FAILED' AND order_id IS NULL AND failure_code IS NOT NULL))
) STRICT
