CREATE TABLE after_sales (
    after_sale_id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL REFERENCES orders(order_id),
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    return_key TEXT NOT NULL,
    version INTEGER NOT NULL CHECK(version>0),
    status TEXT NOT NULL CHECK(status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED')),
    reason TEXT NOT NULL,
    review_note TEXT NOT NULL DEFAULT '',
    applied_at INTEGER NOT NULL,
    reviewed_at INTEGER NOT NULL DEFAULT 0,
    completed_at INTEGER NOT NULL DEFAULT 0,
    refund_cents TEXT NOT NULL,
    reviewer_id INTEGER REFERENCES users(user_id),
    UNIQUE(user_id,return_key)
) STRICT
