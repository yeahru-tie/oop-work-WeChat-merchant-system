CREATE TABLE user_coupons (
    coupon_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    campaign_id INTEGER NOT NULL REFERENCES coupon_campaigns(campaign_id),
    claimed_at INTEGER NOT NULL,
    used INTEGER NOT NULL DEFAULT 0 CHECK(used IN (0,1)),
    UNIQUE(user_id,campaign_id)
) STRICT
