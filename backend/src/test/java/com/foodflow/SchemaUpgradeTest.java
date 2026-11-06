package com.foodflow;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SchemaUpgradeTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test
    void upgradePreservesOrdersStockSnapshotsHistoryAndModeration() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("11").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID user = UUID.randomUUID();
        UUID kitchen = UUID.randomUUID();
        UUID dish = UUID.randomUUID();
        jdbc.update("INSERT INTO users VALUES (?, 'upgrade@example.test', 'test-hash', 'CLIENT', now())", user);
        jdbc.update("""
                INSERT INTO kitchens(id, owner_id, name, description, contacts, delivery_area, status, accepting_orders, created_at)
                VALUES (?, ?, 'Кухня', 'Описание', 'Контакты', 'Район', 'APPROVED', true, now())
                """, kitchen, user);
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        jdbc.update("""
                INSERT INTO dishes(id, kitchen_id, category_id, name, description, price, weight_grams, kcal_per_portion,
                                   available_portions, archived, created_at)
                VALUES (?, ?, ?, 'Новое имя', 'Описание', 999, 300, 250, 13, true, now())
                """, dish, kitchen, category);
        for (String status : List.of("CREATED", "PREPARING", "IN_DELIVERY", "COMPLETED", "CANCELLED")) {
            UUID order = UUID.randomUUID();
            UUID item = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO orders(id, customer_id, kitchen_id, idempotency_key, request_fingerprint, delivery_address,
                                       phone, status, total_price, reservation_released, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'old-fingerprint', 'Тестовый адрес доставки', '79991234567', ?, 250, ?, now(), now())
                    """, order, user, kitchen, status, status, status.equals("CANCELLED"));
            jdbc.update("""
                    INSERT INTO order_items(id, order_id, dish_id, dish_name, unit_price, quantity, line_total, weight_grams, kcal_per_portion)
                    VALUES (?, ?, ?, 'Старое имя', 125, 2, 250, 200, 150)
                    """, item, order, dish);
            jdbc.update("""
                    INSERT INTO order_status_history VALUES (?, ?, ?, ?, ?, 'Историческая причина', now())
                    """, UUID.randomUUID(), order, status.equals("COMPLETED") ? "IN_DELIVERY" : null, status, user);
            if (status.equals("COMPLETED")) {
                insertReview(jdbc, order, item, user, dish, true);
                UUID otherItem = UUID.randomUUID();
                UUID otherDish = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO dishes(id, kitchen_id, category_id, name, description, price, weight_grams, kcal_per_portion,
                                           available_portions, archived, created_at)
                        VALUES (?, ?, ?, 'Второе блюдо', 'Описание', 100, 200, 150, 7, false, now())
                        """, otherDish, kitchen, category);
                jdbc.update("""
                        INSERT INTO order_items(id, order_id, dish_id, dish_name, unit_price, quantity, line_total, weight_grams, kcal_per_portion)
                        VALUES (?, ?, ?, 'Второе блюдо', 100, 1, 100, 200, 150)
                        """, otherItem, order, otherDish);
                jdbc.update("UPDATE orders SET total_price = 350 WHERE id = ?", order);
                insertReview(jdbc, order, otherItem, user, otherDish, false);
            }
        }
        var itemsBefore = jdbc.queryForList("SELECT * FROM order_items ORDER BY id");
        var stockBefore = jdbc.queryForList("SELECT id, available_portions FROM dishes ORDER BY id");
        var ordersBefore = jdbc.queryForList("SELECT id, total_price, request_fingerprint, created_at FROM orders ORDER BY id");
        var moderationBefore = jdbc.queryForList("SELECT id, moderation_reason, moderated_by, moderated_at FROM reviews ORDER BY id");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("SELECT * FROM order_items ORDER BY id")).isEqualTo(itemsBefore);
        assertThat(jdbc.queryForList("SELECT id, available_portions FROM dishes ORDER BY id")).isEqualTo(stockBefore);
        assertThat(jdbc.queryForList("SELECT id, total_price, request_fingerprint, created_at FROM orders ORDER BY id")).isEqualTo(ordersBefore);
        assertThat(jdbc.queryForList("SELECT id, moderation_reason, moderated_by, moderated_at FROM reviews ORDER BY id")).isEqualTo(moderationBefore);
        assertThat(jdbc.queryForList("SELECT status FROM orders ORDER BY status", String.class))
                .containsExactly("CANCELLED", "COMPLETED", "CREATED", "DELIVERING", "PREPARING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE from_status = 'IN_DELIVERY' OR to_status = 'IN_DELIVERY'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE from_status = 'DELIVERING' OR to_status = 'DELIVERING'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE version = 0", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM reservations r JOIN order_items i ON i.id = r.order_item_id JOIN orders o ON o.id = i.order_id
                WHERE r.quantity = i.quantity AND r.status = CASE o.status WHEN 'COMPLETED' THEN 'CONSUMED'
                WHEN 'CANCELLED' THEN 'RELEASED' ELSE 'ACTIVE' END
                """, Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForList("SELECT status FROM reviews ORDER BY status", String.class)).containsExactly("HIDDEN", "VISIBLE");
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'IN_DELIVERY'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE reservations SET status = 'UNKNOWN'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(12);
    }

    private void insertReview(JdbcTemplate jdbc, UUID order, UUID item, UUID user, UUID dish, boolean visible) {
        jdbc.update("""
                INSERT INTO reviews(id, order_id, order_item_id, user_id, dish_id, rating, reordered, review_text,
                                    visible, moderation_reason, moderated_by, moderated_at, created_at)
                VALUES (?, ?, ?, ?, ?, 5, true, 'Исторический отзыв', ?, ?, ?, ?, now())
                """, UUID.randomUUID(), order, item, user, dish, visible, visible ? null : "Причина скрытия",
                visible ? null : user, visible ? null : java.sql.Timestamp.valueOf("2026-01-01 12:00:00"));
    }
}
