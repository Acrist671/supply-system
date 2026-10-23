# API сервера системы снабжения

В примерах используется адрес `https://{your_uri}/`. Замените `{your_uri}` адресом сервера, а `{token}` — токеном из ответа на запрос входа. Локальный сервер проекта по умолчанию работает по адресу `http://127.0.0.1:8080/` без HTTPS. Для адреса с HTTPS нужен прокси с настроенным TLS.

Все запросы, кроме входа, требуют заголовок `Authorization: Bearer {token}`. Запросы с телом принимают JSON.

## Роли

| Роль | Возможности |
| --- | --- |
| `admin` | Просмотр, рабочие операции, удаление записей и добавление пользователей |
| `operator` | Просмотр, создание и изменение данных, поступления, списания и работа с заказами |
| `viewer` | Только просмотр |

Сеанс истекает через 8 часов или после 30 минут бездействия. Сервер проверяет роль при каждом запросе.

## Авторизация и пользователи

Войти и получить токен:

```http
POST https://{your_uri}/api/auth/login HTTP/1.1
Content-Type: application/json

{"username":"admin","password":"ваш-пароль"}
```

Получить сведения о текущем пользователе:

```http
GET https://{your_uri}/api/auth/me HTTP/1.1
Authorization: Bearer {token}
```

Получить список пользователей, только для администратора:

```http
GET https://{your_uri}/api/users HTTP/1.1
Authorization: Bearer {token}
```

Добавить пользователя, только для администратора:

```http
POST https://{your_uri}/api/users HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"username":"operator1","password":"strong-password-123","role":"operator"}
```

## Товары

Получить список товаров:

```http
GET https://{your_uri}/api/products HTTP/1.1
Authorization: Bearer {token}
```

Добавить товар:

```http
POST https://{your_uri}/api/products HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"name":"Молоко","unit":"л","category":"Продукты","price":89.50}
```

Начальный остаток равен нулю, дата добавления устанавливается базой данных. Цена необязательна; по умолчанию она равна `0`. Допустимые единицы измерения: `шт`, `кг`, `л`, `м`, `уп`. Цена должна быть от `0` до `99999.99` с точностью до двух знаков после запятой.

Изменить товар с ID `1`:

```http
PUT https://{your_uri}/api/products/1 HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"name":"Молоко 2","unit":"л","category":"Продукты","price":89.50}
```

Если при изменении не передать `price`, прежняя цена сохранится. Название и категорию можно менять при наличии остатков и заказов. Единицу измерения можно изменить только при нулевом складском остатке и отсутствии товара в магазинах и заказах. Иначе сервер вернёт `409 Conflict`.

Проверить возможность изменения единицы измерения:

```http
GET https://{your_uri}/api/products/1/unit-change HTTP/1.1
Authorization: Bearer {token}
```

Удалить товар с ID `1`, только при нулевом остатке и отсутствии связей с магазинами и заказами:

```http
DELETE https://{your_uri}/api/products/1 HTTP/1.1
Authorization: Bearer {token}
```

Учесть поступление пяти единиц товара:

```http
POST https://{your_uri}/api/products/1/receipts HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"quantity":5}
```

Количество должно быть положительным целым числом. Количество и остаток хранятся как `BIGINT`; при переполнении сервер вернёт ошибку без изменения остатка.

## Магазины

Получить список магазинов:

```http
GET https://{your_uri}/api/shops HTTP/1.1
Authorization: Bearer {token}
```

Добавить магазин:

```http
POST https://{your_uri}/api/shops HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"name":"Центральный","address":"Ленина, 1","phone":"+7 (999) 123-45-67"}
```

Изменить магазин с ID `1`:

```http
PUT https://{your_uri}/api/shops/1 HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"name":"Центральный","address":"Невский, 10","phone":"+7 (999) 123-45-67"}
```

Удалить магазин с ID `1` можно только при отсутствии связанных товаров и заказов:

```http
DELETE https://{your_uri}/api/shops/1 HTTP/1.1
Authorization: Bearer {token}
```

Получить остатки магазина с ID `1`:

```http
GET https://{your_uri}/api/shops/1/products HTTP/1.1
Authorization: Bearer {token}
```

Получить сводку по всем магазинам, включая магазины без товаров:

```http
GET https://{your_uri}/api/shops/summary HTTP/1.1
Authorization: Bearer {token}
```

Сводка содержит число видов товаров, сумму количеств и общую стоимость по текущим ценам. Количества товаров с разными единицами измерения суммируются; отдельные остатки доступны в списке товаров магазина.

Списать три единицы товара `1` в магазине `1`:

```http
POST https://{your_uri}/api/shops/1/products/1/writeoffs HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"quantity":3}
```

Количество должно быть положительным и не превышать остаток магазина. Списание не меняет складской остаток и записывается в журнал. Увеличить остаток магазина можно только выполнением заказа.

## Заказы

Получить список заказов:

```http
GET https://{your_uri}/api/orders HTTP/1.1
Authorization: Bearer {token}
```

Создать заказ для магазина `1` на товар `1`:

```http
POST https://{your_uri}/api/orders HTTP/1.1
Authorization: Bearer {token}
Content-Type: application/json

{"shopId":1,"productId":1,"quantity":5}
```

Новый заказ получает статус `new`. Заказы можно фильтровать по магазину, товару, статусу и году; ненужные параметры следует опустить:

```http
GET https://{your_uri}/api/orders?shopId=1&productId=1&status=in%20progress&year=2026 HTTP/1.1
Authorization: Bearer {token}
```

Взять новый заказ в работу:

```http
POST https://{your_uri}/api/orders/1/start HTTP/1.1
Authorization: Bearer {token}
```

Выполнить заказ в работе:

```http
POST https://{your_uri}/api/orders/1/fulfill HTTP/1.1
Authorization: Bearer {token}
```

Статусы меняются последовательно: `new` → `in progress` → `done`. Выполнение заказа списывает товар со склада и зачисляет его магазину в одной транзакции. При недостаточном складском остатке изменения отменяются.

Удалить можно только новый заказ:

```http
DELETE https://{your_uri}/api/orders/1 HTTP/1.1
Authorization: Bearer {token}
```

Для заказа в работе или выполненного сервер вернёт `409 Conflict`; для отсутствующего — `404 Not Found`.

## Журнал движения товаров

```http
GET https://{your_uri}/api/movements HTTP/1.1
Authorization: Bearer {token}
```

Журнал содержит поступления и отгрузки после применения миграции V3, а также списания товаров из магазинов после миграции V5. Исторические операции до соответствующих миграций восстановить по текущим остаткам нельзя. Миграция V4 сохраняет названия товара и магазина в записи движения на момент операции.

## Завершение сеанса

```http
POST https://{your_uri}/api/auth/logout HTTP/1.1
Authorization: Bearer {token}
```

После выхода прежний токен больше не действует.
