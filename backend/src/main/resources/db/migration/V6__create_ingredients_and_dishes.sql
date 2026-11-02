CREATE TABLE ingredients (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,

    CONSTRAINT uk_ingredients_name UNIQUE (name),
    CONSTRAINT ck_ingredients_name_not_blank
        CHECK (length(trim(name)) > 0)
);

CREATE TABLE dishes (
    id UUID PRIMARY KEY,
    kitchen_id UUID NOT NULL,
    category_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    weight_grams INTEGER NOT NULL,
    kcal_per_portion INTEGER NOT NULL,
    available_portions INTEGER NOT NULL DEFAULT 0,
    photo_url VARCHAR(2048),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_dishes_kitchen
        FOREIGN KEY (kitchen_id) REFERENCES kitchens (id),

    CONSTRAINT fk_dishes_category
        FOREIGN KEY (category_id) REFERENCES categories (id),

    CONSTRAINT ck_dishes_name_not_blank
        CHECK (length(trim(name)) > 0),

    CONSTRAINT ck_dishes_price_positive
        CHECK (price > 0),

    CONSTRAINT ck_dishes_weight_positive
        CHECK (weight_grams > 0),

    CONSTRAINT ck_dishes_kcal_nonnegative
        CHECK (kcal_per_portion >= 0),

    CONSTRAINT ck_dishes_portions_nonnegative
        CHECK (available_portions >= 0)
);

CREATE TABLE dish_ingredients (
    dish_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,

    PRIMARY KEY (dish_id, ingredient_id),

    CONSTRAINT fk_dish_ingredients_dish
        FOREIGN KEY (dish_id) REFERENCES dishes (id),

    CONSTRAINT fk_dish_ingredients_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
);

CREATE INDEX idx_dishes_kitchen_id ON dishes (kitchen_id);
CREATE INDEX idx_dishes_category_id ON dishes (category_id);
CREATE INDEX idx_dish_ingredients_ingredient_id
    ON dish_ingredients (ingredient_id);