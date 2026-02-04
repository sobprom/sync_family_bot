CREATE TABLE IF NOT EXISTS families
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    invite_code TEXT UNIQUE NOT NULL
);

ALTER TABLE users
    ADD COLUMN family_id INTEGER;
ALTER TABLE shopping_list
    ADD COLUMN family_id INTEGER;
