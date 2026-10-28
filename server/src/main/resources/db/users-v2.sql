CREATE TABLE users (
    user_id INTEGER PRIMARY KEY CHECK (user_id > 0),
    username TEXT NOT NULL UNIQUE CHECK (length(username) BETWEEN 3 AND 32),
    display_name TEXT NOT NULL CHECK (length(display_name) BETWEEN 1 AND 80),
    password_hash TEXT NOT NULL CHECK (length(password_hash) BETWEEN 80 AND 160),
    role TEXT NOT NULL CHECK (role IN ('BUYER','ADMIN')),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1))
) STRICT;
