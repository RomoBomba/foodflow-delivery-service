CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    user_id UUID NOT NULL,
    dish_id UUID NOT NULL,
    rating INTEGER NOT NULL,
    reordered BOOLEAN NOT NULL,
    review_text VARCHAR(1000),
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    moderation_reason VARCHAR(500),
    moderated_by UUID,
    moderated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_reviews_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_dish FOREIGN KEY (dish_id) REFERENCES dishes (id),
    CONSTRAINT fk_reviews_moderator FOREIGN KEY (moderated_by) REFERENCES users (id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_dish_visible ON reviews (dish_id, visible);
CREATE INDEX idx_reviews_user ON reviews (user_id);
