CREATE SCHEMA IF NOT EXISTS family_sync;

-- Таблица семей
CREATE TABLE IF NOT EXISTS family_sync.families
(
    id          bigserial primary key,
    created_at  timestamptz not null default now(),
    invite_code text unique not null
);

-- Таблица пользователей
CREATE TABLE IF NOT EXISTS family_sync.users
(
    chat_id         int8 primary key,
    family_id       int8 references family_sync.families (id),
    last_message_id int4,
    created_at      timestamptz not null default now(),
    username        text not null
);

-- Таблица списка покупок
CREATE TABLE IF NOT EXISTS family_sync.shopping_list
(
    id           bigserial primary key,
    family_id    int8 references family_sync.families (id),
    created_at   timestamptz not null default now(),
    is_bought    boolean not null default false,
    product_name text not null

);

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_shopping_list_family ON family_sync.shopping_list (family_id);
CREATE INDEX IF NOT EXISTS idx_users_family ON family_sync.users (family_id);