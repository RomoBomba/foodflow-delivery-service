# HTTP API FoodFlow для frontend

Базовый адрес локального backend — `http://localhost:8080`. UUID передаются строками. Даты — ISO 8601 UTC. Денежные поля JSON — числа в рублях; итог рассчитывает сервер. Пользовательские тексты следует выводить как текст, без `dangerouslySetInnerHTML`.

Для локальной разработки настройте запуск из [README](../README.md). На HTTPS-стенде frontend и API доступны с одного origin через Nginx.

## Сессия и CSRF

1. `GET /api/auth/csrf` возвращает `{ "token": "...", "headerName": "X-CSRF-TOKEN" }` и создаёт сессию.
2. Все изменяющие запросы отправляют cookie и токен в указанном заголовке, включая регистрацию и вход.
3. `POST /api/auth/register` принимает JSON `{ "email": "...", "password": "..." }`, возвращает 201 и `{id,email,role}`. Пароль 8–128 символов. Регистрация не выполняет вход.
4. `POST /api/auth/login` принимает **form-urlencoded** `email` и `password`; успех — 204. После входа нужно заново получить CSRF-токен: предыдущий больше не действует.
5. `GET /api/auth/me` возвращает `{id,email,role}`. `POST /api/auth/logout` с CSRF возвращает 204 и уничтожает сессию. Перед следующим входом нужен новый токен.

Cookie `JSESSIONID`: HttpOnly, SameSite=Lax, Secure на HTTPS-стенде; неактивная сессия живёт 30 минут. После перезапуска backend нужен новый вход, корзина и заказы сохраняются в БД. Роль в сессии обновляется после повторного входа. После одобрения кухни роль аккаунта становится KITCHEN; это отдельный кабинет, клиентские маршруты требуют CLIENT.

В Vite используйте прокси `/api` на `http://localhost:8080` и относительные URL. Cookie передаются при `credentials: 'same-origin'` (значение fetch по умолчанию). CORS для произвольных origin не включён; запросы напрямую с другого origin не являются настроенным способом интеграции.

## Каталог и кухня

| Метод | URL | Доступ / результат |
| --- | --- | --- |
| GET | `/api/health` | Публично; `{status,application}` |
| GET | `/api/categories`, `/api/ingredients` | Публично; массив `{id,name}` |
| GET | `/api/dishes` | Публичный каталог; `{content,page,size,totalElements,totalPages}` |
| GET | `/api/dishes/{id}` | Публичная карточка, 404 для архивного блюда или неодобренной кухни |
| POST | `/api/kitchens` | CLIENT; заявка, 201 |
| GET | `/api/kitchens/me` | CLIENT/KITCHEN; своя заявка или кухня |
| PATCH | `/api/kitchens/me/availability` | KITCHEN; `{acceptingOrders:true}` |
| GET/POST | `/api/kitchens/me/dishes` | KITCHEN; меню / создание блюда (201) |
| PUT/DELETE | `/api/kitchens/me/dishes/{id}` | Своя одобренная кухня; полное обновление / архивирование (204) |
| GET | `/api/admin/kitchens?status=PENDING` | ADMIN; массив заявок указанного статуса |
| POST | `/api/admin/kitchens/{id}/approve` | ADMIN; одобрение |
| POST | `/api/admin/kitchens/{id}/reject` | ADMIN; `{reason:"..."}` |
| POST | `/api/admin/kitchens/{id}/block` | ADMIN; `{reason:"..."}` |

Заявка: `{name,description,contacts,deliveryArea}`. Ограничения длины: 120/1000/500/200; поля обязательны. Статусы кухни: PENDING, APPROVED, REJECTED, BLOCKED.

Создание и PUT блюда принимают:

```json
{
  "name": "Суп",
  "description": "Описание блюда",
  "categoryId": "UUID категории",
  "ingredientIds": ["UUID ингредиента"],
  "price": 250.50,
  "weightGrams": 300,
  "kcalPerPortion": 250,
  "availablePortions": 10,
  "photoUrl": null
}
```

Состав содержит 1–50 ингредиентов. Название ≤120, описание ≤1000, фото ≤2048 символов и только http/https. Цена положительная, до 10 цифр перед точкой и 2 после; масса — положительное целое, ккал и порции — неотрицательные целые. Пустой URL заменяйте `null`. Архивное блюдо нельзя редактировать.

Фильтры каталога: `kitchenId`, `categoryId`, `minPrice`, `maxPrice`, `availableOnly=false`. Пагинация: `page=0`, `size=20`, максимум 100. `sort=priceAsc|priceDesc|relevance`; `score` — алиас `relevance`. Персональная сортировка требует сессии CLIENT; без неё 401. При пустом профиле персональная сортировка использует обычный порядок по цене. Несовместимые блюда исключаются из персональной выдачи, но могут быть в обычном каталоге.

Карточка содержит `id,kitchenId,kitchenName,name,description,category,ingredients,price,weightGrams,kcalPerPortion,availablePortions,available,photoUrl,averageRating,reviewCount,repeatOrderPercent,repeatOrderResponses`. Категория и ингредиенты — объекты `{id,name}`. При отсутствии отзывов `averageRating` и `repeatOrderPercent` равны `null`, счётчики — 0. `available=false` не обязательно скрывает блюдо из обычного каталога.

## Предпочтения и рекомендации

CLIENT: `GET/PUT /api/preferences`. PUT заменяет профиль:

```json
{
  "favoriteCategoryIds": [],
  "favoriteIngredientIds": [],
  "excludedIngredientIds": [],
  "kcalMin": null,
  "kcalMax": null
}
```

В каждом списке до 100 UUID. Пропущенные списки трактуются как пустые. Ответ содержит эти поля, `userId` и `updatedAt`. Исключённые блюда удаляются из текущей корзины в той же транзакции. После успешного PUT обновите отображение корзины.

`GET /api/recommendations?page=0&size=20` возвращает `{scoringEnabled,page,size,totalElements,totalPages,message,content}`. Элемент `content`: `{dish,score,explanation}`; `dish` — карточка каталога, `explanation` — массив строк. При пустом профиле `score=null`. Балл 0–100, веса 50/30/20 нормируются по активным критериям, при равенстве порядок задаётся ID.

`GET /api/recommendations/dishes/{dishId}/compatibility` возвращает `{dishId,compatible,reason,excludedIngredientId}`. Для скрытого из каталога блюда — 404. Причина нужна для показа несовместимости при открытии карточки по прямой ссылке.

## Корзина

Все маршруты требуют CLIENT:

| Метод | URL | Тело / результат |
| --- | --- | --- |
| GET | `/api/cart` | Корзина, в том числе пустая |
| POST | `/api/cart/items` | `{dishId,quantity,confirmReplaceKitchen:false}`; 201 |
| PATCH | `/api/cart/items/{dishId}` | `{quantity:2}`; 200 |
| DELETE | `/api/cart/items/{dishId}` | Обновлённая корзина, 200 |
| DELETE | `/api/cart` | Очистка, 204 |

POST **прибавляет** количество; PATCH **задаёт** его. Количество — целое 1–99. `confirmReplaceKitchen` можно опустить; по умолчанию false. При `409 CART_KITCHEN_MISMATCH` покажите подтверждение очистки и только затем повторите с true. При ошибке прежняя корзина сохраняется. POST корзины не является идемпотентным: после сетевой ошибки сначала перечитайте корзину, не повторяйте добавление автоматически.

Ответ: `{id,kitchenId,items,totalPrice,hasPriceChanges,updatedAt}`. Элемент: `{dishId,dishName,quantity,priceAtAdd,currentPrice,lineTotal,priceChanged,availablePortions,available}`. `totalPrice` использует актуальные цены. Старая цена нужна для проверки при оформлении; изменение количества её не подтверждает.

## Оформление, повтор и цены

`POST /api/orders` требует CLIENT, CSRF и `Idempotency-Key`: 8–128 символов `[A-Za-z0-9._:-]`. Подходит UUID. Тело:

```json
{
  "deliveryAddress": "Санкт-Петербург, улица Тестовая, дом 10",
  "phone": "+7 (999) 123-45-67",
  "comment": null,
  "items": [{"dishId": "UUID блюда", "quantity": 1}],
  "acceptCurrentPrices": false,
  "confirmedPriceFingerprint": null
}
```

Адрес 10–500 символов после удаления краевых пробелов; телефон содержит 10–15 цифр, допустимы `+() -`, общая длина ≤25; комментарий ≤500. Позиции должны совпасть с корзиной, дубликаты запрещены. `acceptCurrentPrices` можно опустить. Клиентскую сумму сервер не использует.

При `409 PRICE_CHANGED` ответ содержит `code,message,updatedTotal,updatedPriceFingerprint,items`. Элементы `items`: `{dishId,dishName,quantity,previousUnitPrice,currentUnitPrice,currentLineTotal}` — только изменившиеся позиции. После явного согласия отправьте `acceptCurrentPrices:true` и `confirmedPriceFingerprint` из **последнего** расчёта. Если цены поменялись снова, сервер вернёт новый 409. Не подтверждайте его автоматически.

Первое успешное оформление возвращает **201**, успешный повтор — **200 с тем же заказом**. После сетевой ошибки повторите тот же ключ и то же тело. Не генерируйте новый ключ, пока исход предыдущей попытки неизвестен. После успешно созданного заказа изменение реквизитов, позиций или полей подтверждения с тем же ключом даёт `409 IDEMPOTENCY_KEY_REUSED`. После бизнес-отказа, при котором заказ не создан, ключ можно использовать для исправленного запроса.

Заказ: `{id,orderNumber,customerId,kitchenId,deliveryAddress,phone,paymentMethod,comment,status,version,totalPrice,cancellationReason,createdAt,updatedAt,items,history}`. `paymentMethod=CASH_ON_DELIVERY`; `orderNumber` сейчас совпадает с UUID. Позиция: `{id,dishId,dishName,unitPrice,quantity,lineTotal,weightGrams,kcalPerPortion}`. История: `{id,fromStatus,toStatus,actorId,reason,createdAt}`. Снимки позиций не меняются при редактировании меню.

| Метод | URL | Доступ / тело |
| --- | --- | --- |
| GET | `/api/orders` | CLIENT; массив своих заказов, новые первыми |
| GET | `/api/orders/{id}` | CLIENT; свой заказ, иначе 404 |
| POST | `/api/orders/{id}/cancel` | CLIENT/KITCHEN; `{expectedVersion:0,reason:null}`, только CREATED; свой заказ / заказ своей кухни |
| GET | `/api/kitchens/me/orders` | KITCHEN; массив заказов своей кухни |
| PATCH | `/api/kitchens/me/orders/{id}/status` | KITCHEN; `{status:"PREPARING",expectedVersion:0,reason:null}` |

`version` — целое число, у нового заказа 0. Для отмены и любой смены статуса передавайте `expectedVersion` из последнего полученного заказа. Успех — 200 с обновлённым заказом и увеличенной на 1 версией. Несовпадение даёт `409 ORDER_VERSION_CONFLICT` с `currentVersion` на верхнем уровне JSON; перечитайте заказ и предложите действие заново. Не повторяйте изменение автоматически с новой версией. Пропущенная, отрицательная или дробная версия — 400.

Пример отмены клиентом: `POST /api/orders/{id}/cancel` с `{"expectedVersion":0}`. Кухня использует тот же маршрут с `{"expectedVersion":0,"reason":"Нет продуктов"}` либо PATCH своего заказа в CANCELLED. Сервер проверяет роль, владельца и версию; чужой заказ — 404.

Допустимые переходы: CREATED → PREPARING → DELIVERING → COMPLETED; из CREATED также CANCELLED. Кухне для CANCELLED нужна непустая причина ≤500 символов. Повтор отмены даёт 409 и не возвращает порции снова. Обновляйте открытый список заказов раз в 15 секунд; WebSocket отсутствует. Списки заказов пока без пагинации.

## Отзывы

`POST /api/orders/{orderId}/reviews` (CLIENT, 201):

```json
{
  "items": [{"orderItemId":"UUID позиции заказа","rating":5,"reordered":true,"text":"Вкусно"}]
}
```

От 1 до 30 позиций, оценка — целое 1–5, `reordered` обязателен, текст необязателен и ≤1000 символов. Только свой COMPLETED-заказ; один отзыв на позицию. Скрытие не разрешает оставлять повторный отзыв.

- `GET /api/dishes/{dishId}/reviews` — публичные видимые отзывы.
- `GET /api/kitchens/me/reviews` — видимые отзывы на блюда своей кухни.
- `GET /api/admin/reviews?visible=true|false` — ADMIN, без фильтра все.
- `POST /api/admin/reviews/{reviewId}/hide` — ADMIN; `{reason:"Причина"}`, непустая строка ≤500.

Ответы создания и списков — массивы. Элемент: `{id,orderId,orderItemId,dishId,rating,reordered,text,visible,status,moderationReason,moderatedBy,moderatedAt,createdAt}`. `status` — VISIBLE или HIDDEN; `visible` равно `status === "VISIBLE"`. Скрытие возвращает один объект. Агрегаты находятся в карточках каталога и пересчитываются по видимым отзывам.

## Ошибки и диагностика

400 — неверные поля, JSON или параметры; 401 — нужен вход; 403 — роль или CSRF; 404 — объект отсутствует либо чужой; 409 — бизнес-конфликт. Ошибки прикладных контроллеров содержат `code,message`, иногда `fields` или дополнительные поля расчёта. Spring Security возвращает тот же формат: 401 — `AUTHENTICATION_REQUIRED`, 403 — `ACCESS_DENIED`. При 401 откройте вход; при 403 проверьте актуальность CSRF и доступную роль. Сетевые ошибки и ответы прокси могут не содержать JSON. `X-Request-ID` позволяет сопоставить ответ с журналом, включая отказы Security.

Основные коды для корзины/заказов: `CART_EMPTY`, `CART_CHANGED`, `CART_KITCHEN_MISMATCH`, `CART_ITEMS_LIMIT`, `CART_QUANTITY_LIMIT`, `KITCHEN_CLOSED`, `DISH_ARCHIVED`, `DISH_UNAVAILABLE`, `INSUFFICIENT_STOCK`, `EXCLUDED_INGREDIENT`, `PRICE_CHANGED`, `IDEMPOTENCY_KEY_REUSED`, `ORDER_NOT_CANCELLABLE`, `ORDER_VERSION_CONFLICT`, `INVALID_ORDER_TRANSITION`. Для отзывов: `ORDER_NOT_COMPLETED`, `REVIEW_ALREADY_EXISTS`, `REVIEW_ITEM_NOT_IN_ORDER`, `REVIEW_ALREADY_HIDDEN`. Некорректный JSON, заголовки и типы параметров: `INVALID_REQUEST`; ошибки DTO: `VALIDATION_ERROR` с `fields`.
