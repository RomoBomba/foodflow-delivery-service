CREATE TABLE preferences (
    user_id UUID PRIMARY KEY,
    kcal_min INTEGER,
    kcal_max INTEGER,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_preferences_kcal_min CHECK (kcal_min IS NULL OR kcal_min >= 0),
    CONSTRAINT ck_preferences_kcal_max CHECK (kcal_max IS NULL OR kcal_max >= 0),
    CONSTRAINT ck_preferences_kcal_range CHECK (
        kcal_min IS NULL OR kcal_max IS NULL OR kcal_min <= kcal_max
    )
);

CREATE TABLE preference_categories (
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    PRIMARY KEY (user_id, category_id),
    FOREIGN KEY (user_id) REFERENCES preferences (user_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE TABLE preference_favorite_ingredients (
    user_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    PRIMARY KEY (user_id, ingredient_id),
    FOREIGN KEY (user_id) REFERENCES preferences (user_id) ON DELETE CASCADE,
    FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
);

CREATE TABLE preference_excluded_ingredients (
    user_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    PRIMARY KEY (user_id, ingredient_id),
    FOREIGN KEY (user_id) REFERENCES preferences (user_id) ON DELETE CASCADE,
    FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
);
