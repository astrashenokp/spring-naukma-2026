# Food Rescue — контракт команди

Домовленість на день розробки групового завдання до лекції 2 (REST, JSON, валідація).
Усе, що тут записано, змінюємо **лише попередивши двох інших у чаті**.

| Роль | Бізнес-кейс | Пише код у пакеті | Пояснює на захисті |
|------|-------------|-------------------|--------------------|
| Людина 1 | БК-1: публікація лоту | `lot` | `common.lot` |
| Людина 2 | БК-2: волонтери та резервування | `volunteer` | `common.error` |
| Людина 3 | БК-3: передача, доставка, підтвердження | `delivery` | `common.history` |

---

## 0. Як працюємо, щоб ніхто нікого не чекав

Три правила, без яких паралельна робота неможлива:

1. **Каркас готовий до початку дня.** Весь пакет `common`, `application.properties`, демо-дані й шаблон тесту вже лежать у `main`, і проєкт компілюється з першої хвилини. Спільний код у процесі ніхто не пише — усі одразу беруться за свій кейс.
2. **Демо-дані з фіксованими id.** При старті застосунок сам створює лоти в потрібних станах (§7). Людині 2 не треба чекати, поки Людина 1 опублікує лот, а Людині 3 — поки Людина 2 його зарезервує: потрібні лоти вже існують.
3. **Заготовка для зв'язку Людини 2 і 3.** Клас `VolunteerStatsRecorder` є в каркасі з порожніми методами (§9). Людина 3 викликає їх одразу, Людина 2 наповнює, коли дійде черга.

Кожен пише **лише у своєму пакеті**. У `common` нічого не змінювати й не видаляти. Якщо чогось бракує — **додати** новий метод, не чіпаючи наявних, і написати в чат. Так чужий код не зламається.

---

## 1. Проєкт

**Spring Initializr:** Maven · Spring Boot 4.1.1 · Java 25 · Group `com.example` · Artifact `foodrescue` · Dependencies: **Spring Web**, **Validation**

```
com.example.foodrescue
├── FoodRescueApplication.java
├── common                ← КАРКАС, готовий до початку дня
│   ├── lot               FoodLot, FoodItem, енуми, LotStore, LotStatusChanger, LotResponse
│   ├── error             винятки, GlobalExceptionHandler
│   ├── history           LotStatusHistory, StatusHistoryRecorder
│   └── demo              DemoData — лоти з фіксованими id
├── lot                   ← Людина 1
├── volunteer             ← Людина 2 (VolunteerStatsRecorder уже є в каркасі)
└── delivery              ← Людина 3
```

- Пишеш **лише у своєму пакеті**, правила для `common` — у §0.
- Сховище даних — `Map` у пам'яті. Бази даних, Security, Swagger, Thymeleaf зараз **немає**.
- БК-4 (планувальник) і БК-5 (Spring Batch) зараз **не робимо**.

---

## 2. Рішення, яких бракувало в ТЗ

| Питання | Рішення |
|---------|---------|
| Премодерація | Стан `PENDING_MODERATION`. Адмін схвалив → `PUBLISHED`, відхилив → `DRAFT` |
| Поріг скасувань донора | Понад 20% скасованих, **лише якщо в донора щонайменше 5 лотів** |
| Мінімальне вікно самовивозу | 45 хвилин |
| Скільки діє резерв | 30 хвилин від резервування |
| Великий лот | Від 20 кг. Перші 10 хв після публікації — лише для волонтерів із показником понад 90 |
| Обмежений волонтер | Понад 3 зриви **або** понад 15% запізнень → може брати лише `BAKERY` і `GROCERY` |
| Показник відповідальності | `% успішних − 2 × запізнення − 10 × зриви`, у межах 0-100. `% успішних = completed / (completed + noShows) × 100`; без жодної доставки — 100 |
| Розбіжність у вазі | Понад 10% між вагою при передачі й при отриманні → `DISPUTED` |
| Код підтвердження | 6 цифр, генерується при переході в `DELIVERED` |

---

## 3. application.properties — у каркасі

```properties
spring.application.name=foodrescue
spring.mvc.problem-details.enabled=true
spring.jackson.deserialization.fail-on-unknown-properties=true
spring.jackson.default-property-inclusion=non_null
spring.jackson.datatype.datetime.write-dates-as-timestamps=false
```

Останній рядок — **не** той, що на слайді 22. У Spring Boot 4 ключа `spring.jackson.serialization.write-dates-as-timestamps` більше немає, і з ним застосунок не стартує. Перевірено.

---

## 4. Спільні правила коду

**URL**
- Префікс `/api/v1`, іменники в множині, через дефіс: `/destination-points`.
- Переходи станів — **іменники-підресурси**: `POST /lots/{id}/publication`, а не `/publish`. Дієслова в URL заборонені слайдом 8.

**Дані**
- `id` — `UUID`, генерує сервер.
- Час — `Instant`, у JSON ISO-8601 в UTC: `"2026-09-14T18:00:00Z"`. Дати без часу — `LocalDate`.
- Вага — `BigDecimal` у кілограмах. Порівнювати через `compareTo`, **не** `equals`.
- Поля JSON — camelCase.

**Класи**
- **DTO — лише records.** Без Lombok.
- Внутрішні моделі, які змінюють стан (`FoodLot`, `VolunteerProfile`, `Delivery`), — звичайні класи.
- Залежності — лише через конструктор, без `@Autowired` на полях.
- **Контролер без логіки:** прийняти DTO → викликати сервіс → повернути відповідь. Усі правила — у сервісі. Від цього залежать тести.

**Відповіді**

| Дія | Статус | Тіло |
|-----|--------|------|
| Створення | `201` + заголовок `Location` | порожнє (слайд 18) |
| Читання, оновлення, перехід стану | `200` | актуальний стан ресурсу |
| Видалення | `204` | порожнє |
| Помилка | `400` / `403` / `404` / `409` / `422` | ProblemDetail |

Тексти повідомлень — коротко, українською, без крапки в кінці: `Лот 3fd2... не знайдено`.

---

## 5. Статуси лоту — у каркасі

```java
public enum FoodCategory { PREPARED_MEAL, BAKERY, VEGETABLES, GROCERY }

public enum StorageCondition { ROOM, CHILLED, FROZEN }

public enum ItemUnit { KG, PIECE, LITER }

public enum LotStatus {
    DRAFT, PENDING_MODERATION, PUBLISHED, RESERVED, PICKED_UP,
    DELIVERED, CONFIRMED, DISPUTED, EXPIRED, CANCELLED, FAILED;

    public boolean canTransitionTo(LotStatus next) { ... }
}
```

Дозволені переходи:

```
DRAFT               → PENDING_MODERATION, PUBLISHED, CANCELLED
PENDING_MODERATION  → PUBLISHED, DRAFT, CANCELLED
PUBLISHED           → RESERVED, CANCELLED, EXPIRED
RESERVED            → PUBLISHED, PICKED_UP
PICKED_UP           → DELIVERED, FAILED
DELIVERED           → CONFIRMED, DISPUTED
CONFIRMED, DISPUTED, EXPIRED, CANCELLED, FAILED → кінцеві, переходів немає
```

`EXPIRED` і `FAILED` виставлятиме планувальник із БК-4 пізніше — зараз вони лише є в переліку.

---

## 6. Модель лоту — у каркасі

`FoodLot` — звичайний клас із геттерами й сеттерами.

| Поле | Тип | Хто записує |
|------|-----|-------------|
| `id` | `UUID` | Людина 1 |
| `donorOrgId` | `UUID` | Людина 1 |
| `title` | `String` | Людина 1 |
| `category` | `FoodCategory` | Людина 1 |
| `items` | `List<FoodItem>` | Людина 1 |
| `totalWeightKg` | `BigDecimal` | Людина 1 |
| `storageCondition` | `StorageCondition` | Людина 1 |
| `pickupAddress` | `String` | Людина 1 |
| `pickupFrom`, `pickupTo` | `Instant` | Людина 1 |
| `createdAt`, `publishedAt` | `Instant` | Людина 1 |
| `reservedByVolunteerId` | `UUID` | Людина 2 |
| `reservedUntil` | `Instant` | Людина 2 |
| `status` | `LotStatus` | **усі — лише через `LotStatusChanger`** |

```java
public record FoodItem(String name, BigDecimal quantity, ItemUnit unit, LocalDate bestBefore) {}
```

Дані доставки (вага при передачі, код, час доставки й підтвердження) — **не у `FoodLot`**, а в окремій моделі `Delivery` Людини 3.

---

## 7. Спільні компоненти — у каркасі

Усе це вже є в каркасі. Сигнатури не змінювати — лише додавати нове.

### `LotStore`

```java
@Component
public class LotStore {
    public FoodLot save(FoodLot lot);
    public Optional<FoodLot> findById(UUID id);
    public FoodLot getById(UUID id);          // кидає NotFoundException
    public List<FoodLot> findAll();
}
```

### `LotStatusChanger`

```java
@Component
public class LotStatusChanger {
    // перевіряє canTransitionTo → інакше ConflictException,
    // ставить новий статус, пише історію через StatusHistoryRecorder
    public void transition(FoodLot lot, LotStatus next, String comment);
}
```

### `LotResponse` та `FoodItemResponse`

Спільні, бо лот повертають кейси всіх трьох.

```java
public record LotResponse(
        UUID id, UUID donorOrgId, String title, FoodCategory category,
        List<FoodItemResponse> items, BigDecimal totalWeightKg,
        StorageCondition storageCondition, String pickupAddress,
        Instant pickupFrom, Instant pickupTo, LotStatus status,
        Instant createdAt, Instant publishedAt,
        UUID reservedByVolunteerId, Instant reservedUntil) {

    public static LotResponse from(FoodLot lot) { ... }
}

public record FoodItemResponse(String name, BigDecimal quantity, ItemUnit unit, LocalDate bestBefore) {}
```

### `StatusHistoryRecorder`

```java
public record LotStatusHistory(UUID lotId, LotStatus fromStatus, LotStatus toStatus,
                               Instant changedAt, String comment) {}

@Component
public class StatusHistoryRecorder {
    public void record(UUID lotId, LotStatus from, LotStatus to, String comment);
    public List<LotStatusHistory> findByLot(UUID lotId);
}
```

Хто змінив статус (`changedBy`), додамо після лекції про Security — зараз користувачів немає.

### `DemoData` — лоти з фіксованими id

При старті застосунку `CommandLineRunner` кладе в `LotStore` чотири лоти. Id завжди однакові, тож їх можна одразу вписувати в запити.

| Константа | id | Що це | Для кого |
|-----------|----|-------|----------|
| `DONOR` | `00000000-0000-0000-0000-00000000d001` | донор усіх демо-лотів | усі |
| `LOT_DRAFT` | `00000000-0000-0000-0000-0000000000a1` | `DRAFT`, BAKERY, 5 кг | Людина 1 |
| `LOT_PUBLISHED` | `00000000-0000-0000-0000-0000000000a2` | `PUBLISHED`, BAKERY, 5 кг | Людина 2 |
| `LOT_PUBLISHED_BIG` | `00000000-0000-0000-0000-0000000000a3` | щойно `PUBLISHED`, PREPARED_MEAL, 25 кг | Людина 2: ранній доступ, обмеження категорії |
| `LOT_RESERVED` | `00000000-0000-0000-0000-0000000000a4` | `RESERVED` на `DEMO_VOLUNTEER`, резерв ще 30 хв | Людина 3 |
| `DEMO_VOLUNTEER` | `00000000-0000-0000-0000-0000000000b1` | волонтер демо-резерву | Людина 2 створює його у своєму сховищі |

Демо-лоти створюються одразу в потрібному стані, тому історії переходів у них немає — `GET /history` для них поверне порожній список.

Дані в пам'яті, тож після перезапуску все повертається до цього стану. Зіпсував демо-лот — просто перезапусти застосунок.

### Як кожен змінює статус лоту

Однаково в усіх трьох сервісах:

```java
FoodLot lot = lotStore.getById(lotId);                      // 404, якщо немає

// спершу — перевірки свого кейсу, власні винятки

statusChanger.transition(lot, LotStatus.RESERVED, "Зарезервовано");   // 409 + запис історії

// лише після успішного переходу — свої поля
lot.setReservedByVolunteerId(volunteerId);
lot.setReservedUntil(Instant.now().plus(30, ChronoUnit.MINUTES));
lotStore.save(lot);
```

Порядок важливий: якщо спершу змінити поля, а перехід потім кине виняток, лот залишиться напівзміненим.

---

## 8. Помилки — у каркасі

```java
public abstract class BusinessException extends RuntimeException {
    protected BusinessException(HttpStatus status, String message) { ... }
    public HttpStatus getStatus() { ... }
}
```

| Клас | Статус | `type` | `title` |
|------|--------|--------|---------|
| — (валідація) | 400 | `.../errors/validation` | Помилка валідації |
| `NotFoundException` | 404 | `.../errors/not-found` | Не знайдено |
| `ConflictException` | 409 | `.../errors/conflict` | Конфлікт стану |
| `ForbiddenActionException` | 403 | `.../errors/forbidden` | Дію заборонено |
| `BusinessRuleException` | 422 | `.../errors/business-rule` | Порушено бізнес-правило |

`type` повністю: `https://api.foodrescue.local/errors/...`

**Власні винятки** кожен створює у своєму пакеті, успадковуючи один із чотирьох:

```java
public class LotAlreadyReservedException extends ConflictException { ... }
```

**`GlobalExceptionHandler`** має два обробники: для `MethodArgumentNotValidException` (з мапою `errors`) і один для `BusinessException` (статус береться з `getStatus()`). Нові винятки ловляться автоматично, обробник правити не треба.

Над класом **обов'язково** `@Order(Ordered.HIGHEST_PRECEDENCE)`. Без нього вбудований обробник Spring перехоплює помилку валідації раніше, і мапа `errors` не потрапляє у відповідь. Перевірено.

Невідоме поле в JSON Spring обробляє сам і повертає `400` у форматі ProblemDetail — окремий обробник не потрібен.

**Формат відповіді:**

```json
{
  "type": "https://api.foodrescue.local/errors/validation",
  "title": "Помилка валідації",
  "status": 400,
  "detail": "Дані неправильні",
  "instance": "/api/v1/lots",
  "errors": {
    "title": "Назва від 3 до 100 символів",
    "totalWeightKg": "Вага мінімум 0.1 кг"
  }
}
```

```json
{
  "type": "https://api.foodrescue.local/errors/conflict",
  "title": "Конфлікт стану",
  "status": 409,
  "detail": "Лот 3fd2...: перехід PUBLISHED → PICKED_UP заборонено",
  "instance": "/api/v1/lots/3fd2.../pickup"
}
```

---

## 9. Зв'язок між Людиною 2 і Людиною 3

Клас уже є в каркасі, у пакеті `volunteer`, з **порожніми** методами:

```java
@Component
public class VolunteerStatsRecorder {
    public void recordPickup(UUID volunteerId, boolean late) { }     // забрав, чи із запізненням
    public void recordCompletedDelivery(UUID volunteerId) { }        // успішне підтвердження
    public void recordStrike(UUID volunteerId) { }                   // розбіжність у вазі
}
```

- **Людина 3** викликає ці методи з `DeliveryService` з самого початку. Поки вони порожні, нічого не відбувається, але код уже правильний і нічого переписувати не доведеться.
- **Людина 2** наповнює тіла методів, коли дійде до метрик волонтера. Сигнатури не змінює.
- Невідомий `volunteerId` → `NotFoundException`. Щоб демо-сценарій Людини 3 не зламався, Людина 2 при старті створює волонтера з id `DemoData.DEMO_VOLUNTEER`.

---

## 10. Ендпоінти

«Якщо встигнете» — не блокує здачу; усе інше обов'язкове.

### Людина 1 — БК-1

Працює на: власних лотах, які сама створює, і `LOT_DRAFT`.

| Метод | URL | Тіло | Успіх | Помилки |
|-------|-----|------|-------|---------|
| POST | `/api/v1/lots` | `LotRequest` | 201 | 400 |
| GET | `/api/v1/lots` | — | 200 `List<LotResponse>` | — |
| GET | `/api/v1/lots/{id}` | — | 200 `LotResponse` | 404 |
| PUT | `/api/v1/lots/{id}` | `LotRequest` | 200 `LotResponse` | 400, 404, 409 не `DRAFT` |
| POST | `/api/v1/lots/{id}/publication` | — | 200 `LotResponse` | 404, 409 |
| POST | `/api/v1/lots/{id}/cancellation` | — | 200 `LotResponse` | 404, 409 з `RESERVED` і далі |
| POST | `/api/v1/lots/{id}/approval` *(якщо встигнете)* | `ApprovalRequest` | 200 `LotResponse` | 400, 404, 409 |
| GET | `/api/v1/lots?status=&category=&donorOrgId=` *(якщо встигнете)* | — | 200 | — |

**`LotRequest`** — для POST і PUT, з анотацією `@ValidPickupWindow` на класі:

| Поле | Валідація |
|------|-----------|
| `donorOrgId` | `@NotNull` |
| `title` | `@NotBlank`, `@Size(min = 3, max = 100)` |
| `category` | `@NotNull` |
| `items` | `@Valid`, `@NotEmpty` — `List<FoodItemRequest>` |
| `totalWeightKg` | `@NotNull`, `@DecimalMin("0.1")` |
| `storageCondition` | `@NotNull` |
| `pickupAddress` | `@NotBlank`, `@Size(max = 200)` |
| `pickupFrom`, `pickupTo` | `@NotNull`, `@Future` |

`@ValidPickupWindow` — власний валідатор на рівні класу (слайд 36): `pickupTo` пізніше за `pickupFrom`, різниця щонайменше 45 хв. Якщо за 30 хв не виходить — перенести перевірку в сервіс і кидати `BusinessRuleException` (422).

**`FoodItemRequest`**: `name` — `@NotBlank`; `quantity` — `@NotNull`, `@Positive`; `unit` — `@NotNull`; `bestBefore` — `@FutureOrPresent`

**`ApprovalRequest`**: `approved` — `@NotNull Boolean`; `comment` — `@Size(max = 300)`

**Правила:** створення → `DRAFT`, `createdAt = now`. Публікація → `PUBLISHED` і `publishedAt = now`, або `PENDING_MODERATION` за порогом скасувань. PUT лише в `DRAFT`. Скасування дозволене з `DRAFT`, `PENDING_MODERATION`, `PUBLISHED`.

### Людина 2 — БК-2

Працює на: `LOT_PUBLISHED`, `LOT_PUBLISHED_BIG` і власних волонтерах.

| Метод | URL | Тіло | Успіх | Помилки |
|-------|-----|------|-------|---------|
| POST | `/api/v1/volunteers` | `VolunteerRequest` | 201 | 400 |
| GET | `/api/v1/volunteers/{id}` | — | 200 `VolunteerResponse` | 404 |
| POST | `/api/v1/lots/{id}/reservation` | `ReservationRequest` | 201, `Location` на `.../reservation` | 400, 403, 404, 409 |
| DELETE | `/api/v1/lots/{id}/reservation` | — | 204 | 404, 409 |
| GET | `/api/v1/volunteers/{id}/available-lots` *(якщо встигнете)* | — | 200 `List<LotResponse>` | 404 |

**`VolunteerRequest`**

| Поле | Валідація |
|------|-----------|
| `fullName` | `@NotBlank`, `@Size(min = 2, max = 100)` |
| `email` | `@NotBlank`, `@Email` |
| `phone` | `@NotBlank`, `@Pattern(regexp = "^\\+380\\d{9}$")` |
| `transportType` | `@NotNull` — `enum TransportType { FOOT, BIKE, CAR }` |
| `activityZone` | `@NotBlank`, `@Size(max = 100)` |

**`VolunteerResponse`**: `id, fullName, email, phone, transportType, activityZone, responsibilityScore, restricted, completedDeliveries, latePickups, noShows`

**`ReservationRequest`**: `volunteerId` — `@NotNull`

**Правила:**
- лот не в `PUBLISHED` → 409;
- обмежений волонтер і категорія не `BAKERY` / `GROCERY` → 403;
- *(якщо встигнете)* великий лот у перші 10 хв після `publishedAt` і показник не понад 90 → 403;
- резервування → `reservedByVolunteerId`, `reservedUntil = now + 30 хв`;
- метод резервування — `synchronized`, щоб два волонтери не взяли лот одночасно;
- DELETE: лот не в `RESERVED` → 409; інакше назад у `PUBLISHED`, поля резерву → `null`;
- при старті створити волонтера з id `DemoData.DEMO_VOLUNTEER`;
- наповнити методи `VolunteerStatsRecorder` (§9).

Лічильник зривів поки міняють лише тестові дані — автоматично його заповнюватиме БК-4 пізніше.

### Людина 3 — БК-3

Працює на: `LOT_RESERVED` і власних пунктах призначення.

| Метод | URL | Тіло | Успіх | Помилки |
|-------|-----|------|-------|---------|
| POST | `/api/v1/destination-points` | `DestinationPointRequest` | 201 | 400 |
| GET | `/api/v1/destination-points/{id}` | — | 200 `DestinationPointResponse` | 404 |
| POST | `/api/v1/lots/{id}/pickup` | `PickupRequest` | 200 `DeliveryResponse` | 400, 404, 409 |
| POST | `/api/v1/lots/{id}/delivery` | `DeliveryRequest` | 200 `DeliveryResponse` | 400, 404, 409, 422 |
| POST | `/api/v1/lots/{id}/confirmation` | `ConfirmationRequest` | 200 `DeliveryResponse` | 400, 404, 409, 422 |
| GET | `/api/v1/lots/{id}/history` | — | 200 `List<StatusHistoryResponse>` | 404 |

**`DestinationPointRequest`**

| Поле | Валідація |
|------|-----------|
| `organizationId` | `@NotNull` |
| `name` | `@NotBlank`, `@Size(min = 2, max = 100)` |
| `address` | `@NotBlank`, `@Size(max = 200)` |
| `workingHours` | `@NotBlank`, `@Pattern(regexp = "^\\d{2}:\\d{2}-\\d{2}:\\d{2}$")` — `"09:00-18:00"` |
| `acceptedCategories` | `@NotEmpty` — `Set<FoodCategory>` |

**`PickupRequest`**: `actualWeightKg` — `@NotNull`, `@DecimalMin("0.1")`

**`DeliveryRequest`**: `destinationPointId` — `@NotNull`

**`ConfirmationRequest`**: `confirmationCode` — `@NotBlank`, `@Pattern(regexp = "^\\d{6}$")`; `receivedWeightKg` — `@NotNull`, `@DecimalMin("0.0")`

**`DeliveryResponse`**: `lotId, volunteerId, destinationPointId, lotStatus, pickedUpAt, pickupWeightKg, deliveredAt, confirmationCode, confirmedAt, receivedWeightKg`

**`StatusHistoryResponse`**: `fromStatus, toStatus, changedAt, comment`

**Правила:**
- `pickup`: лот `RESERVED → PICKED_UP`; зберегти `pickupWeightKg`, `pickedUpAt`; `now > reservedUntil` → запізнення;
- `delivery`: `PICKED_UP → DELIVERED`; пункт не приймає категорію лоту → 422; згенерувати 6-значний код;
- `confirmation`: неправильний код → 422; `|receivedWeightKg − pickupWeightKg|` понад 10% від `pickupWeightKg` → `DISPUTED`, інакше `CONFIRMED`;
- викликати `VolunteerStatsRecorder`: `recordPickup` при передачі, `recordStrike` при `DISPUTED`, `recordCompletedDelivery` при `CONFIRMED`. Методи вже є в каркасі — Людину 2 не чекати.

`confirmationCode` зараз повертається у відповіді всім — прибрати доступ можна буде лише після лекції про Security.

---

## 11. Тести — у кожного

`@WebMvcTest(ТвійController.class)` + сервіс через **`@MockitoBean`**. У Spring Boot 4 старого `@MockBean` уже немає — перевірено по бібліотеках.
Шаблон тесту з усіма чотирма перевірками є в каркасі — копіюєш під свій контролер.

Тести мокають власний сервіс, тож чужий код для них не потрібен узагалі.

Обов'язкові чотири на кожен контролер:

| # | Сценарій | Очікуємо |
|---|----------|----------|
| 1 | Валідний запит | Правильний статус **і** `verify(service).метод(...)` |
| 2 | Невалідне тіло | 400, у відповіді `errors` |
| 3 | Сервіс кидає бізнес-виняток (`given(...).willThrow(...)`) | Правильний статус: 403 / 404 / 409 / 422 |
| 4 | Невідоме поле в JSON | 400 |

---

## 12. Git і злиття

- Гілки від `main` з каркасом: `lot`, `volunteer`, `delivery`.
- Зливаєш свою гілку в `main`, **коли захочеш** — конфліктів не буде, бо пакети різні. Чим частіше, тим краще: інші одразу бачать твій код.
- Перед кожним злиттям — `./mvnw test` зелений.
- Протягом дня свій кейс перевіряєш на **демо-даних** (§7) — чужих ендпоінтів не чекаєш.

### Наскрізний сценарій — наприкінці, коли все злито

1. `POST /destination-points` → запам'ятати `id` пункту
2. `POST /volunteers` → запам'ятати `id` волонтера
3. `POST /lots` → 201, запам'ятати `id` лоту з `Location`
4. `POST /lots/{id}/publication` → `PUBLISHED`
5. `POST /lots/{id}/reservation` з `volunteerId` → 201
6. `POST /lots/{id}/pickup` → `PICKED_UP`
7. `POST /lots/{id}/delivery` з `destinationPointId` → `DELIVERED`, у відповіді код
8. `POST /lots/{id}/confirmation` з кодом → `CONFIRMED`
9. `GET /lots/{id}/history` → шість переходів від `DRAFT` до `CONFIRMED`

Кожен крок має проходити без правок коду. Якщо ні — розбіжність у контракті, виправляємо одразу.

---

## 13. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| До початку дня | — | Каркас у `main`, проєкт компілюється |
| 15 хв | разом | Короткий розбір каркаса: хто за яку частину `common` відповідає на захисті |
| Години 1-4 | кожен окремо | Свій кейс на демо-даних, обов'язкова частина. Зливаєш у `main`, коли готово |
| Години 5-6 | кожен окремо | Тести |
| 30 хв | разом | Усе в `main` + наскрізний сценарій |
| Година 7 | разом | Кожен пояснює свій кейс двом іншим · `./mvnw test` · запуск із розпакованого архіву · distedu |
| Якщо лишився час | кожен окремо | Пункти «якщо встигнете» |

Разом збираєтесь лише тричі: на початку, для злиття і наприкінці. Решту часу ніхто ні від кого не залежить.
