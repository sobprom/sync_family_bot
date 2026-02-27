ALTER TABLE IF EXISTS family_sync.users
    ADD COLUMN IF NOT EXISTS editing_product_id int8
        REFERENCES family_sync.shopping_list (id) ON DELETE SET NULL;

COMMENT ON COLUMN family_sync.users.editing_product_id IS 'ID продукта из списка покупок, который пользователь редактирует в данный момент. Если не NULL, вводимый текст обновляет этот продукт.';