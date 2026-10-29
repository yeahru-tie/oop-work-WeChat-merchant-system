CREATE TABLE message_reads (
    message_id INTEGER NOT NULL REFERENCES notifications(message_id),
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    read_at INTEGER NOT NULL CHECK (read_at >= 0),
    PRIMARY KEY (message_id,user_id)
)
