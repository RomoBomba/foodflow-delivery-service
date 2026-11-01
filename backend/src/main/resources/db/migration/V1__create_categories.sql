CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT ck_categories_name_not_blank
        CHECK (length(trim(name)) > 0)
);