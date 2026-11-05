CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    kitchen_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH_ON_DELIVERY',
    comment VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    total_price NUMERIC(12, 2) NOT NULL,
    reservation_released BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_orders_kitchen FOREIGN KEY (kitchen_id) REFERENCES kitchens (id),
    CONSTRAINT uk_orders_customer_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'CREATED', 'PREPARING', 'IN_DELIVERY', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT ck_orders_total CHECK (total_price > 0),
    CONSTRAINT ck_orders_payment_method CHECK (payment_method = 'CASH_ON_DELIVERY')
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    dish_id UUID NOT NULL,
    dish_name VARCHAR(120) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    weight_grams INTEGER NOT NULL,
    kcal_per_portion INTEGER NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_dish FOREIGN KEY (dish_id) REFERENCES dishes (id),
    CONSTRAINT ck_order_items_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_order_items_total CHECK (line_total > 0)
);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_id UUID NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_history_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_kitchen_created ON orders (kitchen_id, created_at DESC);
CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_history_order ON order_status_history (order_id, created_at);
