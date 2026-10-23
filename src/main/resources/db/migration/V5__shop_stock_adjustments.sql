ALTER TABLE stock_movements ALTER COLUMN movement_type TYPE VARCHAR(16);

ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_type CHECK (
    (movement_type = 'receipt' AND id_shop IS NULL AND id_order IS NULL AND shop_name IS NULL)
    OR (movement_type = 'shipment' AND id_order IS NOT NULL AND shop_name IS NOT NULL)
    OR (movement_type IN ('shop_increase', 'shop_decrease')
        AND id_order IS NULL AND shop_name IS NOT NULL)
);

ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_id_shop_fkey;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_id_shop_fkey
    FOREIGN KEY (id_shop) REFERENCES shops (id) ON DELETE SET NULL;

DELETE FROM shop_products WHERE quantity = 0;
