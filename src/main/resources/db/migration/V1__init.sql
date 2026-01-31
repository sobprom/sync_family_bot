CREATE TABLE users
(
    chat_id   INTEGER PRIMARY KEY,
    family_id TEXT,
    username  TEXT
);

CREATE TABLE shopping_list
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id      INTEGER,
    product_name TEXT NOT NULL,
    is_bought    BOOLEAN  DEFAULT FALSE,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
);