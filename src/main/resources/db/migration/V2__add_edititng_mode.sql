ALTER TABLE IF EXISTS family_sync.users
    ADD COLUMN IF NOT EXISTS shopping_list_edit_mode boolean NOT NULL default false;
COMMENT ON COLUMN family_sync.users.shopping_list_edit_mode IS 'Состояние интерфейса: true - режим правки списка, false - режим покупок';
