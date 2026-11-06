-- Preserve existing orders and history while adopting the agreed UML names and entities.
ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
UPDATE orders SET status = 'DELIVERING' WHERE status = 'IN_DELIVERY';
UPDATE order_status_history SET from_status = 'DELIVERING' WHERE from_status = 'IN_DELIVERY';
UPDATE order_status_history SET to_status = 'DELIVERING' WHERE to_status = 'IN_DELIVERY';
ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (
    status IN ('CREATED', 'PREPARING', 'DELIVERING', 'COMPLETED', 'CANCELLED')
);
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE order_items ADD CONSTRAINT uk_order_items_dish UNIQUE (order_id, dish_id);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL UNIQUE REFERENCES order_items(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED'))
);
INSERT INTO reservations(id, order_item_id, quantity, status)
SELECT gen_random_uuid(), i.id, i.quantity,
       CASE WHEN o.status = 'CANCELLED' THEN 'RELEASED'
            WHEN o.status = 'COMPLETED' THEN 'CONSUMED' ELSE 'ACTIVE' END
FROM order_items i JOIN orders o ON o.id = i.order_id;
-- No stock adjustment: the old checkout/cancellation already applied it.
ALTER TABLE orders DROP COLUMN reservation_released;

ALTER TABLE reviews ADD COLUMN status VARCHAR(20);
UPDATE reviews SET status = CASE WHEN visible THEN 'VISIBLE' ELSE 'HIDDEN' END;
ALTER TABLE reviews ALTER COLUMN status SET NOT NULL;
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN'));
DROP INDEX idx_reviews_dish_visible;
ALTER TABLE reviews DROP COLUMN visible;
CREATE INDEX idx_reviews_dish_status ON reviews(dish_id, status);
