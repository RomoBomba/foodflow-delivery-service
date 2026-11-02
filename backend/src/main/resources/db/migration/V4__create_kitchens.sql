CREATE TABLE kitchens (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    contacts VARCHAR(500) NOT NULL,
    delivery_area VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    accepting_orders BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_kitchens_owner
        FOREIGN KEY (owner_id) REFERENCES users (id),

    CONSTRAINT uk_kitchens_owner
        UNIQUE (owner_id),

    CONSTRAINT ck_kitchens_status
        CHECK (status IN (
            'PENDING', 'APPROVED', 'REJECTED', 'BLOCKED'
        ))
);