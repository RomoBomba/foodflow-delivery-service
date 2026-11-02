CREATE TABLE users (
    id UUID PRIMARY KEY,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_users_email_normalized
        UNIQUE (email_normalized),

    CONSTRAINT ck_users_role
        CHECK (role IN ('CLIENT', 'KITCHEN', 'ADMIN'))
);