# FoodFlow

Учебный проект команды Университета ИТМО: заказ домашней еды от локальных кухонь. Каталог → корзина → заказ → обработка кухней → выполнение → отзыв. Есть кабинеты клиента, кухни и администратора, предпочтения и объяснимые рекомендации.

[HTTP API](docs/API.md) · [ТЗ](docs/FoodFlow_TZ_Stage2.docx) · [Диаграммы](docs/diagrams/) · [Макеты](docs/mockups/)

## Быстрый запуск

Нужны Docker и Docker Compose ≥2.24.4. Java и Node.js на хосте не требуются.

```bash
cp .env.example .env
```

Задайте в `.env` собственный `POSTGRES_PASSWORD`. Чтобы создать администратора, добавьте:

```dotenv
SPRING_PROFILES_ACTIVE=local
ADMIN_EMAIL=admin@example.test
ADMIN_PASSWORD=replace_with_12_to_128_characters
```

```bash
docker compose -f compose.yaml -f compose.dev.yaml up --build -d
curl --fail http://localhost:8080/api/health
```

Откройте **http://localhost:3000**. Backend — `localhost:8080`, PostgreSQL — `localhost:5433`; порты доступны только локально. Первая сборка требует интернета. Ожидаемый ответ health: `{"status":"UP","application":"FoodFlow"}`.

На чистой БД каталог пуст; категории и ингредиенты создаются миграциями. Администратор создаётся только с профилем `local`; существующий пароль не сбрасывается, обычный аккаунт не повышается до ADMIN. Публичная регистрация создаёт CLIENT.

```bash
docker compose -f compose.yaml -f compose.dev.yaml logs --tail=100 backend
docker compose -f compose.yaml -f compose.dev.yaml stop
```

Данные сохраняются в томе `postgres_data`. `down -v` удаляет БД. Изменение `.env` не меняет пароль уже созданной БД. Не коммитьте `.env`, `.local/`, пароли, настройки IDE и результаты сборки.

## Разработка

Backend: Java 21, Spring Boot 4.1.2, Maven Wrapper, PostgreSQL 17, Flyway. Frontend: React 18, TypeScript, Vite. Nginx отдаёт SPA и проксирует `/api`. Авторизация — HTTP-сессия, `JSESSIONID`, CSRF; пароли — PBKDF2.

Модульный монолит `com.foodflow`: `identity`, `catalog`, `recommendation`, `ordering`, `feedback`, `common`. Контроллеры работают с DTO, сервисы содержат бизнес-правила и транзакции. Модули взаимодействуют через сервисы. Схема меняется новыми миграциями Flyway; `ddl-auto=validate`, применённые миграции не редактируются.

### Backend

```bash
docker compose -f compose.yaml -f compose.dev.yaml up -d postgres
cd backend
# Задайте DB_PASSWORD в окружении терминала или IDE.
./mvnw spring-boot:run
```

Java не читает корневой `.env`. `DB_PASSWORD` должен совпадать с `POSTGRES_PASSWORD`. По умолчанию `DB_URL=jdbc:postgresql://localhost:5433/foodflow`, `DB_USERNAME=foodflow`. В Compose используется `postgres:5432`, пароль передаётся автоматически.

В IDE импортируйте `backend/pom.xml`, выберите JDK 21 и запустите `BackendApplication`. Для администратора передайте `SPRING_PROFILES_ACTIVE=local`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` (12–128 символов). Если порт 8080 занят контейнером, остановите сервис `backend`.

### Frontend

Нужны Node.js 20+ и запущенный backend:

```bash
cd frontend
npm ci
npm run dev
```

Откройте http://localhost:5173. Прокси `/api` использует `http://localhost:8080`; другой адрес задаётся через `FOODFLOW_BACKEND_URL`. Используйте относительные URL, чтобы сохранить работу cookie и CSRF.

## Демонстрационные данные

Нужны запущенное приложение, созданный администратор и Python 3.9+. Задайте **в окружении терминала** `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `DEMO_PASSWORD` (8–128 символов):

```bash
python3 scripts/demo.py
python3 scripts/demo.py --smoke
```

Скрипт создаёт `client@demo.test`, `kitchen1@demo.test` … `kitchen3@demo.test` и 20 блюд. Пароль демоаккаунтов — `DEMO_PASSWORD`. Повторный запуск не сбрасывает пароли, цены и остатки.

`--smoke` очищает корзину и предпочтения демоклиента, оформляет заказ, проверяет повтор запроса, проводит заказ до выполнения и создаёт отзыв. Каждый запуск расходует порцию и создаёт новый заказ; используйте только учебную БД. Другой адрес API — `FOODFLOW_BASE_URL`; доверенный CA для HTTPS — `FOODFLOW_CA_FILE`.

## Тесты

Frontend:

```bash
cd frontend
npm ci
npm test
npm run build
```

Backend (JDK 21 и работающий Docker):

```bash
cd backend
./mvnw -B verify
```

Testcontainers запускает отдельную PostgreSQL 17: локальная БД и `.env` не нужны. Проверяются миграции, права и CSRF, конкурентные заказы и отмены, идемпотентность, откат, цены, рекомендации и отзывы. Без Docker интеграционные тесты завершаются ошибкой. Отчёты — `backend/target/surefire-reports/`.

Без Docker можно проверить формулу и переходы статусов: `./mvnw -B -Dtest=RecommendationScorerTest,OrderTransitionsTest test` из `backend/`.

[GitHub Actions](https://github.com/RomoBomba/foodflow-delivery-service/actions) выполняет проверки backend и frontend. Нагрузочные цели ТЗ (p95 чтения ≤500 мс, оформления ≤800 мс, 5xx <1%) требуют отдельного испытания.

## HTTPS-стенд

Задайте `.env`, положите сертификат `fullchain.pem` и ключ `privkey.pem` в `.local/tls` (либо задайте `TLS_CERT_DIR`). Сертификат должен быть доверенным браузером; автоматически он не выпускается.

```bash
docker compose up --build -d
docker compose ps
curl --fail https://your-domain.example/api/health
```

Наружу опубликован только Nginx на 443; backend и БД доступны внутри сети Compose. Cookie имеет `Secure`. Порт и привязка задаются `HTTPS_PORT` и `HTTPS_BIND`. Не подключайте `compose.dev.yaml` на публичном стенде. После замены сертификата: `docker compose exec nginx nginx -s reload`.

## Обновление и резервные копии

Перед обновлением остановите backend и сделайте копию БД. Flyway применяет миграции при запуске. V12 переводит `IN_DELIVERY` в `DELIVERING`, добавляет версии заказов, `Reservation` и статусы отзывов с сохранением истории и остатков. Старый backend несовместим с V12: для отката нужна предварительная копия БД.

Для локального режима сначала задайте `export COMPOSE_FILE=compose.yaml:compose.dev.yaml`. При работающем `postgres`:

```bash
bash scripts/db-backup.sh
```

Копии сохраняются в `.local/backups/`; каталог можно изменить через `BACKUP_DIR`. Восстановление **заменяет текущую БД**:

```bash
docker compose stop backend
bash scripts/db-restore.sh .local/backups/foodflow_YYYYMMDD_HHMMSS.dump
docker compose start backend
```

Восстановление выполняется одной транзакцией; тома не удаляются. При запуске backend применит отсутствующие миграции.
