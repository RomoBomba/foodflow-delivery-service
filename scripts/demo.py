#!/usr/bin/env python3
"""Create local demo accounts, 3 kitchens and 20 dishes; optionally complete one order."""
import argparse
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


class ApiError(Exception):
    def __init__(self, status, code):
        super().__init__(f"HTTP {status}: {code}")
        self.status = status


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None
        self.csrf_header = None
        self.refresh_csrf()

    def refresh_csrf(self):
        token = self.call("GET", "/api/auth/csrf")
        self.csrf, self.csrf_header = token["token"], token["headerName"]

    def call(self, method, path, body=None, form=False, headers=None):
        headers = dict(headers or {})
        data = None
        if body is not None:
            data = (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
        if method != "GET" and self.csrf:
            headers[self.csrf_header] = self.csrf
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=20) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            try:
                code = json.loads(error.read()).get("code", "REQUEST_REJECTED")
            except (ValueError, AttributeError):
                code = "REQUEST_REJECTED"
            raise ApiError(error.code, code) from None

    def login(self, email, password):
        self.call("POST", "/api/auth/login", {"email": email, "password": password}, form=True)
        self.refresh_csrf()

    def register_and_login(self, email, password):
        try:
            self.call("POST", "/api/auth/register", {"email": email, "password": password})
        except ApiError as error:
            if error.status != 409:
                raise
        self.login(email, password)


def seed(base, admin_email, admin_password, demo_password, smoke):
    admin = Api(base)
    admin.login(admin_email, admin_password)
    categories = admin.call("GET", "/api/categories")
    ingredients = admin.call("GET", "/api/ingredients")
    client = Api(base)
    client.register_and_login("client@demo.test", demo_password)
    first_dish = None
    first_kitchen = None
    for index, amount in enumerate((7, 7, 6), start=1):
        kitchen = Api(base)
        kitchen.register_and_login(f"kitchen{index}@demo.test", demo_password)
        try:
            info = kitchen.call("GET", "/api/kitchens/me")
        except ApiError as error:
            if error.status != 404:
                raise
            info = kitchen.call("POST", "/api/kitchens", {
                "name": f"Демо кухня {index}", "description": "Учебные демонстрационные данные",
                "contacts": "Контакты учебной кухни", "deliveryArea": "Санкт-Петербург, учебный район"})
        if info["status"] == "PENDING":
            admin.call("POST", f"/api/admin/kitchens/{info['id']}/approve")
            kitchen.login(f"kitchen{index}@demo.test", demo_password)
        elif info["status"] != "APPROVED":
            raise RuntimeError("Демо кухня отклонена или заблокирована: используйте чистую учебную БД")
        kitchen.call("PATCH", "/api/kitchens/me/availability", {"acceptingOrders": True})
        menu = {dish["name"]: dish for dish in kitchen.call("GET", "/api/kitchens/me/dishes")}
        for number in range(1, amount + 1):
            name = f"Демо блюдо {index}.{number}"
            dish = menu.get(name)
            if dish is None:
                dish = kitchen.call("POST", "/api/kitchens/me/dishes", {
                    "name": name, "description": "Блюдо для демонстрации учебного сценария",
                    "categoryId": categories[(number - 1) % len(categories)]["id"],
                    "ingredientIds": [ingredients[(number - 1) % len(ingredients)]["id"]],
                    "price": 150 + number * 25, "weightGrams": 300, "kcalPerPortion": 200 + number * 50,
                    "availablePortions": 20})
            if first_dish is None:
                first_dish, first_kitchen = dish, kitchen
    print("Подготовлены 3 кухни и 20 блюд; клиент client@demo.test, кухни kitchen1..3@demo.test.")
    if not smoke:
        return
    # Explicit --smoke uses only the demo customer's cart and creates one real test order.
    client.call("DELETE", "/api/cart")
    client.call("PUT", "/api/preferences", {"favoriteCategoryIds": [], "favoriteIngredientIds": [],
                                            "excludedIngredientIds": []})
    client.call("POST", "/api/cart/items", {"dishId": first_dish["id"], "quantity": 1})
    body = {"deliveryAddress": "Учебный адрес, улица Тестовая, дом 10", "phone": "79990000000",
            "items": [{"dishId": first_dish["id"], "quantity": 1}]}
    key = str(uuid.uuid4())
    order = client.call("POST", "/api/orders", body, headers={"Idempotency-Key": key})
    repeated = client.call("POST", "/api/orders", body, headers={"Idempotency-Key": key})
    if repeated["id"] != order["id"]:
        raise RuntimeError("Повтор оформления создал другой заказ")
    for status in ("PREPARING", "IN_DELIVERY", "COMPLETED"):
        first_kitchen.call("PATCH", f"/api/kitchens/me/orders/{order['id']}/status", {"status": status})
    review = client.call("POST", f"/api/orders/{order['id']}/reviews", {
        "items": [{"orderItemId": order["items"][0]["id"], "rating": 5, "reordered": True, "text": "Демонстрационный отзыв"}]})
    visible = client.call("GET", f"/api/dishes/{first_dish['id']}/reviews")
    if not any(item["id"] == review[0]["id"] for item in visible):
        raise RuntimeError("Созданный отзыв отсутствует в публичной выдаче")
    print(f"Полный HTTP-сценарий выполнен. Заказ: {order['id']}; статус: COMPLETED; отзыв опубликован.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--smoke", action="store_true", help="Also place, complete and review one demo order")
    args = parser.parse_args()
    names = ("ADMIN_EMAIL", "ADMIN_PASSWORD", "DEMO_PASSWORD")
    if any(not os.environ.get(name) for name in names):
        parser.error("Задайте ADMIN_EMAIL, ADMIN_PASSWORD и DEMO_PASSWORD в окружении")
    if not 8 <= len(os.environ["DEMO_PASSWORD"]) <= 128:
        parser.error("DEMO_PASSWORD должен содержать 8–128 символов")
    try:
        seed(os.environ.get("FOODFLOW_BASE_URL", "http://localhost:8080"),
             *(os.environ[name] for name in names), args.smoke)
    except (ApiError, RuntimeError, urllib.error.URLError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
