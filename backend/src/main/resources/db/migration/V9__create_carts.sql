CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    kitchen_id UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_carts_kitchen FOREIGN KEY (kitchen_id) REFERENCES kitchens (id)
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    dish_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    price_at_add NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uk_cart_items_dish UNIQUE (cart_id, dish_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_dish FOREIGN KEY (dish_id) REFERENCES dishes (id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_cart_items_price CHECK (price_at_add > 0)
);

CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);
