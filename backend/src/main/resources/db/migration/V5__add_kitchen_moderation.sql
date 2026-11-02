ALTER TABLE kitchens
    ADD COLUMN moderation_reason VARCHAR(500),
    ADD COLUMN moderated_by UUID,
    ADD COLUMN moderated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE kitchens
    ADD CONSTRAINT fk_kitchens_moderator
        FOREIGN KEY (moderated_by) REFERENCES users (id);