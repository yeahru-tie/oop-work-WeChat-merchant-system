CREATE TABLE coupon_campaigns (
    campaign_id INTEGER PRIMARY KEY AUTOINCREMENT,
    create_key TEXT NOT NULL UNIQUE,
    version INTEGER NOT NULL CHECK(version>0),
    name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 80),
    threshold_cents INTEGER NOT NULL CHECK(threshold_cents>=0),
    discount_cents INTEGER NOT NULL CHECK(discount_cents>0),
    starts_at INTEGER NOT NULL,
    ends_at INTEGER NOT NULL CHECK(ends_at>starts_at),
    total INTEGER NOT NULL CHECK(total BETWEEN 1 AND 2147483647),
    claimed INTEGER NOT NULL DEFAULT 0 CHECK(claimed>=0 AND claimed<=total),
    published INTEGER NOT NULL DEFAULT 0 CHECK(published IN (0,1)),
    paused INTEGER NOT NULL DEFAULT 0 CHECK(paused IN (0,1)),
    CHECK(published=1 OR claimed=0)
) STRICT
