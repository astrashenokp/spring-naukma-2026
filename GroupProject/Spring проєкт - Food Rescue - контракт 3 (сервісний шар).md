# Food Rescue — контракт №3: сервісний шар

Групове завдання лекції 3 (DI + IoC, бізнес-логіка). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №2, і нічого більше.** Розділ 2 каже, що в ньому змінюється. URL, DTO і правила ендпоінтів з розділу 10 контракту №2 лишаються.

---

## 1. Що вимагає викладач

| # | Вимога | Звідки |
|---|--------|--------|
| R1 | Компоненти взаємодіють **лише через інтерфейси**: контролер → інтерфейс сервісу, сервіс → інтерфейс сховища | слайд 53; слайд 55 |
| R2 | Сервіс — Stateless Singleton (лише `final`-поля), залежності **тільки через конструктор**, жодного `@Autowired` на полях | слайди 53, 55 |
| R3 | Формальна модель переходів станів лоту й суворий контроль бізнес-правил | слайд 53 |
| R4 | Типізовані доменні винятки при порушенні правил чи недозволеному переході | слайд 53 |
| R5 | Варіативна логіка — патерн «Стратегія»; колекція `List<…>` впроваджується Spring **без `@Qualifier`** | слайд 54; слова: «логіка була динамічна» |
| R6 | Побічні дії через події: **один сервіс публікує подію після збереження**, на неї підписані **мінімум два** компоненти (`@ApplicationModuleListener`) | слайд 54; слова: «якщо не збережеться, немає сенсу робити цю подію», «має бути мінімум 2» |
| R7 | **Усі** бізнес-сервіси покриті модульними тестами на Mockito без запуску контексту; перевіряємо, що **не викликано зайвого** | слайд 54; слова: «перевірка, що ви не викликали нікого зайвого» |
| R8 | `README.md`: опис бізнес-правил і **матриця переходів станів** | слайд 54 |
| R9 | Помилки повертаються як `ProblemDetail` через глобальний обробник | слайд 55; слова про «проблем дітейл» |
| R10 | Перевірка: `./mvnw test` → `BUILD SUCCESS`; `ApplicationModules.of(...).verify()`; аудит (контролери лише з інтерфейсами, без `@Autowired`); **журнал** показує асинхронне виконання побічних дій | слайд 55 |

Про журнал він прямо сказав: справжнє логування ще не проходили, **достатньо `System.out.println`**.

---

## 2. Вихідні умови та відкриті питання

**Вихідна точка** — код у гілці `main` за звітом про репозиторій (коміт `5a55f5e`): усе відповідає контракту №2, 13 тестів зелені, Spring Modulith у `pom.xml` немає. Єдине порушення меж модулів — `delivery` імпортує `volunteer.VolunteerStatsRecorder`; решта чиста.

Що з цим робимо:

| Що є в коді | Що робимо | Навіщо |
|-------------|-----------|--------|
| Підпакети `common.lot`, `common.error`, `common.history`, `common.demo` | Усі класи переносимо прямо в `common`, підпакети прибираємо | Інакше `verify()` червоний (розділ 3) |
| `LotStore` (клас) | `LotRepository` (інтерфейс: `save`, `findById`, `findAll`) + `InMemoryLotRepository`. Метод `getById` зникає: сервіс пише `findById(id).orElseThrow(() -> new LotNotFoundException(id))` | R1. Так типізований виняток видно в самому сервісі, і його можна протестувати |
| `VolunteerStore`, `DeliveryStore`, `DestinationPointStore` (класи) | Так само: інтерфейс `…Repository` + `InMemory…Repository`; `getById` / `getByLotId` → `findById` / `findByLotId` + `orElseThrow` | R1 |
| `LotStatusChanger`, `StatusHistoryRecorder` (класи) | Інтерфейси + `…Impl`. **Зміна поведінки:** недозволений перехід кидає `InvalidLotStateException` → **422** замість `ConflictException` → 409. `StatusHistoryRecorder` тримає історію у звичайному `ArrayList` — перевести на `CopyOnWriteArrayList` | R1. Лекція: 422 — недозволений перехід. Сховища мусять бути потокобезпечні |
| `LotService`, `VolunteerService`, `DeliveryService` (класи) | Інтерфейс отримує **ім'я старого класу**, клас стає `…Impl`. Контролери й тести `@WebMvcTest` не міняються. Нових сервісів (`ReservationService` та подібних) **не створюємо**: резервування лишається у `VolunteerService`, пункти призначення — у `DeliveryService` | R1, R2 |
| `VolunteerStatsRecorder` | **Видаляємо разом з 3 викликами з `DeliveryService`.** Логіку лічильників Людина 2 бере з історії git у `VolunteerService.recordOutcome`. `Delivery` отримує поле `latePickup` | R6. Прямий виклик `delivery → volunteer` дає цикл модулів |
| Ланцюжок `if` у `VolunteerService.reserve` (великий лот, `EARLY_ACCESS_*`, `isRestricted`) | Виноситься в стратегію `ReservationAccessStrategy` | R5 |
| Допуск ваги `MAX_WEIGHT_DIFFERENCE` (10%) у `DeliveryService` | Виноситься в стратегію `WeightToleranceStrategy` | R5 |
| Прямі `new BusinessRuleException(...)`, `new ForbiddenActionException(...)` у сервісах; `NotFoundException` зі сховищ | Типізовані нащадки (розділ 4.4) | R4 |
| `Instant.now()` у сервісах і в `StatusHistoryRecorder` | `Instant.now(clock)` з біна `Clock` | Тести з часом стають детермінованими |
| Тести `@WebMvcTest` (3 класи по 4 тести) | Лишаються без змін | Мусять проходити |
| `FoodRescueApplicationTests` (`@SpringBootTest`) | Видалити | Слайд 55 |
| Премодерація (`PENDING_MODERATION`, `approve`), фільтри списку лотів, `GET /history` | **Лишаються як є**, лише переходять на інтерфейси | Уже написано й працює |

**Відкриті питання — з'ясувати до старту:**

| Питання | Що відомо |
|---------|-----------|
| Дедлайн групового №3 | Ніде не вказаний (ні на слайдах, ні в його словах). Перевірити на distedu. Якщо як минулого разу «1 тиждень» — це 23.09, але це припущення |
| Формат здачі | Для попереднього групового він сказав: **архів, а не git** («з гітом бувають проблеми») → здаємо архів |

---

## 3. Архітектура модулів

Spring Modulith вважає **кожен пакет першого рівня модулем**. `verify()` (R10) перевіряє кордони між ними. Викладач сказав: «кожна бізнес-частина повинна мати свій модуль» — саме так і розкладено.

```
com.example.foodrescue
├── common       спільне ядро (лот, статуси, винятки, обробник, інфраструктура) — БЕЗ підпакетів
├── lot          Людина 1
├── volunteer    Людина 2
└── delivery     Людина 3   ← публікує подію
```

Дозволені залежності між модулями:

```
lot        ──►  common
volunteer  ──►  common
delivery   ──►  common
lot        ──►  delivery     (лише тип події та DeliveryOutcome)
volunteer  ──►  delivery     (лише тип події та DeliveryOutcome)
```

**Правила, перевірені на робочому прикладі** (порушення = `verify()` червоний):

| Правило | Що станеться при порушенні |
|---------|----------------------------|
| `delivery` **не імпортує** `lot` і `volunteer`; `lot` і `volunteer` не імпортують один одного | `Cycle detected: Slice delivery -> Slice lot ->` |
| У `common` **немає підпакетів**. Клас, яким користуються інші модулі, лежить прямо в `common` | `Module 'delivery' depends on non-exposed type …common.error.SomeError within module 'common'!` |
| Усе, що стосується двох модулів одночасно (лот, статуси, винятки), — в `common`; усе інше — у своєму модулі | — |

Тому підпакети `common.lot`, `common.error`, `common.history`, `common.demo` **прибираємо**: класи переїжджають у `common` напряму. Це стосується й `DemoData`: на його константу `DEMO_VOLUNTEER` посилається модуль `volunteer`.

Прямий виклик між модулями (як `VolunteerStatsRecorder` у №2) **замінюється подією** — саме це й називає викладач послабленням зв'язності.

---

## 4. База (міграція спільної частини) — робить Людина 1 до старту

Це єдиний крок, який мусить відбутися **першим**: перенесення пакетів міняє імпорти в усіх файлах, тож паралельна робота з тими самими файлами дає купу конфліктів. Щоб під час роботи ніхто нікого не чекав, **Людина 1 готує всю базу заздалегідь** і зливає її в `main` одним злиттям. Орієнтир — 1,5-2 години.

Поки Людина 1 зайнята базою, Людини 2 і 3 не сидять без діла: читають лекцію та розділ 6 і можуть уже писати **нові** файли у своєму пакеті (стратегії, винятки, слухач). Вони не чіпають наявних файлів; після злиття бази лише виправляється імпорт `FoodCategory` та подібних. Коли база злита, всі троє працюють паралельно й нікого не чекають.

| Крок | Що зробити |
|------|-----------|
| 1 | `pom.xml`: залежності Modulith (4.1) |
| 2 | Перенести класи з `common.lot`, `common.error`, `common.history`, `common.demo` у `common` (в IDE: Move), підпакети видалити |
| 3 | `LotStore` → `LotRepository` + `InMemoryLotRepository`. Виклики `lotStore.getById(...)` у `LotService`, `VolunteerService`, `DeliveryService` (близько 11 місць) замінити на `lotRepository.findById(...).orElseThrow(() -> new LotNotFoundException(id))` |
| 4 | `LotStatusChanger` і `StatusHistoryRecorder` → інтерфейси + `…Impl`; недозволений перехід кидає `InvalidLotStateException`; історія — на `CopyOnWriteArrayList`, а `StatusHistoryRecorderImpl` позначити `@Repository`: він тримає дані, як сховище, тож це не порушує Stateless для сервісів; додати `LotNotFoundException` і `InvalidLotStateException` (4.4) |
| 5 | Видалити `VolunteerStatsRecorder` і його 3 виклики з `DeliveryService` |
| 6 | `InfrastructureConfig`, `NoOpTransactionManager` (4.2) |
| 7 | `GlobalExceptionHandler` уже ловить `BusinessException` і має `@Order(Ordered.HIGHEST_PRECEDENCE)` — після переносу лише перевірити імпорти |
| 8 | `ModulesTest` (архітектурний тест `verify()`) |
| 9 | `README.md`: скелет розділів і матриця переходів |
| 10 | Видалити `FoodRescueApplicationTests` |
| 11 | Створити `DeliveryOutcome` і `DeliveryFinishedEvent` у пакеті `delivery` (розділ 5): їх використовують `lot` і `volunteer`, тож вони мають існувати ще до того, як Людина 3 дійде до свого треку |

Тести `LotStatusChangerImplTest` і `StatusHistoryRecorderImplTest` у базу **не входять**: їх у своєму треку пише Людина 2 (розділ 7).

Власні сховища й сервіси (`VolunteerStore`, `DeliveryStore`, `DestinationPointStore`, `VolunteerService`, `DeliveryService`, `LotService`) у базу **не входять** — їх кожен переробляє у своєму треку.

**Готово, коли:** проєкт збирається, `./mvnw test` зелений (13 наявних тестів), `ModulesTest` зелений. Після кроків 2 і 5 порушень `verify()` лишитися не має: звіт показує, що єдиним був `delivery → volunteer`.

### 4.1. `pom.xml`

Версія Modulith **2.1.1**. Саме її підбирає Initializr для Boot 4.1.1. У прикладі викладача було `1.3.1` — це версія для іншого Boot, копіювати її не можна.

```xml
<properties>
    <spring-modulith.version>2.1.1</spring-modulith.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-events-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

`spring-modulith-events-api` додається **окремо** — без неї `@ApplicationModuleListener` не компілюється (перевірено). Mockito вже приходить із `spring-boot-starter-webmvc-test`.

### 4.2. Інфраструктура для подій — усі чотири елементи обов'язкові

`@ApplicationModuleListener` спрацьовує лише **після коміту транзакції** і **асинхронно**. У проєкті без бази даних нічого з цього не вмикається саме. Кожен пункт перевірено окремим запуском:

| Чого немає | Що відбувається |
|------------|-----------------|
| `@EnableTransactionManagement` | `@Transactional` **мовчки не діє**, слухачі **не викликаються взагалі**, помилки немає |
| `@EnableAsync` | Слухачі працюють у потоці HTTP (`http-nio-8080-exec-1`) і **затримують відповідь** — вимога про асинхронність провалена, помилки немає |
| Бін `TransactionManager` | `500`, `NoSuchBeanDefinitionException` |
| `@Transactional` на методі-видавці | Подія губиться, помилки немає |

```java
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableAsync
public class InfrastructureConfig {

    @Bean
    public Clock appClock() {
        return Clock.systemUTC();
    }
}
```

```java
@Component
public class NoOpTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
    }
}
```

`NoOpTransactionManager` потрібен лише тому, що бази даних немає: без нього Spring не має на що чіпляти `@Transactional`. Реальний менеджер з'явиться разом із БД.

`Clock` — рядок зі слайда 10; потрібен, щоб правила з часом (`вікно в майбутньому`, `таймер резерву`) можна було тестувати. Усі сервіси, що працюють із часом, беруть `Clock` у конструкторі.

### 4.3. Формальна модель переходів (R3)

```java
public enum LotStatus {
    DRAFT, PENDING_MODERATION, PUBLISHED, RESERVED, PICKED_UP,
    DELIVERED, CONFIRMED, DISPUTED, EXPIRED, CANCELLED, FAILED;

    public boolean canTransitionTo(LotStatus next) {
        return switch (this) {
            case DRAFT              -> next == PENDING_MODERATION || next == PUBLISHED || next == CANCELLED;
            case PENDING_MODERATION -> next == PUBLISHED || next == DRAFT || next == CANCELLED;
            case PUBLISHED          -> next == RESERVED || next == CANCELLED || next == EXPIRED;
            case RESERVED           -> next == PUBLISHED || next == PICKED_UP;
            case PICKED_UP          -> next == DELIVERED || next == FAILED;
            case DELIVERED          -> next == CONFIRMED || next == DISPUTED;
            case CONFIRMED, DISPUTED, EXPIRED, CANCELLED, FAILED -> false;
        };
    }
}
```

`EXPIRED` і `FAILED` у матриці є, але жоден сервіс їх зараз не виставляє: це робитиме планувальник пізніше (БК-4, не в цьому завданні).

Матриця вже є в коді й збігається з таблицею вище (звірено). Її реалізація через `EnumMap` лишається як є. Перевірку `canTransitionTo` виконує `LotStatusChanger`: це єдине місце в проєкті, недозволений перехід дає `InvalidLotStateException` (422).

**Як сервіс змінює статус** (однаково в усіх трьох):

```java
FoodLot lot = lotRepository.findById(lotId)
        .orElseThrow(() -> new LotNotFoundException(lotId));

// інші правила свого кейсу: кидають свої винятки ДО зміни стану

statusChanger.transition(lot, LotStatus.RESERVED, "Зарезервовано");   // 422 + запис історії

// лише після успішного переходу — свої поля
lot.setReservedByVolunteerId(volunteerId);
lot.setReservedUntil(Instant.now(clock).plus(30, ChronoUnit.MINUTES));
lotRepository.save(lot);
```

Порядок важливий: якщо спершу змінити поля, а перехід потім кине виняток, лот залишиться напівзміненим.

### 4.4. Винятки й обробник (R4, R9)

Ієрархія з №2 **лишається**: `BusinessException` зі статусом і чотири нащадки. Типізовані винятки — це власні класи, що успадковують один із чотирьох. Нижче все, що потрібно в цьому завданні; уже наявне позначено.

| Базовий клас | Статус | Типізовані винятки |
|--------------|--------|--------------------|
| `NotFoundException` | 404 | `LotNotFoundException` (у `common`, конструктор `(UUID id)`), `VolunteerNotFoundException`, `DestinationPointNotFoundException`, `DeliveryNotFoundException` |
| `ConflictException` | 409 | `LotNotDraftException` (**вже є**), `DuplicateVolunteerException`, `DuplicateDestinationPointException` |
| `ForbiddenActionException` | 403 | `ReservationDeniedException` (замінює `VolunteerRestrictedException`) |
| `BusinessRuleException` | 422 | `InvalidLotStateException` (у `common`), `InvalidPickupWindowException`, `CategoryNotAcceptedException`, `InvalidConfirmationCodeException` |

```java
public class InvalidLotStateException extends BusinessRuleException {
    public InvalidLotStateException(String message) {
        super(message);
    }
}
```

Спільні (`LotNotFoundException`, `InvalidLotStateException`) лежать у `common`, решту кожен створює у своєму модулі.

`GlobalExceptionHandler` уже ловить `BusinessException` за `getStatus()`, тому **новий виняток додається без правок обробника**. Обробник валідації (з `@Order(Ordered.HIGHEST_PRECEDENCE)`) лишається в цьому ж класі.

Статуси за домовленістю лекції: **404** — не знайдено, **409** — дублікат, **422** — порушення бізнес-правила чи недозволений перехід, **403** — дію заборонено.

---

## 5. Подія (R6)

Лежить у модулі-видавці, у **базовому пакеті** `delivery`, щоб її бачили інші модулі. Обидва типи створює крок 11 міграції, тому `lot` і `volunteer` компілюються з самого початку й не чекають Людину 3:

```java
public enum DeliveryOutcome { CONFIRMED, DISPUTED }

public record DeliveryFinishedEvent(
        UUID lotId,
        UUID donorOrgId,
        UUID volunteerId,
        DeliveryOutcome outcome,
        boolean latePickup) {
}
```

| Роль | Хто | Що робить |
|------|-----|-----------|
| Видавець | `DeliveryService.confirm` (Людина 3) | Публікує подію через `ApplicationEventPublisher` **останньою дією, після `save`** лоту й доставки: якщо збереження не вдалося, події не буде. Метод **`@Transactional`**. Значення `latePickup` бере з `Delivery`, куди його записав `pickup` |
| Слухач 1 | `lot.DonorStatsListener` (Людина 1) | `@ApplicationModuleListener` → `DonorStatsService.recordOutcome(...)` |
| Слухач 2 | `volunteer.VolunteerStatsListener` (Людина 2) | `@ApplicationModuleListener` → `VolunteerService.recordOutcome(...)` |

Слухач — тонкий: лише передає дані сервісу. Бізнес-логіка живе в сервісі, її й тестуємо.

**Кожен слухач друкує один рядок з іменем потоку**: це і є «журнал підтверджує асинхронність»:

```java
System.out.println("[" + Thread.currentThread().getName() + "] donor stats: lot " + event.lotId() + " " + event.outcome());
```

Очікуваний журнал: видавець працює в `http-nio-8080-exec-N`, слухачі — у `task-N`. Якщо слухач надрукував `http-nio…` — асинхронність не ввімкнена.

Якщо `confirm` кидає виняток, транзакція відкочується й подія **не** публікується — це правильно, слухачі виконуються лише після успіху.

---

## 6. Розподіл — рівно порівну

Кожен веде **один бізнес-кейс наскрізь** у своєму модулі: сервіс, сховище-інтерфейс із реалізацією, одна родина стратегій, свої винятки, тести. Поділу «ти пишеш DTO, ти — тести» немає: викладач сказав, що на захисті кожен відповідає за все.

Правила **оновлення**, про які він питав («які правила у вас є оновлення?»), є там, де оновлення існує: `LotService.update` (правило 1.2). В інших кейсах стан змінюють переходи й `recordOutcome`.

Три наявні сервіси лишаються **трьома** (`LotService`, `VolunteerService`, `DeliveryService`): дробити їх не потрібно. Додається один новий, `DonorStatsService`, бо слухач події має де тримати логіку.

| | Людина 1 — БК-1 | Людина 2 — БК-2 | Людина 3 — БК-3 |
|---|---|---|---|
| Модуль | `lot` | `volunteer` | `delivery` |
| Сервіси | `LotService`, `DonorStatsService` (новий) | `VolunteerService` | `DeliveryService` |
| Методів у сервісах | 8 | 5 | 6 |
| з них із логікою | 6 | 4 | 4 |
| Бізнес-правил | 4 | 4 | 4 |
| Родин стратегій | 1 | 1 | 1 |
| Роль у події | слухач | слухач | **видавець** |
| Розділів README | 1 | 1 | 1 |
| Крім кейсу | **база** (розділ 4) — до старту, поза основною роботою | тести `LotStatusChangerImpl` і `StatusHistoryRecorderImpl` (розділ 7) | — |

У робочий день (за грубою оцінкою: класи, які треба написати чи переробити, плюс тести) навантаження приблизно порівну. Людина 1 бере базу **додатково, до старту**: це її внесок, який знімає чекання з інших. Її власний кейс легший — `update`, `cancel`, `approve` це тонкі обгортки над готовими переходами. У Людини 3 найважчі методи (код підтвердження, допуск ваги, публікація події), 5 нових винятків і найбільший тестовий клас. Якщо хтось хоче обмінятися дрібницею — можна вільно.

**Для всіх трьох однаково:**
- Наявний сервіс: інтерфейс отримує **ім'я старого класу**, клас стає `…Impl` з `@Service`. Усі поля `final`, залежності лише через конструктор.
- Кожне наявне `…Store` стає інтерфейсом `…Repository` + `InMemory…Repository` на `ConcurrentHashMap` (слухачі працюють в іншому потоці, тож сховища мусять бути потокобезпечні).
- Час — лише через `Clock`: `Instant.now(clock)`.
- Прямі `new BusinessRuleException(...)` та `new ForbiddenActionException(...)` замінюються типізованими винятками (4.4).
- Стратегії — окремі `@Component`, сервіс приймає `List<…>` у конструкторі й будує з нього `Map` (слайд 27), **без `@Qualifier`**.
- Контролер і його `@WebMvcTest` не міняються: інтерфейс має те саме ім'я, що й старий клас.
- Тести з розділу 7.
- Після міграції `common` **не міняємо**. Якщо чогось бракує — пишемо в чат: інакше двоє правитимуть ті самі файли й отримають конфлікт.

### Людина 1 — БК-1: публікація лоту (модуль `lot`)

**Сервіси**
- `LotService`: `create`, `update`, `publish`, `cancel`, `approve` і читання `findAll`, `getById`. Премодерація й `approve` **вже написані** — лишаються, лише переходять на інтерфейси й `Clock`.
- `DonorStatsService` (новий): `recordOutcome(UUID donorOrgId, DeliveryOutcome outcome)` — збільшує лічильник підтверджених або спірних лотів донора.

**Нові класи:** `DonorStats(donorOrgId, confirmedLots, disputedLots)`, `DonorStatsRepository` + `InMemoryDonorStatsRepository`, `DonorStatsListener`

**Бізнес-правила**

| # | Правило | Виняток |
|---|---------|---------|
| 1.1 | `pickupTo` пізніше за `pickupFrom`, `pickupFrom` у майбутньому, тривалість вікна ≥ мінімуму для категорії (стратегія). Перевірку довжини вікна, якщо вона зараз у `PickupWindowValidator`, переносимо в сервіс; валідатор лишає структурні перевірки | `InvalidPickupWindowException` → 422 |
| 1.2 | `update` дозволений лише в `DRAFT` | `LotNotDraftException` → 409 (**вже є**) |
| 1.3 | `publish`: перехід `DRAFT → PUBLISHED`, виставити `publishedAt`. Якщо в донора ≥ 5 лотів і скасовано понад 20% — лот іде в `PENDING_MODERATION` (**вже є**) | `InvalidLotStateException` → 422 |
| 1.4 | `cancel` дозволений з `DRAFT`, `PENDING_MODERATION`, `PUBLISHED`; з `RESERVED` і далі — заборонено | `InvalidLotStateException` → 422 |

Неіснуючий лот → `LotNotFoundException` → 404.

**Стратегія `PickupWindowStrategy`**: `FoodCategory category()`, `int minWindowMinutes()`

| Реалізація | Категорія | Мінімальне вікно, хв |
|------------|-----------|----------------------|
| `PreparedMealPickupWindow` | `PREPARED_MEAL` | 45 |
| `BakeryPickupWindow` | `BAKERY` | 60 |
| `VegetablesPickupWindow` | `VEGETABLES` | 90 |
| `GroceryPickupWindow` | `GROCERY` | 120 |

### Людина 2 — БК-2: волонтери та резервування (модуль `volunteer`)

**Сервіси**
- `VolunteerService`: `create`, `getById`, `reserve(UUID lotId, ReservationRequest)`, `cancelReservation(UUID lotId)`, `recordOutcome(UUID volunteerId, DeliveryOutcome outcome, boolean latePickup)`. Резервування лишається тут: окремого `ReservationService` немає й не робимо.

**Класи:** `VolunteerProfile` (є), `VolunteerRepository` (з `VolunteerStore`: `save`, `findById`, **новий** `existsByEmail`) + `InMemoryVolunteerRepository`, нові `VolunteerTier { TRUSTED, STANDARD, RESTRICTED }` і `VolunteerStatsListener`. `VolunteerDemoData` лишається.

**Рівень волонтера.** Формули вже є у `VolunteerProfile` (`getResponsibilityScore()`, `isRestricted()`) — не міняємо. Рівень: `RESTRICTED`, якщо `isRestricted()`; інакше `TRUSTED`, якщо показник понад 90; інакше `STANDARD`.

**Бізнес-правила**

| # | Правило | Виняток |
|---|---------|---------|
| 2.1 | Пошта волонтера унікальна (**нове**) | `DuplicateVolunteerException` → 409 |
| 2.2 | `reserve`: перехід `PUBLISHED → RESERVED` | `InvalidLotStateException` → 422 |
| 2.3 | `reserve`: доступ за рівнем волонтера (стратегія) | `ReservationDeniedException` → 403 (замінює `VolunteerRestrictedException` і пряму `ForbiddenActionException`) |
| 2.4 | `cancelReservation`: перехід `RESERVED → PUBLISHED`, поля резерву очищаються | `InvalidLotStateException` → 422 |

Успішний `reserve` ставить `reservedByVolunteerId` і `reservedUntil = зараз + 30 хв` (як і зараз). Неіснуючий волонтер → `VolunteerNotFoundException` → 404.

**`recordOutcome`:** `CONFIRMED` → `completedDeliveries + 1`; `DISPUTED` → `noShows + 1` (так само, як робив `recordStrike`); `latePickup` → ще й `latePickups + 1`. Метод `synchronized`, як і `reserve`: він працює в іншому потоці.

**Стратегія `ReservationAccessStrategy`**: `VolunteerTier tier()`, `boolean canReserve(FoodLot lot, Instant now)`. Сталі `EARLY_ACCESS_MINUTES`, поріг 90 і «великий лот від 20 кг» переїжджають зі `VolunteerService` у стратегії.

| Реалізація | Рівень | Правило |
|------------|--------|---------|
| `TrustedAccess` | `TRUSTED` | Без обмежень |
| `StandardAccess` | `STANDARD` | Великий лот (від 20 кг) закритий перші 10 хв після `publishedAt` |
| `RestrictedAccess` | `RESTRICTED` | Лише `BAKERY` і `GROCERY`; великі лоти перші 10 хв закриті так само |

### Людина 3 — БК-3: передача, доставка, підтвердження (модуль `delivery`)

**Сервіси**
- `DeliveryService`: `createDestinationPoint`, `getDestinationPoint`, `pickup`, `deliver`, `confirm`, `getHistory`. Пункти призначення лишаються тут: окремого `DestinationPointService` немає й не робимо. `getHistory` працює через `StatusHistoryRecorder` і не міняється.

**Класи:** `Delivery` (є; **додати поле `boolean latePickup`**), `DeliveryRepository` (з `DeliveryStore`: `save`, `findByLotId`) + `InMemoryDeliveryRepository`, `DestinationPoint` (є), `DestinationPointRepository` (з `DestinationPointStore`: `save`, `findById`, **новий** `existsByName`) + `InMemoryDestinationPointRepository`; `DeliveryFinishedEvent` і `DeliveryOutcome` створює міграція (крок 11), Людина 3 лише публікує подію

**Бізнес-правила**

| # | Правило | Виняток |
|---|---------|---------|
| 3.1 | `pickup`: перехід `RESERVED → PICKED_UP`; `latePickup = зараз > reservedUntil` записується в `Delivery` | `InvalidLotStateException` → 422 |
| 3.2 | `deliver`: перехід `PICKED_UP → DELIVERED`; пункт має приймати категорію лоту (зараз це простий `BusinessRuleException`); код 6 цифр, як і зараз | `InvalidLotStateException` → 422; `CategoryNotAcceptedException` → 422 |
| 3.3 | `confirm`: неправильний код заборонений (зараз простий `BusinessRuleException`); розбіжність ваги понад допуск (стратегія) → `DISPUTED`, інакше `CONFIRMED`; після успіху публікується подія | `InvalidLotStateException` → 422; `InvalidConfirmationCodeException` → 422 |
| 3.4 | Назва пункту призначення унікальна (**нове**) | `DuplicateDestinationPointException` → 409 |

Неіснуючий пункт → `DestinationPointNotFoundException` → 404; немає запису доставки → `DeliveryNotFoundException` → 404.

**Зміна часу оновлення лічильників.** Раніше запізнення волонтера рахувалося одразу при `pickup`. Тепер `pickup` лише записує `latePickup` у `Delivery`, а лічильники оновлює слухач після `confirm`. Це наслідок того, що подія одна.

**Стратегія `WeightToleranceStrategy`**: `FoodCategory category()`, `BigDecimal tolerancePercent()`. Відхилення: `|отримано − при передачі| / при передачі × 100`, порівнювати через `compareTo`.

| Реалізація | Категорія | Допуск, % |
|------------|-----------|-----------|
| `PreparedMealTolerance` | `PREPARED_MEAL` | 10 |
| `BakeryTolerance` | `BAKERY` | 10 |
| `VegetablesTolerance` | `VEGETABLES` | 15 |
| `GroceryTolerance` | `GROCERY` | 5 |

---

## 7. Тести (R7)

Один тестовий клас на кожен сервіс, у тому ж модулі. `@ExtendWith(MockitoExtension.class)`, **без** `@SpringBootTest`. Сервіс створюється через конструктор, залежності — `@Mock`.

| Що покрити | Як |
|------------|----|
| Кожен публічний метод сервісу | Мінімум один успішний сценарій |
| Кожне бізнес-правило | Мінімум один сценарій порушення: `assertThrows` + `verify(repository, never()).save(any())` |
| Недозволений перехід | Сервіс: мок `LotStatusChanger` кидає `InvalidLotStateException` → сервіс **не** зберігає і **не** публікує. Сама матриця — у `LotStatusChangerImplTest` (Людина 2): дозволений перехід ставить статус і пише історію, недозволений кидає виняток і історії не пише |
| Кожна реалізація стратегії | Повертає своє значення; сервіс обирає її за ключем |
| **«Не викликали зайвого»** | `verifyNoMoreInteractions(...)` на всіх моках сервісу |
| Видавець події (Людина 3) | Успіх → `verify(publisher).publishEvent(any(DeliveryFinishedEvent.class))`; будь-який виняток → `verify(publisher, never()).publishEvent(any())` |
| Слухачі (Люди 1 і 2) | Слухач викликає метод сервісу з даними події |
| Час | Через `Clock.fixed(...)` |

Тести контролерів `@WebMvcTest` з №2 лишаються й мусять проходити; тепер вони мокають **інтерфейс сервісу**. Це зрізовий, а не повний контекст.

---

## 8. README.md (R8)

У корені проєкту. Скелет із заголовками створює міграція (розділ 4), кожен пише свій розділ.

1. Що це за проєкт — 2-3 речення
2. Як запустити й перевірити (`./mvnw test`, `./mvnw spring-boot:run`)
3. Модулі й правила залежностей (розділ 3)
4. **Матриця переходів станів лоту** — таблиця: рядки «з», стовпці «до», `✓` = дозволено. Джерело істини — `canTransitionTo` з розділу 4.3
5. Бізнес-правила — по розділу на кожен кейс (таблиці правил з розділу 6): правило, виняток, HTTP-статус
6. Стратегії — таблиця значень
7. Події: хто публікує, хто слухає, що роблять слухачі

---

## 9. Готово, коли (як перевірятиме викладач, R10)

Кожен перевіряє це на **розпакованому архіві** в порожній папці, а не в IDE.

**1. Тести й архітектура**
```
./mvnw test
```
Очікуємо `BUILD SUCCESS`, серед тестів — `ModulesTest` (`verify()`).

**2. Аудит архітектури** — усі команди мають повернути **порожньо**:
```
grep -rn "@Autowired" src/main
grep -rnE "LotStore|VolunteerStore|DeliveryStore|DestinationPointStore|VolunteerStatsRecorder" src
grep -rn "@Qualifier" src/main
grep -rln "Impl" src/main --include=*Controller.java
grep -rn "@SpringBootTest" src/test
```

**3. Наскрізний сценарій через `curl`**

| Крок | Очікуємо |
|------|----------|
| Створити пункт призначення | 201 |
| Створити пункт з тією ж назвою | **409**, `ProblemDetail` |
| Зареєструвати волонтера | 201 |
| Зареєструвати ще раз з тією ж поштою | **409** |
| Створити лот → опублікувати | 201 → 200, статус `PUBLISHED` |
| Зарезервувати | 201 |
| Зарезервувати той самий лот вдруге | **422**, `ProblemDetail` (недозволений перехід) |
| `pickup` → `deliver` → `confirm` з правильним кодом | 200, 200, 200 |
| `confirm` з неправильним кодом | **422** |

**4. Журнал після `confirm`** — два рядки слухачів у потоках `task-N`, не `http-nio-…`:
```
[task-1] donor stats: lot … CONFIRMED
[task-2] volunteer stats: lot … CONFIRMED
```

---

## 10. Чого не робимо

Викладач цього не вимагає, тож не робимо:

- бази даних, Spring Security, Swagger, Thymeleaf, AOP, кешу — це теми наступних лекцій;
- планувальника (БК-4) і Spring Batch (БК-5);
- справжнього логування — лише `System.out.println`;
- тестів із `@SpringBootTest`;
- нових ендпоінтів, яких немає в контракті №2;
- нової логіки премодерації й `approve`: те, що вже є, лишається як є;
- правил і полів, яких немає в ТЗ і в цьому файлі.

---

## 11. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| Зараз | разом | Домовитися, хто Людина 1, 2 і 3. Прочитати розділи 3, 5 і 6 |
| До старту | Людина 1 | База, розділ 4, одним злиттям у `main`. Проєкт збирається, `ModulesTest` зелений. Людини 2 і 3 не чекають: читають лекцію й розділ 6 та можуть уже писати нові файли у своєму пакеті |
| Основний час | кожен окремо | Нова гілка від `main` (наприклад `svc-lot`, `svc-volunteer`, `svc-delivery`; старі `lot` і `volunteer` не чіпаємо). Свій кейс: сервіс, сховище, стратегія, винятки, тести, слухач або видавець, розділ README |
| Перед здачею | разом | Злити все в `main`; пройти розділ 9 на розпакованому архіві |
| Здача | разом | Архів на distedu. Кожен готовий пояснити **будь-яку** частину, не лише свою |
