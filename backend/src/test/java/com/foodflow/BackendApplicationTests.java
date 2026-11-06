package com.foodflow;

import com.foodflow.catalog.dish.*;
import com.foodflow.catalog.kitchen.*;
import com.foodflow.common.ApiException;
import com.foodflow.feedback.*;
import com.foodflow.ordering.*;
import com.foodflow.recommendation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=8")
@AutoConfigureMockMvc
class BackendApplicationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired DishService dishes;
    @Autowired KitchenService kitchens;
    @Autowired KitchenModerationService moderation;
    @Autowired PreferenceService preferences;
    @Autowired RecommendationService recommendations;
    @Autowired ReviewService reviews;
    @Autowired ReviewQueryService reviewQueries;
    @Autowired CatalogService catalog;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;

    private UUID clientId;
    private UUID kitchenId;
    private UUID dishId;
    private UUID otherDishId;
    private UUID categoryId;
    private UUID ingredientId;
    private static final String CLIENT = "client@example.test";
    private static final String OTHER = "other@example.test";
    private static final String OWNER = "kitchen@example.test";
    private static final String ADMIN = "admin@example.test";
    private static final String PASSWORD = "test-password-123";
    private static String passwordHash;

    @BeforeEach
    void fixtures() {
        // Only the disposable Testcontainers database is ever cleaned.
        jdbc.execute("TRUNCATE users CASCADE");
        if (passwordHash == null) passwordHash = passwords.encode(PASSWORD);
        clientId = insertUser(CLIENT, "CLIENT");
        insertUser(OTHER, "CLIENT");
        UUID ownerId = insertUser(OWNER, "KITCHEN");
        insertUser(ADMIN, "ADMIN");
        categoryId = jdbc.queryForObject("SELECT id FROM categories ORDER BY id LIMIT 1", UUID.class);
        ingredientId = jdbc.queryForObject("SELECT id FROM ingredients ORDER BY id LIMIT 1", UUID.class);
        kitchenId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kitchens(id, owner_id, name, description, contacts, delivery_area, status,
                                     accepting_orders, created_at)
                VALUES (?, ?, 'Кухня', 'Описание', 'Контакты', 'Район', 'APPROVED', true, now())
                """, kitchenId, ownerId);
        dishId = dish(10);
        otherDishId = dish(10);
    }

    private UUID insertUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users VALUES (?, ?, ?, ?, now())", id, email, passwordHash, role);
        return id;
    }

    private UUID dish(int stock) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dishes(id, kitchen_id, category_id, name, description, price, weight_grams,
                                   kcal_per_portion, available_portions, archived, created_at)
                VALUES (?, ?, ?, 'Суп', 'Описание супа', 100.00, 300, 250, ?, false, now())
                """, id, kitchenId, categoryId, stock);
        jdbc.update("INSERT INTO dish_ingredients VALUES (?, ?)", id, ingredientId);
        return id;
    }

    private OrderCreateRequest request(UUID dish) {
        return new OrderCreateRequest("Санкт-Петербург, улица Тестовая, 10", "+7 (999) 123-45-67", null,
                List.of(new OrderLineRequest(dish, 1)), false, null);
    }

    private OrderResponse checkout(String email, String key) {
        carts.add(email, new CartItemRequest(dishId, 1, false));
        return create(email, key, request(dishId));
    }

    private OrderResponse create(String email, String key, OrderCreateRequest request) {
        return orders.create(email, key, request).order();
    }

    private String reservationStatus(OrderResponse order) {
        return jdbc.queryForObject("SELECT status FROM reservations WHERE order_item_id = ?",
                String.class, order.items().getFirst().id());
    }

    private int stock() {
        return jdbc.queryForObject("SELECT available_portions FROM dishes WHERE id = ?", Integer.class, dishId);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private void conflict(String code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    private <T> T transaction(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> action.get());
    }

    private void awaitBlockedTransaction() {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND cardinality(pg_blocking_pids(pid)) > 0
                """, Integer.class) > 0);
    }

    private Object outcome(Callable<?> action) throws Exception {
        try { return action.call(); }
        catch (ApiException error) { return error.getCode(); }
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> { ready.countDown(); start.await(10, TimeUnit.SECONDS); return outcome(first); });
            var b = executor.submit(() -> { ready.countDown(); start.await(10, TimeUnit.SECONDS); return outcome(second); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void cleanDatabaseMigratesAndValidatesSchema() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(12);
        assertThat(count("categories")).isPositive();
        assertThat(count("ingredients")).isPositive();
    }

    @ParameterizedTest
    @CsvSource({"false,archive", "true,archive", "false,stock", "true,stock", "false,price", "true,price"})
    void cartUsesDishStateCommittedWhileWaitingForKitchen(boolean update, String change) throws Exception {
        if (update) carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<Object> pending = transaction(() -> {
                jdbc.queryForObject("SELECT id FROM kitchens WHERE id = ? FOR UPDATE", UUID.class, kitchenId);
                Future<Object> future = executor.submit(() -> outcome(() -> update
                        ? carts.updateQuantity(CLIENT, dishId, 2)
                        : carts.add(CLIENT, new CartItemRequest(dishId, 1, false))));
                awaitBlockedTransaction();
                switch (change) {
                    case "archive" -> jdbc.update("UPDATE dishes SET archived = true WHERE id = ?", dishId);
                    case "stock" -> jdbc.update("UPDATE dishes SET available_portions = 0 WHERE id = ?", dishId);
                    case "price" -> jdbc.update("UPDATE dishes SET price = 150 WHERE id = ?", dishId);
                    default -> throw new AssertionError(change);
                }
                return future;
            });
            Object result = pending.get(20, TimeUnit.SECONDS);
            switch (change) {
                case "archive" -> assertThat(result).isEqualTo("DISH_ARCHIVED");
                case "stock" -> assertThat(result).isEqualTo("INSUFFICIENT_STOCK");
                case "price" -> {
                    CartResponse response = (CartResponse) result;
                    assertThat(response.items().getFirst().currentPrice()).isEqualByComparingTo("150.00");
                    assertThat(response.items().getFirst().priceAtAdd()).isEqualByComparingTo(update ? "100.00" : "150.00");
                }
            }
        }
    }

    @Test
    void distinctMultilineDetailsCannotReuseKey() {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        var first = new OrderCreateRequest("Адрес доставки\n79991111111", "79992222222", "Комментарий",
                request(dishId).items(), false, null);
        var collision = new OrderCreateRequest("Адрес доставки", "79991111111", "79992222222\nКомментарий",
                request(dishId).items(), false, null);
        create(CLIENT, "multiline-key", first);
        conflict("IDEMPOTENCY_KEY_REUSED", () -> create(CLIENT, "multiline-key", collision));
        assertThat(count("orders")).isEqualTo(1);
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void retryAfterWaitingReturnsOrderEvenWhenCartWasRefilled() throws Exception {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<OrderResponse> pending = transaction(() -> {
                jdbc.queryForObject("SELECT id FROM carts WHERE user_id = ? FOR UPDATE", UUID.class, clientId);
                Future<OrderResponse> future = executor.submit(() -> create(CLIENT, "refilled-key", request(dishId)));
                awaitBlockedTransaction();
                create(CLIENT, "refilled-key", request(dishId));
                carts.add(CLIENT, new CartItemRequest(otherDishId, 1, false));
                return future;
            });
            assertThat(pending.get(20, TimeUnit.SECONDS).items().getFirst().dishId()).isEqualTo(dishId);
            assertThat(carts.get(CLIENT).items()).singleElement().satisfies(item -> assertThat(item.dishId()).isEqualTo(otherDishId));
            assertThat(count("orders")).isEqualTo(1);
            assertThat(stock()).isEqualTo(9);
        }
    }

    @Test
    void onlyOneCustomerCanBuyLastPortion() throws Exception {
        jdbc.update("UPDATE dishes SET available_portions = 1 WHERE id = ?", dishId);
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        carts.add(OTHER, new CartItemRequest(dishId, 1, false));
        var results = race(() -> create(CLIENT, "last-first", request(dishId)),
                () -> create(OTHER, "last-second", request(dishId)));
        assertThat(results.stream().filter(OrderResponse.class::isInstance)).hasSize(1);
        assertThat(results).contains("INSUFFICIENT_STOCK");
        assertThat(stock()).isZero();
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("cart_items")).isEqualTo(1);
    }

    @Test
    void parallelIdenticalRequestsReserveOnce() throws Exception {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        var results = race(() -> create(CLIENT, "parallel-key", request(dishId)),
                () -> create(CLIENT, "parallel-key", request(dishId)));
        assertThat(results).allMatch(OrderResponse.class::isInstance);
        assertThat(((OrderResponse) results.get(0)).id()).isEqualTo(((OrderResponse) results.get(1)).id());
        assertThat(stock()).isEqualTo(9);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("order_status_history")).isEqualTo(1);
        assertThat(count("cart_items")).isZero();
    }

    @Test
    void keyCannotBeReusedWithChangedPayloadAndIsScopedToCustomer() {
        var original = checkout(CLIENT, "shared-key");
        conflict("IDEMPOTENCY_KEY_REUSED", () -> create(CLIENT, "shared-key", request(otherDishId)));
        assertThat(create(CLIENT, "shared-key", request(dishId)).id()).isEqualTo(original.id());
        assertThat(checkout(OTHER, "shared-key").id()).isNotEqualTo(original.id());
    }

    @Test
    void failureAfterReservationRollsBackEveryWrite() {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        jdbc.execute("""
                CREATE FUNCTION fail_history() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected test failure'; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_history BEFORE INSERT ON order_status_history FOR EACH ROW EXECUTE FUNCTION fail_history()");
        try {
            assertThatThrownBy(() -> create(CLIENT, "rollback-key", request(dishId)))
                    .isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected test failure");
            assertThat(stock()).isEqualTo(10);
            assertThat(count("orders")).isZero();
            assertThat(count("order_items")).isZero();
            assertThat(count("reservations")).isZero();
            assertThat(count("order_status_history")).isZero();
            assertThat(carts.get(CLIENT).items()).hasSize(1);
        } finally {
            jdbc.execute("DROP TRIGGER fail_history ON order_status_history");
            jdbc.execute("DROP FUNCTION fail_history()");
        }
        assertThat(create(CLIENT, "rollback-key", request(dishId)).status()).isEqualTo(OrderStatus.CREATED);
    }

    private String priceQuote(OrderCreateRequest request) {
        ApiException error = catchThrowableOfType(() -> create(CLIENT, "price-key", request), ApiException.class);
        assertThat(error.getCode()).isEqualTo("PRICE_CHANGED");
        return (String) error.getDetails().get("updatedPriceFingerprint");
    }

    @Test
    void priceConfirmationIsBoundToExactQuoteAndOrderKeepsSnapshots() {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        jdbc.update("UPDATE dishes SET price = 125 WHERE id = ?", dishId);
        String oldQuote = priceQuote(request(dishId));
        var confirmed = new OrderCreateRequest(request(dishId).deliveryAddress(), request(dishId).phone(), null,
                request(dishId).items(), true, oldQuote);
        jdbc.update("UPDATE dishes SET price = 140 WHERE id = ?", dishId);
        String newQuote = priceQuote(confirmed);
        assertThat(newQuote).isNotEqualTo(oldQuote);
        assertThat(count("orders")).isZero();
        var order = create(CLIENT, "price-key", new OrderCreateRequest(confirmed.deliveryAddress(), confirmed.phone(),
                null, confirmed.items(), true, newQuote));
        assertThat(order.totalPrice()).isEqualByComparingTo("140.00");
        jdbc.update("UPDATE dishes SET price = 999, name = 'Другое имя', archived = true WHERE id = ?", dishId);
        var saved = orders.getMine(CLIENT, order.id());
        assertThat(saved.items().getFirst().dishName()).isEqualTo("Суп");
        assertThat(saved.totalPrice()).isEqualByComparingTo("140.00");
    }

    @Test
    void acceptingAndCancellingCannotBothSucceed() throws Exception {
        var order = checkout(CLIENT, "cancel-race");
        var results = race(() -> orders.cancelByCustomer(CLIENT, order.id(), order.version()),
                () -> orders.changeStatusByKitchen(OWNER, order.id(), new OrderStatusRequest(OrderStatus.PREPARING, order.version(), null)));
        assertThat(results.stream().filter(OrderResponse.class::isInstance)).hasSize(1);
        OrderStatus status = orders.getMine(CLIENT, order.id()).status();
        assertThat(status).isIn(OrderStatus.CANCELLED, OrderStatus.PREPARING);
        assertThat(stock()).isEqualTo(status == OrderStatus.CANCELLED ? 10 : 9);
        assertThat(count("order_status_history")).isEqualTo(2);
    }

    @Test
    void concurrentCancellationsReturnPortionsExactlyOnce() throws Exception {
        var order = checkout(CLIENT, "cancel-twice");
        var results = race(() -> orders.cancelByCustomer(CLIENT, order.id(), order.version()),
                () -> orders.cancelByCustomer(CLIENT, order.id(), order.version()));
        assertThat(results.stream().filter(OrderResponse.class::isInstance)).hasSize(1);
        assertThat(results).contains("ORDER_VERSION_CONFLICT");
        assertThat(stock()).isEqualTo(10);
        assertThat(reservationStatus(order)).isEqualTo("RELEASED");
        assertThat(count("order_status_history")).isEqualTo(2);
    }

    @Test
    void kitchenMustGiveReasonAndCannotSkipStages() {
        var order = checkout(CLIENT, "stages-key");
        conflict("CANCELLATION_REASON_REQUIRED", () -> orders.changeStatusByKitchen(OWNER, order.id(), new OrderStatusRequest(OrderStatus.CANCELLED, order.version(), " ")));
        conflict("INVALID_ORDER_TRANSITION", () -> orders.changeStatusByKitchen(OWNER, order.id(), new OrderStatusRequest(OrderStatus.COMPLETED, order.version(), null)));
        orders.changeStatusByKitchen(OWNER, order.id(), new OrderStatusRequest(OrderStatus.CANCELLED, order.version(), "Нет возможности приготовить"));
        assertThat(stock()).isEqualTo(10);
        assertThat(orders.getMine(CLIENT, order.id()).history().getLast().reason()).isEqualTo("Нет возможности приготовить");
    }

    @Test
    void blockedKitchenCannotSellButCanCompleteExistingOrder() {
        var order = checkout(CLIENT, "blocked-key");
        carts.add(OTHER, new CartItemRequest(dishId, 1, false));
        moderation.block(kitchenId, ADMIN, "Проверка кухни");
        conflict("KITCHEN_CLOSED", () -> create(OTHER, "blocked-new", request(dishId)));
        conflict("KITCHEN_CLOSED", () -> carts.add(CLIENT, new CartItemRequest(dishId, 1, false)));
        assertThat(kitchens.getMine(OWNER).acceptingOrders()).isFalse();
        complete(order);
        assertThat(orders.getMine(CLIENT, order.id()).status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(stock()).isEqualTo(9);
    }

    private void complete(OrderResponse order) {
        for (OrderStatus status : List.of(OrderStatus.PREPARING, OrderStatus.DELIVERING, OrderStatus.COMPLETED)) {
            order = orders.changeStatusByKitchen(OWNER, order.id(), new OrderStatusRequest(status, order.version(), null));
        }
    }

    @Test
    void excludedIngredientsRemoveCartItemsAndBlockPurchases() {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        preferences.update(CLIENT, new PreferenceRequest(Set.of(), Set.of(), Set.of(ingredientId), null, null));
        assertThat(carts.get(CLIENT).items()).isEmpty();
        conflict("EXCLUDED_INGREDIENT", () -> carts.add(CLIENT, new CartItemRequest(dishId, 1, false)));
        assertThat(recommendations.checkCompatibility(CLIENT, dishId).compatible()).isFalse();
        assertThat(recommendations.recommend(CLIENT, 0, 20).content()).isEmpty();
        // A stale or externally restored cart is revalidated at checkout too.
        UUID cartId = carts.get(CLIENT).id();
        jdbc.update("UPDATE carts SET kitchen_id = ? WHERE id = ?", kitchenId, cartId);
        jdbc.update("INSERT INTO cart_items VALUES (?, ?, ?, 1, 100)", UUID.randomUUID(), cartId, dishId);
        conflict("EXCLUDED_INGREDIENT", () -> create(CLIENT, "excluded-key", request(dishId)));
    }

    @Test
    void preferenceChangeWaitsForCartAndRechecksNewlyAddedItems() throws Exception {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<PreferenceResponse> pending = transaction(() -> {
                jdbc.queryForObject("SELECT id FROM carts WHERE user_id = ? FOR UPDATE", UUID.class, clientId);
                Future<PreferenceResponse> future = executor.submit(() -> preferences.update(CLIENT,
                        new PreferenceRequest(Set.of(), Set.of(), Set.of(ingredientId), null, null)));
                awaitBlockedTransaction();
                carts.add(CLIENT, new CartItemRequest(otherDishId, 1, false));
                return future;
            });
            pending.get(20, TimeUnit.SECONDS);
            assertThat(carts.get(CLIENT).items()).isEmpty();
        }
    }

    @Test
    void reviewsRequireCompletedOwnedOrderAndModerationUpdatesAggregates() {
        var order = checkout(CLIENT, "review-key");
        var request = new OrderReviewRequest(List.of(new ReviewItemRequest(order.items().getFirst().id(), 5, true, "Вкусно")));
        conflict("ORDER_NOT_COMPLETED", () -> reviews.create(CLIENT, order.id(), request));
        complete(order);
        conflict("ORDER_NOT_FOUND", () -> reviews.create(OTHER, order.id(), request));
        var review = reviews.create(CLIENT, order.id(), request).getFirst();
        conflict("REVIEW_ALREADY_EXISTS", () -> reviews.create(CLIENT, order.id(), request));
        assertThat(review.status()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(reviewQueries.summary(dishId).averageRating()).isEqualTo(5.0);
        assertThat(reviewQueries.summary(dishId).repeatOrderPercent()).isEqualTo(100);
        conflict("MODERATION_REASON_REQUIRED", () -> reviews.hide(ADMIN, review.id(), " "));
        assertThat(reviews.hide(ADMIN, review.id(), "Нарушение правил").status()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(reviewQueries.summary(dishId).reviewCount()).isZero();
        assertThat(reviewQueries.summary(dishId).averageRating()).isNull();
        assertThat(reviewQueries.summary(dishId).repeatOrderPercent()).isNull();
        assertThat(reviews.findVisibleByDish(dishId)).isEmpty();
        assertThat(reviews.findForAdmin(false)).hasSize(1);
    }

    @Test
    void protectedEndpointsRequireAuthenticationRolesAndOwnership() throws Exception {
        var order = checkout(CLIENT, "security-key");
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("AUTHENTICATION_REQUIRED")).andExpect(jsonPath("message").isString());
        mvc.perform(get("/api/orders/" + order.id()).with(user(OTHER).roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(user(OTHER).roles("CLIENT")).with(csrf())
                        .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/reviews").with(user(CLIENT).roles("CLIENT"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/kitchens/me/orders").with(user(CLIENT).roles("CLIENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").with(user(OWNER).roles("KITCHEN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(user(CLIENT).roles("CLIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(user(CLIENT).roles("CLIENT")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dishes/" + dishId)).andExpect(status().isOk())
                .andExpect(jsonPath("deliveryAddress").doesNotExist()).andExpect(jsonPath("phone").doesNotExist());
    }

    @Test
    void anotherKitchenCannotChangeOrderOrDish() throws Exception {
        UUID stranger = insertUser("stranger@example.test", "KITCHEN");
        jdbc.update("""
                INSERT INTO kitchens(id, owner_id, name, description, contacts, delivery_area, status, accepting_orders, created_at)
                VALUES (?, ?, 'Другая', 'Описание', 'Контакты', 'Район', 'APPROVED', true, now())
                """, UUID.randomUUID(), stranger);
        var order = checkout(CLIENT, "ownership-key");
        mvc.perform(patch("/api/kitchens/me/orders/" + order.id() + "/status")
                        .with(user("stranger@example.test").roles("KITCHEN")).with(csrf())
                        .contentType("application/json").content("{\"status\":\"PREPARING\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/kitchens/me/dishes/" + dishId)
                        .with(user("stranger@example.test").roles("KITCHEN")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(orders.getMine(CLIENT, order.id()).status()).isEqualTo(OrderStatus.CREATED);
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        String json = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.token");
    }

    @Test
    void realLoginRotatesCsrfAndLogoutInvalidatesSessionButKeepsCart() throws Exception {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        var session = new MockHttpSession();
        String oldToken = csrfToken(session);
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", oldToken)
                        .contentType("application/x-www-form-urlencoded")
                        .param("email", CLIENT).param("password", PASSWORD))
                .andExpect(status().isNoContent());
        String newToken = csrfToken(session);
        mvc.perform(delete("/api/cart").session(session).header("X-CSRF-TOKEN", oldToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ACCESS_DENIED"))
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("email").value(CLIENT));
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", newToken)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", csrfToken(session))
                .param("email", CLIENT).param("password", PASSWORD)).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").session(session)).andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"quantity\":0}", "{\"dishId\":null,\"quantity\":1}"})
    void malformedCartRequestsReturn400(String json) throws Exception {
        mvc.perform(post("/api/cart/items").with(user(CLIENT).roles("CLIENT")).with(csrf())
                        .contentType("application/json").content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").exists());
        assertThat(count("cart_items")).isZero();
    }

    @Test
    void fullHttpOrderLifecycleWithOptionalFlagsOmitted() throws Exception {
        mvc.perform(post("/api/cart/items").with(user(CLIENT).roles("CLIENT")).with(csrf())
                        .contentType("application/json").content("{\"dishId\":\"" + dishId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("items.length()").value(1));
        String body = """
                {"deliveryAddress":"Санкт-Петербург, Тестовая 10", "phone":"79991234567",
                 "items":[{"dishId":"%s","quantity":1}]}
                """.formatted(dishId);
        var created = mvc.perform(post("/api/orders").with(user(CLIENT).roles("CLIENT")).with(csrf())
                        .header("Idempotency-Key", "http-lifecycle").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("totalPrice").value(100))
                .andExpect(jsonPath("status").value("CREATED")).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String itemId = com.jayway.jsonpath.JsonPath.read(created, "$.items[0].id");
        mvc.perform(post("/api/orders").with(user(CLIENT).roles("CLIENT")).with(csrf())
                        .header("Idempotency-Key", "http-lifecycle").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
        long version = 0;
        for (String next : List.of("PREPARING", "DELIVERING", "COMPLETED")) {
            mvc.perform(patch("/api/kitchens/me/orders/" + id + "/status").with(user(OWNER).roles("KITCHEN")).with(csrf())
                            .contentType("application/json").content("{\"status\":\"" + next + "\",\"expectedVersion\":" + version + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("status").value(next))
                    .andExpect(jsonPath("version").value(++version));
        }
        mvc.perform(post("/api/orders/" + id + "/reviews").with(user(CLIENT).roles("CLIENT")).with(csrf())
                        .contentType("application/json").content("""
                                {"items":[{"orderItemId":"%s","rating":5,"reordered":true,"text":"Вкусно"}]}
                                """.formatted(itemId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].rating").value(5));
        mvc.perform(get("/api/dishes/" + dishId + "/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/orders/" + id).with(user(CLIENT).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("history.length()").value(4));
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void registrationCannotGrantAdminAndEmailIsNormalized() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("""
                {"email":" New@Example.Test ","password":"test-password-123","role":"ADMIN"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("role").value("CLIENT"))
                .andExpect(jsonPath("email").value("new@example.test")).andExpect(jsonPath("passwordHash").doesNotExist());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email_normalized = 'new@example.test'", String.class);
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("""
                {"email":"new@example.test","password":"test-password-123"}
                """)).andExpect(status().isConflict());
    }

    @Test
    void kitchenApplicationApprovalAndDishManagementWorkWithoutSql() throws Exception {
        String json = mvc.perform(post("/api/kitchens").with(user(OTHER).roles("CLIENT")).with(csrf())
                        .contentType("application/json").content("""
                                {"name":"Новая кухня","description":"Домашние блюда","contacts":"79991234567","deliveryArea":"Центр"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mvc.perform(post("/api/admin/kitchens/" + id + "/approve").with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("APPROVED"));
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email_normalized = ?", String.class, OTHER)).isEqualTo("KITCHEN");
        var owner = user(OTHER).roles("KITCHEN");
        mvc.perform(patch("/api/kitchens/me/availability").with(owner).with(csrf())
                .contentType("application/json").content("{\"acceptingOrders\":true}")).andExpect(status().isOk());
        String dishJson = """
                {"name":"Каша","description":"Сытный завтрак","categoryId":"%s","ingredientIds":["%s"],
                 "price":120.50,"weightGrams":200,"kcalPerPortion":200,"availablePortions":3}
                """.formatted(categoryId, ingredientId);
        String dish = mvc.perform(post("/api/kitchens/me/dishes").with(owner).with(csrf())
                        .contentType("application/json").content(dishJson)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String createdId = com.jayway.jsonpath.JsonPath.read(dish, "$.id");
        mvc.perform(put("/api/kitchens/me/dishes/" + createdId).with(owner).with(csrf())
                .contentType("application/json").content(dishJson.replace("120.50", "150.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("price").value(150));
        mvc.perform(delete("/api/kitchens/me/dishes/" + createdId).with(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/dishes/" + createdId)).andExpect(status().isNotFound());
    }

    @Test
    void replacementRequiresConfirmationAndFailedReplacementPreservesCart() {
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        UUID secondOwner = insertUser("second@example.test", "KITCHEN");
        UUID secondKitchen = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kitchens(id, owner_id, name, description, contacts, delivery_area, status, accepting_orders, created_at)
                VALUES (?, ?, 'Другая', 'Описание', 'Контакты', 'Район', 'APPROVED', true, now())
                """, secondKitchen, secondOwner);
        jdbc.update("UPDATE dishes SET kitchen_id = ? WHERE id = ?", secondKitchen, otherDishId);
        conflict("CART_KITCHEN_MISMATCH", () -> carts.add(CLIENT, new CartItemRequest(otherDishId, 1, false)));
        assertThat(carts.get(CLIENT).items().getFirst().dishId()).isEqualTo(dishId);
        carts.add(CLIENT, new CartItemRequest(otherDishId, 1, true));
        assertThat(carts.get(CLIENT).items()).singleElement().satisfies(item -> assertThat(item.dishId()).isEqualTo(otherDishId));
        assertThat(carts.get(CLIENT).kitchenId()).isEqualTo(secondKitchen);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void cartQuantityAndDistinctItemLimitsAreEnforced() {
        jdbc.update("UPDATE dishes SET available_portions = 200 WHERE id = ?", dishId);
        carts.add(CLIENT, new CartItemRequest(dishId, 99, false));
        conflict("CART_QUANTITY_LIMIT", () -> carts.add(CLIENT, new CartItemRequest(dishId, 1, false)));
        for (int i = 0; i < 29; i++) carts.add(CLIENT, new CartItemRequest(dish(1), 1, false));
        conflict("CART_ITEMS_LIMIT", () -> carts.add(CLIENT, new CartItemRequest(otherDishId, 1, false)));
        assertThat(carts.get(CLIENT).items()).hasSize(30);
    }

    @Test
    void preferencesValidateConflictsAndRecommendationsExplainScores() {
        conflict("PREFERENCE_INGREDIENT_CONFLICT", () -> preferences.update(CLIENT,
                new PreferenceRequest(Set.of(), Set.of(ingredientId), Set.of(ingredientId), null, null)));
        conflict("INVALID_KCAL_RANGE", () -> preferences.update(CLIENT,
                new PreferenceRequest(Set.of(), Set.of(), Set.of(), 500, 100)));
        preferences.update(CLIENT, new PreferenceRequest(Set.of(categoryId), Set.of(ingredientId), Set.of(), 200, 300));
        var result = recommendations.recommend(CLIENT, 0, 20);
        assertThat(result.scoringEnabled()).isTrue();
        assertThat(result.content()).allSatisfy(item -> {
            assertThat(item.score()).isEqualTo(100);
            assertThat(item.explanation()).hasSize(3);
        });
        assertThat(result.content().stream().map(item -> item.dish().id()).toList()).isSorted();
        assertThat(recommendations.recommend(CLIENT, Integer.MAX_VALUE, 100).content()).isEmpty();
    }

    @Test
    void requestsWithInvalidHeadersAndParametersReturnStructured400() throws Exception {
        mvc.perform(post("/api/orders").with(user(CLIENT).roles("CLIENT")).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/dishes/not-a-uuid")).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/dishes?size=101")).andExpect(status().isBadRequest()).andExpect(jsonPath("code").exists());
    }

    @Test
    void legacyFingerprintRetriesRemainSafeAfterUpgrade() throws Exception {
        var request = new OrderCreateRequest("Адрес доставки\n79991111111", "79992222222", "Комментарий",
                request(dishId).items(), false, null);
        carts.add(CLIENT, new CartItemRequest(dishId, 1, false));
        var order = create(CLIENT, "legacy-key", request);
        String legacy = request.deliveryAddress() + "\n" + request.phone() + "\n" + request.comment()
                + "\nfalse\n\n" + dishId + ":1";
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("UPDATE orders SET request_fingerprint = ? WHERE id = ?", hash, order.id());
        assertThat(create(CLIENT, "legacy-key", request).id()).isEqualTo(order.id());
        var collision = new OrderCreateRequest("Адрес доставки", "79991111111", "79992222222\nКомментарий",
                request.items(), false, null);
        conflict("IDEMPOTENCY_KEY_REUSED", () -> create(CLIENT, "legacy-key", collision));
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void fractionalQuantitiesAreRejectedRatherThanTruncated() throws Exception {
        mvc.perform(post("/api/cart/items").with(user(CLIENT).roles("CLIENT")).with(csrf())
                .contentType("application/json").content("{\"dishId\":\"" + dishId + "\",\"quantity\":1.5}"))
                .andExpect(status().isBadRequest());
        assertThat(count("cart_items")).isZero();
    }

    @Test
    void ratingsUseVisibleAnswersAndRoundToSpecification() {
        for (int rating : List.of(5, 4, 3)) {
            var order = checkout(CLIENT, "rating-key-" + rating);
            complete(order);
            reviews.create(CLIENT, order.id(), new OrderReviewRequest(List.of(
                    new ReviewItemRequest(order.items().getFirst().id(), rating, rating != 3, null))));
        }
        var summary = reviewQueries.summary(dishId);
        assertThat(summary.averageRating()).isEqualTo(4.0);
        assertThat(summary.repeatOrderPercent()).isEqualTo(67);
        assertThat(summary.reviewCount()).isEqualTo(3);
        assertThat(summary.repeatOrderResponses()).isEqualTo(3);
        reviews.hide(ADMIN, reviews.findVisibleByDish(dishId).getFirst().id(), "Причина");
        assertThat(reviewQueries.summary(dishId).reviewCount()).isEqualTo(2);
        assertThat(reviewQueries.summary(dishId).repeatOrderPercent()).isEqualTo(100);
    }

    @Test
    void parallelFirstCartCreationDoesNotLoseItems() throws Exception {
        var results = race(() -> carts.add(CLIENT, new CartItemRequest(dishId, 1, false)),
                () -> carts.add(CLIENT, new CartItemRequest(dishId, 1, false)));
        assertThat(results).allMatch(CartResponse.class::isInstance);
        assertThat(count("carts")).isEqualTo(1);
        assertThat(carts.get(CLIENT).items().getFirst().quantity()).isEqualTo(2);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void concurrentReviewsAndModerationPreserveSingleRecords() throws Exception {
        var order = checkout(CLIENT, "review-race");
        complete(order);
        var request = new OrderReviewRequest(List.of(new ReviewItemRequest(order.items().getFirst().id(), 5, true, null)));
        var results = race(() -> reviews.create(CLIENT, order.id(), request), () -> reviews.create(CLIENT, order.id(), request));
        assertThat(results.stream().filter(List.class::isInstance)).hasSize(1);
        assertThat(results).contains("REVIEW_ALREADY_EXISTS");
        var review = reviews.findVisibleByDish(dishId).getFirst();
        var hidden = race(() -> reviews.hide(ADMIN, review.id(), "Причина 1"), () -> reviews.hide(ADMIN, review.id(), "Причина 2"));
        assertThat(hidden.stream().filter(ReviewResponse.class::isInstance)).hasSize(1);
        assertThat(hidden).contains("REVIEW_ALREADY_HIDDEN");
        assertThat(reviewQueries.summary(dishId).reviewCount()).isZero();
        assertThat(count("reviews")).isEqualTo(1);
    }

    @Test
    void catalogPaginationAndHiddenDishCompatibilityDoNotLeakOrFail() throws Exception {
        mvc.perform(get("/api/dishes?page=2147483647&size=100")).andExpect(status().isOk())
                .andExpect(jsonPath("content.length()").value(0)).andExpect(jsonPath("totalElements").value(2));
        jdbc.update("UPDATE dishes SET archived = true WHERE id = ?", dishId);
        conflict("DISH_NOT_FOUND", () -> recommendations.checkCompatibility(CLIENT, dishId));
        jdbc.update("UPDATE kitchens SET status = 'PENDING' WHERE id = ?", kitchenId);
        conflict("DISH_NOT_FOUND", () -> recommendations.checkCompatibility(CLIENT, otherDishId));
        mvc.perform(get("/api/dishes")).andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(0));
    }
    @Test
    void reservationsFollowOrderLifecycleAndVersionsAdvanceOnce() {
        var order = checkout(CLIENT, "reservation-lifecycle");
        assertThat(order.version()).isZero();
        assertThat(reservationStatus(order)).isEqualTo("ACTIVE");
        for (OrderStatus next : List.of(OrderStatus.PREPARING, OrderStatus.DELIVERING, OrderStatus.COMPLETED)) {
            long previous = order.version();
            order = orders.changeStatusByKitchen(OWNER, order.id(), new OrderStatusRequest(next, previous, null));
            assertThat(order.version()).isEqualTo(previous + 1);
            assertThat(reservationStatus(order)).isEqualTo(next == OrderStatus.COMPLETED ? "CONSUMED" : "ACTIVE");
            assertThat(stock()).isEqualTo(9);
        }
        var completed = order;
        conflict("ORDER_NOT_CANCELLABLE", () -> orders.cancelByCustomer(CLIENT, completed.id(), completed.version()));
        conflict("INVALID_ORDER_TRANSITION", () -> orders.changeStatusByKitchen(OWNER, completed.id(),
                new OrderStatusRequest(OrderStatus.CANCELLED, completed.version(), "Поздняя отмена")));
        assertThat(reservationStatus(order)).isEqualTo("CONSUMED");
        assertThat(count("order_status_history")).isEqualTo(4);
    }

    @Test
    void staleVersionCannotChangeStatusOrReleaseStock() {
        var order = checkout(CLIENT, "stale-version");
        var preparing = orders.changeStatusByKitchen(OWNER, order.id(), new OrderStatusRequest(OrderStatus.PREPARING, 0L, null));
        conflict("ORDER_VERSION_CONFLICT", () -> orders.changeStatusByKitchen(OWNER, order.id(),
                new OrderStatusRequest(OrderStatus.DELIVERING, 0L, null)));
        conflict("ORDER_VERSION_CONFLICT", () -> orders.cancelByCustomer(CLIENT, order.id(), 0));
        assertThat(orders.getMine(CLIENT, order.id()).version()).isEqualTo(preparing.version());
        assertThat(reservationStatus(order)).isEqualTo("ACTIVE");
        assertThat(stock()).isEqualTo(9);
        assertThat(count("order_status_history")).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expectedVersion\":-1}", "{\"expectedVersion\":0.5}"})
    void cancellationRequiresNonNegativeIntegerVersion(String body) throws Exception {
        var order = checkout(CLIENT, "cancel-input");
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(user(CLIENT).roles("CLIENT")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(reservationStatus(order)).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "KITCHEN"})
    void cancellationEndpointSupportsBothOwnersAndReturnsNewVersion(String role) throws Exception {
        var order = checkout(CLIENT, "http-cancel");
        var actor = user(role.equals("CLIENT") ? CLIENT : OWNER).roles(role);
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(actor).with(csrf())
                        .contentType("application/json").content("{\"expectedVersion\":0,\"reason\":\"Нет продуктов\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("CANCELLED"))
                .andExpect(jsonPath("version").value(1));
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(actor).with(csrf())
                        .contentType("application/json").content("{\"expectedVersion\":0,\"reason\":\"Повтор\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("ORDER_VERSION_CONFLICT"))
                .andExpect(jsonPath("currentVersion").value(1));
        assertThat(reservationStatus(order)).isEqualTo("RELEASED");
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void kitchenCancelRequiresReasonAndOwnership() throws Exception {
        var order = checkout(CLIENT, "kitchen-cancel");
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(user(OWNER).roles("KITCHEN")).with(csrf())
                        .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("CANCELLATION_REASON_REQUIRED"));
        UUID otherOwner = insertUser("foreign-kitchen@example.test", "KITCHEN");
        jdbc.update("""
                INSERT INTO kitchens(id, owner_id, name, description, contacts, delivery_area, status, accepting_orders, created_at)
                VALUES (?, ?, 'Чужая', 'Описание', 'Контакты', 'Район', 'APPROVED', true, now())
                """, UUID.randomUUID(), otherOwner);
        mvc.perform(post("/api/orders/" + order.id() + "/cancel").with(user("foreign-kitchen@example.test").roles("KITCHEN")).with(csrf())
                        .contentType("application/json").content("{\"expectedVersion\":0,\"reason\":\"Причина\"}"))
                .andExpect(status().isNotFound());
        assertThat(reservationStatus(order)).isEqualTo("ACTIVE");
    }

    @Test
    void cancellationRollsBackStockReservationVersionAndHistoryOnFailure() {
        var order = checkout(CLIENT, "cancel-rollback");
        jdbc.execute("""
                CREATE FUNCTION fail_cancel_history() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected cancellation failure'; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_cancel_history BEFORE INSERT ON order_status_history FOR EACH ROW EXECUTE FUNCTION fail_cancel_history()");
        try {
            assertThatThrownBy(() -> orders.cancelByCustomer(CLIENT, order.id(), order.version()))
                    .isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected cancellation failure");
            assertThat(stock()).isEqualTo(9);
            assertThat(reservationStatus(order)).isEqualTo("ACTIVE");
            assertThat(orders.getMine(CLIENT, order.id()).version()).isZero();
            assertThat(orders.getMine(CLIENT, order.id()).status()).isEqualTo(OrderStatus.CREATED);
            assertThat(count("order_status_history")).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER fail_cancel_history ON order_status_history");
            jdbc.execute("DROP FUNCTION fail_cancel_history()");
        }
        orders.cancelByCustomer(CLIENT, order.id(), 0);
        assertThat(reservationStatus(order)).isEqualTo("RELEASED");
    }

    @Test
    void failedLoginReturnsStructuredErrorWithoutCredentials() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/x-www-form-urlencoded")
                        .param("email", CLIENT).param("password", "wrong-password"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("message").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("wrong-password"))));
    }

    @Test
    void onlyOwnerCanReadHiddenReviewsOfTheirOrder() throws Exception {
        var order = checkout(CLIENT, "own-hidden-review");
        complete(order);
        var review = reviews.create(CLIENT, order.id(), new OrderReviewRequest(List.of(
                new ReviewItemRequest(order.items().getFirst().id(), 5, true, "Текст автора")))).getFirst();
        reviews.hide(ADMIN, review.id(), "Причина модерации");
        String path = "/api/orders/" + order.id() + "/reviews";
        mvc.perform(get(path).with(user(CLIENT).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(review.id().toString()))
                .andExpect(jsonPath("$[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$[0].moderationReason").value("Причина модерации"));
        mvc.perform(get(path).with(user(OTHER).roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user(OWNER).roles("KITCHEN"))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dishes/" + dishId + "/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(reviewQueries.summary(dishId).reviewCount()).isZero();
    }

}
