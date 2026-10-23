ALTER TABLE stock_movements
    ADD COLUMN product_name VARCHAR(100),
    ADD COLUMN unit VARCHAR(5),
    ADD COLUMN shop_name VARCHAR(100);

UPDATE stock_movements m
SET product_name = p.name, unit = p.unit
FROM products p
WHERE p.id = m.id_product;

UPDATE stock_movements m
SET shop_name = s.name
FROM shops s
WHERE s.id = m.id_shop;

ALTER TABLE stock_movements
    ALTER COLUMN product_name SET NOT NULL,
    ALTER COLUMN unit SET NOT NULL,
    ALTER COLUMN id_product DROP NOT NULL;

ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_id_product_fkey;
ALTER TABLE stock_movements
    ADD CONSTRAINT stock_movements_id_product_fkey
    FOREIGN KEY (id_product) REFERENCES products (id) ON DELETE SET NULL;
