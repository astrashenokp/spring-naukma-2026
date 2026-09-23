# Food Rescue — контракт №4: база даних (JPA)

Групове завдання лекції 4 (Spring Data JPA, H2, зв'язки, `JOIN FETCH`). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №3 і злитий у `main`.** Правила з №2 і №3 (URL, DTO, винятки, `ProblemDetail`, інтерфейси сервісів, стратегії, подія, Mockito-тести, `verify()`) **лишаються**. Тут змінюється лише шар збереження: «колекції» стають справжньою базою даних.

> Старт — коли контракт №3 злитий у `main`, а `./mvnw test` зелений. Якщо це ще не так, спершу закриваємо №3.

---

## 1. Що вимагає викладач

| # | Вимога | Звідки |
|---|--------|--------|
| R1 | Схема командного проєкту: **щонайменше 4 пов'язані сутності з різними типами відношень** | слайд 60 |
| R2 | **Повноцінний CRUD** для кожної з цих сутностей: REST-контролер → сервісний шар → репозиторій | слайд 60; слова: «розширення CRUD по ваших сутностях, по ваших сценаріях, їх має бути не менше 4»; «це доходить вже до бази даних» |
| R3 | Оптимізовані вибірки колекцій через `JOIN FETCH`: **проблеми N+1 немає** | слайди 60, 62; слова: «будуть зв'язки і ви зробите join fetch запит» |
| R4 | Правила каскадування (`CascadeType`) і `orphanRemoval = true` для дочірніх сутностей | слайд 61. Він сказав, що це «більше пропозиція» і можна пропустити, але слайд його вимагає, тож робимо |
| R5 | Пошук у репозиторії за **конвенцією імен** (derived queries) і **кастомні вибірки через `@Query`** | слайд 61; слова: «запит на основі JPQL теж повинен бути, щоб показати, що ви цим володієте» |
| R6 | Тести **актуальні**: `./mvnw test` без помилок | слайд 62; слова: «тести лишалися, вони мають бути актуальними» |
| R7 | У журналі SQL (`spring.jpa.show-sql=true`) видно **один запит із `LEFT JOIN`** замість серії | слайд 62 |
| R8 | `curl "http://localhost:8080/api/v1/lots"` повертає коректний список **DTO** | слайд 62; слова про перевірку API через `curl` |
| R9 | База — **H2**: викладачеві треба її запустити, Testcontainers ще не проходили | слова: «можете брати h2 … але мені треба його запускати … поки що пропоную працювати з h2» |

Правила моделі з лекції 4, за якими він дивитиметься на код: сутність — **клас**, не record; **DTO ≠ Entity** (ручний мапінг статичними фабриками дозволений слайдом 26); усі зв'язки `LAZY`; двосторонні зв'язки з `mappedBy` і допоміжними `add/remove`; `equals/hashCode` не чіпають колекції; `spring.jpa.open-in-view=false`.

---

## 2. Вихідні умови й відкриті питання

Що змінюється відносно контракту №3:

| Що є в коді | Що робимо | Навіщо |
|-------------|-----------|--------|
| `InMemory…Repository` на `ConcurrentHashMap` (лоти, волонтери, доставки, пункти, статистика донорів) | Видаляємо. Кожен `…Repository` стає інтерфейсом Spring Data (`ListCrudRepository`) без реалізації | R2. Дані живуть у H2 |
| `NoOpTransactionManager` | **Видаляємо.** Реальний менеджер створює JPA | Без цього JPA не працює (розділ 3, таблиця перевірок) |
| `FoodLot`, `VolunteerProfile`, `Delivery`, `DestinationPoint`, `DonorStats`, `LotStatusHistory` (звичайні класи й records) | Стають `@Entity` (розділи 4-5) | R1 |
| `FoodItem` (record) | Клас-сутність, дочірня до `FoodLot` | Record не може бути сутністю |
| `StatusHistoryRecorderImpl` на `CopyOnWriteArrayList` | Пише й читає через `LotStatusHistoryRepository` | Історія — аудит, її місце в базі |
| Поле `UUID lotId` у `Delivery`, `UUID destinationPointId` | Справжні зв'язки `@OneToOne` / `@ManyToOne` | R1 |
| `synchronized` у `VolunteerServiceImpl.reserve` і `recordOutcome` | Прибираємо. Замість нього `@Version` (оптимістичне блокування) і атомарні `UPDATE` для лічильників | Перевірено: `synchronized` на `@Transactional`-методі **не захищає** (розділ 3) |
| Мапінг сутність → DTO (`LotResponse.from(lot)` тощо), де б він не викликався зараз | Переходить **у сервіс**, у транзакцію; сервіс повертає DTO | `open-in-view=false`: поза транзакцією `lot.getItems()` кидає `LazyInitializationException` |
| Стратегії, подія, `LotStatusChanger`, винятки, інтерфейси сервісів | Без змін | Це вже зроблено в №3 |
| Тести Mockito й `@WebMvcTest` | Лишаються й мусять проходити; оновлюємо лише те, що зачепила зміна сигнатур | R6 |

**Відкриті питання — з'ясувати до старту:**

| Питання | Що відомо |
|---------|-----------|
| Дедлайн групового №4 | Ніде не вказаний. Перевірити на distedu. Якщо, як і раніше, «1 тиждень» від лекції — це припущення, не факт. План у розділі 12 розрахований на 4 дні з запасом |
| Що вважається «4 сутностями з CRUD» | Слайд каже «для кожної з 4 сутностей». Ми закриваємо **чотири з повним CRUD** (`FoodLot`, `FoodItem`, `VolunteerProfile`, `DestinationPoint`), а `Delivery` — п'ята: вона виникає в процесі (`pickup` → `deliver` → `confirm`), ручного POST/PUT/DELETE не має, але читається двома GET. Викладач сказав «по ваших сценаріях», тож це узгоджується. Якщо він вимагатиме CRUD і для доставки, додаємо `DELETE` і `PUT` за півгодини — на схему це не впливає |
| Формат здачі | Архів (без `target/`), як і минулого разу |

---

## 3. Що перевірено на робочому прикладі

Все нижче — не припущення: прототип із такою ж структурою модулів (Boot 4.1.1, Java 25, H2, JPA, Modulith 2.1.1) запущено, усі 19 сценаріїв пройшли; порушення правил нижче дають описану поведінку.

| Що | Результат |
|----|-----------|
| `NoOpTransactionManager` лишається в проєкті | `NoSuchBeanDefinitionException: No bean named 'transactionManager' available`: JPA не може працювати. **Видалити обов'язково** |
| 5 лотів по 2 позиції: `findAll()` + `getItems().size()` | **6 запитів** (1 + 5) — це і є N+1 |
| Те саме через `LEFT JOIN FETCH` | **1 запит**. У журналі: `… from food_lots … left join food_items … on … order by …` |
| Читання `lot.getItems()` поза транзакцією | `LazyInitializationException` (перевірено; це й причина правила «мапінг у сервісі») |
| `save` нової сутності з **готовим `UUID`** і **з** `@Version` | 1 запит (`INSERT`) |
| Те саме **без** `@Version` | **2 запити** (`SELECT` + `INSERT`): Spring Data вважає сутність неновою й робить `merge`. Тому кожна сутність із призначеним `id` має `@Version` |
| `cascade = ALL`, `orphanRemoval = true`: збереження лоту, видалення позиції зі списку, видалення лоту | Позиції зберігаються разом із лотом; видалена зі списку зникає з бази; видалення лоту забирає всі позиції |
| `replaceItems(...)`: `items.clear()` + додавання нових | Старі позиції видаляються, нові вставляються |
| `JOIN FETCH` для `@OneToOne`, `@ManyToOne`, `@ElementCollection`, `@ManyToMany` | В кожному випадку **1 запит** |
| Видалення пункту призначення, на який посилається волонтер (M:N) | `DataIntegrityViolationException` на коміті транзакції сервісу. Обробник у `GlobalExceptionHandler` → **409** |
| Видалення волонтера з M:N-зв'язками | Рядки в `volunteer_points` зникають, самі пункти лишаються |
| 20 паралельних викликів `synchronized @Transactional recordCompleted` | Зараховано **6-7 із 20**, решта — `ObjectOptimisticLockingFailureException`. `synchronized` відпускається до коміту |
| Той самий виклик через атомарний `UPDATE … SET x = x + 1` (`@Modifying @Query`) | **20 із 20**, винятків немає |
| Подія (`@ApplicationModuleListener`) після справжнього JPA-коміту | Видавець у `main`, слухач у `task-1`; лічильник волонтера оновився |
| Історія переходів із однаковим `changedAt` | Порядок зберігає `findByLotIdOrderByIdAsc` (id зі `SEQUENCE`); сортування за часом при однакових мітках недетерміноване |
| `ApplicationModules.verify()` із сутностями, що посилаються через модулі | Зелений за умов розділу 4.2 |
| `@DataJpaTest` і `@WebMvcTest` (`@MockitoBean`) | Працюють поряд; статистику Hibernate вмикає атрибут `properties` в `@DataJpaTest` |

---

## 4. Схема даних

### 4.1. Сутності й зв'язки

П'ять пов'язаних сутностей, три різні типи зв'язків плюс колекція значень. Ще дві сутності (`LotStatusHistory`, `DonorStats`) зв'язків не мають — вони лише переїжджають у базу.

```
food_lots (FoodLot)  1 ─────── N  food_items (FoodItem)        cascade ALL + orphanRemoval, LAZY
food_lots (FoodLot)  1 ─────── 1  deliveries (Delivery)        унікальний lot_id, LAZY
destination_points   1 ─────── N  deliveries                    LAZY
destination_points   1 ─────── N  destination_point_categories  @ElementCollection, enum STRING
volunteers           N ─────── M  destination_points            таблиця volunteer_points (улюблені пункти)
```

| Зв'язок | Тип | Власник (FK) | Де оголошено | Каскад |
|---------|-----|--------------|--------------|--------|
| `FoodLot` → `FoodItem` | `@OneToMany` / `@ManyToOne` (двосторонній, `mappedBy`) | `FoodItem.lot` | `common` | `ALL` + `orphanRemoval = true` |
| `Delivery` → `FoodLot` | `@OneToOne` (односторонній) | `Delivery.lot` | `delivery` | немає |
| `Delivery` → `DestinationPoint` | `@ManyToOne` (односторонній) | `Delivery.destinationPoint` | `delivery` | немає |
| `VolunteerProfile` → `DestinationPoint` | `@ManyToMany` (односторонній, `Set`, `@JoinTable`) | `VolunteerProfile.preferredPoints` | `volunteer` | немає |
| `DestinationPoint` → категорії | `@ElementCollection` (`Set<FoodCategory>`) | таблиця `destination_point_categories` | `delivery` | разом із власником |

**«Улюблені пункти» — наше рішення**, у ТЗ зв'язку «волонтер — пункт» немає. Потрібен він для M:N, якого інакше в предметній області нема. Волонтер позначає пункти, куди охоче возить. На бізнес-правила (резервування, доставка) не впливає.

Посилання **між модулями** лишаються звичайними `UUID`-полями, **не** зв'язками:

| Поле | Куди веде |
|------|-----------|
| `FoodLot.donorOrgId` | організація-донор (сутності немає) |
| `FoodLot.reservedByVolunteerId` | `volunteer` |
| `Delivery.volunteerId` | `volunteer` |
| `LotStatusHistory.lotId`, `DonorStats.donorOrgId` | лот, донор |

### 4.2. Обмеження Spring Modulith для сутностей (перевірено)

Зв'язки між сутностями — це залежності між пакетами, тож `verify()` їх бачить:

| Правило | Що станеться при порушенні |
|---------|----------------------------|
| Зв'язок сутності йде **лише в `common`** або в межах власного модуля (`delivery` → `common`, `volunteer` → `delivery`, `lot` → `common`) | Цикл модулів: `Cycle detected: Slice … ->` |
| `delivery` **не посилається** на `volunteer` і `lot`; тому `Delivery.volunteerId` — `UUID`, а не `@ManyToOne` | те саме |
| `volunteer` посилається на `delivery.DestinationPoint` — дозволено (напрям `volunteer → delivery` уже є через подію) | — |
| У `common` **немає підпакетів**; типи, які бачать інші модулі, лежать у **кореневому пакеті** модуля (сутність, репозиторій, виняток) | `depends on non-exposed type … within module …` |
| Не додавати `spring-modulith-starter-jpa` | Він зберігає журнал публікацій подій у базі; лекція цього не вимагає. Initializr може додати його сам при виборі JPA — прибрати |

Дозволені залежності після цього завдання:

```
lot        ──►  common
volunteer  ──►  common
volunteer  ──►  delivery   (сутність DestinationPoint, її репозиторій, виняток, подія)
delivery   ──►  common
lot        ──►  delivery   (лише подія)
```

---

## 5. База (міграція спільної частини) — робить Людина 1 до старту

Це єдиний крок, який мусить відбутися **першим**: він змінює `common`, який використовують усі. Щоб ніхто нікого не чекав, **Людина 1 готує всю базу заздалегідь** і зливає її в `main` одним злиттям. Орієнтир — 2 години. Після злиття `common` **не міняємо**: якщо чогось бракує — пишемо в чат.

Поки Людина 1 зайнята базою, Людини 2 і 3 читають лекцію й розділи 4, 6 і 7 і можуть писати **нові** файли у своєму пакеті (DTO, винятки, тестові класи), але **не чіпають наявних**. Після злиття бази всі троє працюють паралельно й нікого не чекають.

| Крок | Що зробити |
|------|-----------|
| 1 | `pom.xml`: залежності з 5.1 |
| 2 | `application.properties`: блок з 5.2 |
| 3 | Видалити `NoOpTransactionManager`. У `InfrastructureConfig` лишити `Clock`, `@EnableAsync` і `@EnableTransactionManagement` як є |
| 4 | Сутності `common`: `FoodLot`, `FoodItem` (5.3), `LotStatusHistory` (5.4). Поля `FoodLot` — ті, що в контракті №2 §6, плюс `version`. `FoodItem` — `name`, `quantity`, `unit`, `bestBefore`, `lot`. Записи `FoodItem` в усьому коді замінити на клас (`item.name()` → `item.getName()`) |
| 5 | Репозиторії `common` (5.5): `LotRepository`, `FoodItemRepository`, `LotStatusHistoryRepository`. Видалити `InMemoryLotRepository` |
| 6 | `StatusHistoryRecorderImpl` пише/читає через `LotStatusHistoryRepository`, час — з `Clock`; прибрати `CopyOnWriteArrayList`. Оновити його тест: мок репозиторію замість списку |
| 7 | `DestinationPoint` (5.6) і `DestinationPointRepository` (5.7), `DestinationPointNotFoundException` — у **кореневому пакеті `delivery`**. Видалити `InMemoryDestinationPointRepository` |
| 8 | `GlobalExceptionHandler`: два нових обробники (5.8) |
| 9 | `DemoData` зберігає демо-лоти через `LotRepository`; фіксовані id працюють, бо сутності мають `@Version` |
| 10 | Прибрати `FoodRescueApplicationTests`, якщо він є; додати в `README.md` розділ «База даних» (скелет заголовків — розділ 9) |
| 11 | Перевірити: проєкт збирається, `ModulesTest` зелений, наявні тести зелені |

**Тимчасово в базі:** у `application.properties` стоїть `spring.jpa.open-in-view=true`. Наявні сервіси ще віддають сутності, а мапінг у DTO робить контролер; з `false` ці виклики впали б із `LazyInitializationException` ще до того, як кожен переробить свій код. Кожен у своєму треку переносить мапінг у сервіс і перевіряє себе запуском із `--spring.jpa.open-in-view=false`. **В останній день значення міняється на `false`** (розділ 12) — це вимога викладача.

Власні сутності й сховища `VolunteerProfile`/`VolunteerRepository`, `Delivery`/`DeliveryRepository`, `DonorStats`/`DonorStatsRepository` у базу **не входять** — їх кожен переробляє у своєму треку. До того вони живуть як були (`InMemory…`), тож проєкт збирається.

**Готово, коли:** `./mvnw test` зелений; застосунок стартує; в `http://localhost:8080/h2-console` видно таблиці `FOOD_LOTS`, `FOOD_ITEMS`, `LOT_STATUS_HISTORY`, `DESTINATION_POINTS`, `DESTINATION_POINT_CATEGORIES`; `GET /api/v1/lots` повертає демо-лоти.

### 5.1. `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-h2console</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
```

`spring-boot-h2console` — окремий модуль у Boot 4: без нього `spring.h2.console.enabled=true` нічого не вмикає. Залежності Modulith (`starter-core`, `events-api`, `starter-test`) уже є з №3.

### 5.2. `application.properties` (додати до наявного)

```properties
spring.datasource.url=jdbc:h2:mem:foodrescue;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=true
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

`open-in-view=true` — **тимчасово** (див. вище); у здачі має бути `false` (слайд 38). `show-sql=true` потрібен для перевірки R7. Дані в пам'яті: після перезапуску база порожня, `DemoData` створює лоти заново.

### 5.3. `FoodLot` і `FoodItem` — головна пара (1:N, cascade, orphanRemoval)

Показано лише те, що відрізняє сутність від звичайного класу; решта полів (контракт №2 §6) і геттери/сеттери — як були.

```java
@Entity
@Table(name = "food_lots")
public class FoodLot {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalWeightKg;

    @OneToMany(mappedBy = "lot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodItem> items = new ArrayList<>();

    protected FoodLot() {
    }

    public void addItem(FoodItem item) {
        items.add(item);
        item.setLot(this);
    }

    public void removeItem(FoodItem item) {
        items.remove(item);
        item.setLot(null);
    }

    public void replaceItems(List<FoodItem> newItems) {
        items.clear();
        newItems.forEach(this::addItem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FoodLot other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
```

```java
@Entity
@Table(name = "food_items")
public class FoodItem {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private FoodLot lot;

    protected FoodItem() {
    }

    public FoodItem(String name, BigDecimal quantity, ItemUnit unit, LocalDate bestBefore) {
        this.id = UUID.randomUUID();
        ...
    }

    void setLot(FoodLot lot) {
        this.lot = lot;
    }
}
```

Поля `unit` (`@Enumerated(STRING)`) і `bestBefore` (`LocalDate`) — з контракту №2. `setLot` без модифікатора доступу (пакетний): викликати його може лише `FoodLot`, тож зв'язок не розсинхронізується. `equals/hashCode` у `FoodItem` — за `id`, як і в `FoodLot`; колекції в них **не** беруть участі.

### 5.4. `LotStatusHistory` — без зв'язків, `id` зі `SEQUENCE`

```java
@Entity
@Table(name = "lot_status_history")
public class LotStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private UUID lotId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LotStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotStatus toStatus;

    @Column(nullable = false)
    private Instant changedAt;

    @Column(length = 300)
    private String comment;
    ...
}
```

Запис лише додається, тож `@Version` не потрібен: `id` генерує база.

### 5.5. Репозиторії `common`

```java
public interface LotRepository extends ListCrudRepository<FoodLot, UUID> {

    long countByDonorOrgId(UUID donorOrgId);

    long countByDonorOrgIdAndStatus(UUID donorOrgId, LotStatus status);

    boolean existsByReservedByVolunteerIdAndStatusIn(UUID volunteerId, Collection<LotStatus> statuses);

    @Query("SELECT DISTINCT l FROM FoodLot l LEFT JOIN FETCH l.items ORDER BY l.createdAt DESC")
    List<FoodLot> findAllWithItems();

    @Query("SELECT DISTINCT l FROM FoodLot l LEFT JOIN FETCH l.items WHERE l.status = :status ORDER BY l.createdAt DESC")
    List<FoodLot> findAllByStatusWithItems(@Param("status") LotStatus status);

    @Query("SELECT DISTINCT l FROM FoodLot l LEFT JOIN FETCH l.items WHERE l.id = :id")
    Optional<FoodLot> findByIdWithItems(@Param("id") UUID id);
}

public interface FoodItemRepository extends ListCrudRepository<FoodItem, UUID> {

    List<FoodItem> findByLotId(UUID lotId);

    Optional<FoodItem> findByIdAndLotId(UUID id, UUID lotId);
}

public interface LotStatusHistoryRepository extends ListCrudRepository<LotStatusHistory, Long> {

    List<LotStatusHistory> findByLotIdOrderByIdAsc(UUID lotId);
}
```

Що з цього хто використовує: `countByDonorOrgId*` — поріг скасувань (Людина 1); `existsByReservedByVolunteerIdAndStatusIn` — видалення волонтера (Людина 2); `find…WithItems` — усі, хто повертає лот у відповіді. Скрізь, де відповідь містить `items`, лот читається **через `…WithItems`**, а не через `findById`/`findAll`.

### 5.6. `DestinationPoint` — `@ElementCollection`, у кореневому пакеті `delivery`

```java
@Entity
@Table(name = "destination_points")
public class DestinationPoint {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 11)
    private String workingHours;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "destination_point_categories", joinColumns = @JoinColumn(name = "point_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private Set<FoodCategory> acceptedCategories = new HashSet<>();
    ...
}
```

Поля — ті, що в `DestinationPointRequest` (контракт №2 §10). `workingHours` має вигляд `09:00-18:00` — це 11 символів.

### 5.7. `DestinationPointRepository`

```java
public interface DestinationPointRepository extends ListCrudRepository<DestinationPoint, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    @Query("SELECT DISTINCT p FROM DestinationPoint p LEFT JOIN FETCH p.acceptedCategories ORDER BY p.name")
    List<DestinationPoint> findAllWithCategories();

    @Query("SELECT DISTINCT p FROM DestinationPoint p LEFT JOIN FETCH p.acceptedCategories WHERE p.id = :id")
    Optional<DestinationPoint> findByIdWithCategories(@Param("id") UUID id);
}
```

### 5.8. Обробники в `GlobalExceptionHandler`

Обидва повертають `ProblemDetail` у форматі №2 §8: `type` = `…/errors/conflict`, `title` = `Конфлікт стану`, статус **409**.

| Виняток | `detail` | Коли виникає |
|---------|----------|--------------|
| `DataIntegrityViolationException` | `Запис пов'язаний з іншими даними` | видалення запису, на який посилаються (пункт призначення з волонтерами) |
| `ObjectOptimisticLockingFailureException` | `Запис змінено паралельно, повторіть запит` | двоє одночасно резервують той самий лот |

`instance` Spring підставляє сам. Обробники — у тому самому класі з `@Order(Ordered.HIGHEST_PRECEDENCE)`.

---

## 6. Правила для сутностей і сервісів — для всіх трьох однаково

**Сутність**
- Клас із `protected` конструктором без параметрів, **не** record і не `final`.
- `@Id UUID`, **призначений у коді** (фіксовані id для демо-даних). Кожна така сутність має `@Version Long version`, інакше кожен `save` — це `SELECT` + `INSERT` (розділ 3). `IDENTITY` і `AUTO` не використовуємо.
- Усі зв'язки `fetch = LAZY`. `EAGER` не використовуємо ніде.
- Для enum — `@Enumerated(EnumType.STRING)`, ніколи `ORDINAL`.
- `BigDecimal` вага — `precision`/`scale` в `@Column`.
- `equals/hashCode` — лише за `id`. Колекції в них не входять, `toString` колекцій не друкує.
- Колекції: `List` для впорядкованих дочірніх (`items`), `Set` для M:N і `@ElementCollection`. Двосторонній зв'язок міняємо **лише допоміжними методами** (`addItem`/`removeItem`), не прямо через геттер.
- Геттер колекції повертає саму колекцію (не `List.copyOf`): інакше Hibernate не побачить змін.

**Сервіс**
- Метод, що міняє дані, — `@Transactional`; метод, що лише читає, — `@Transactional(readOnly = true)`. Публікація події (`DeliveryService.confirm`) лишається `@Transactional`, подія — останньою дією.
- **Сутність не залишає сервіс.** Сервіс повертає DTO, змаплений усередині транзакції (`LotResponse.from(lot)` тощо). Інакше `open-in-view=false` дає `LazyInitializationException`.
- Читання, що повертає колекції, — лише запитом із `JOIN FETCH`. Цикл `for (lot : findAll()) lot.getItems()` — це і є N+1.
- Зміна статусу, як у №3: спершу перевірки й `statusChanger.transition(...)`, лише потім свої поля. Після змін на керованій сутності `save` у межах транзакції можна не викликати, але **викликаємо**, щоб код читався однаково з №3.
- `synchronized` не використовуємо ніде. Конкуренцію забезпечують `@Version` і, для лічильників, атомарні `UPDATE` (розділ 7, Людина 2).

**DTO**
- DTO — лише records (№2). Мапінг сутність → DTO — статичні фабрики `from(...)` усередині сервісу. MapStruct не додаємо: слайд 26 дозволяє ручний мапінг, а нова залежність нічого не дає.
- Відповіді змінюються лише там, де зазначено в розділі 7 (`id` у `FoodItemResponse`, `preferredPoints` у `VolunteerResponse`). URL і решта DTO — як у №2.

**Запити**
- Derived query — коли достатньо назви методу. `@Query` (JPQL) — коли потрібен `JOIN FETCH`, сортування чи умова, яку назва не виражає. Нативних запитів немає.
- Параметри — лише `@Param`, ніколи конкатенація рядків.

---

## 7. Розподіл — рівно порівну

Кожен веде **свій модуль наскрізь**: сутність, репозиторій, DTO, сервіс, контролер, винятки, тести. Поділу «ти пишеш DTO, ти — тести» немає: викладач сказав, що на захисті кожен відповідає за все.

| | Людина 1 | Людина 2 | Людина 3 |
|---|---|---|---|
| Модуль | `lot` | `volunteer` | `delivery` |
| Сутності з CRUD | `FoodLot`, `FoodItem` | `VolunteerProfile` | `DestinationPoint` (+ `Delivery` за сценарієм) |
| Зв'язок, що показує | 1:N, `cascade`, `orphanRemoval` | M:N | 1:1, N:1, `@ElementCollection` |
| Нових ендпоінтів | 6 | 6 | 5 |
| Сутностей переводить сама | `DonorStats` | `VolunteerProfile` | `Delivery` |
| Запитів у репозиторії свого модуля | у базі (5.5); тут лише `DonorStats` | 1 derived, 2 `@Query` з `JOIN FETCH`, 3 атомарні `UPDATE` | 1 derived, 3 `@Query` з `JOIN FETCH` |
| Тести | сервіси, `LotRepositoryTest`, контролери | сервіс, `VolunteerRepositoryTest`, контролер | сервіси, `DeliveryRepositoryTest`, контролери |
| Крім кейсу | **база** (розділ 5) — до старту | — | наскрізний сценарій (розділ 10, п. 5) і перевірка N+1 у журналі (розділ 10, п. 2) |

Людина 1 бере базу **додатково, до старту**: це її внесок, який знімає чекання з інших. Її власний кейс по обсягу легший, ніж у Людини 2 (нових ендпоінтів порівну, але немає ні M:N, ні рефакторингу лічильників). У Людини 3 найбільше змін у наявному коді: `Delivery` із `UUID lotId` стає зв'язком, `pickup`/`deliver`/`confirm` переходять на JPA й подію. Якщо хтось хоче обмінятися дрібницею — можна вільно.

Для всіх трьох: інтерфейси сервісів і контролери **лишають свої імена**; свої `InMemory…Repository` після переходу видаляємо; для кожного нового контролера — тести за шаблоном №2 §11 (валідний запит, невалідне тіло, бізнес-виняток, невідоме поле).

### Людина 1 — модуль `lot`: лот, позиції, статистика донора

**Сутності й зв'язок:** `FoodLot` ↔ `FoodItem` (створює база) — тут це `cascade = ALL` + `orphanRemoval`. `DonorStats` (**твоя**): `@Id UUID donorOrgId`, `@Version`, `confirmedLots`, `disputedLots`; `DonorStatsRepository` — інтерфейс Spring Data (`findById`, `save`), `InMemoryDonorStatsRepository` видалити. `DonorStatsService.recordOutcome` збільшує лічильник тим самим способом, що й у Людини 2: атомарний `@Modifying @Query("UPDATE DonorStats s SET s.confirmedLots = s.confirmedLots + 1 WHERE s.donorOrgId = :id")` (і `disputedLots`). Якщо оновлено 0 рядків (запису ще немає) — створити `DonorStats` і повторити оновлення. Слухач події не змінюється.

**Ендпоінти лоту** (нові позначено *нове*; решта — як у №2, лише переходять на JPA й на мапінг у сервісі):

| Метод | URL | Тіло | Успіх | Помилки |
|-------|-----|------|-------|---------|
| POST | `/api/v1/lots` | `LotRequest` | 201 | 400 |
| GET | `/api/v1/lots?status=` | — | 200 `List<LotResponse>` | 400 (невідомий статус) |
| GET | `/api/v1/lots/{id}` | — | 200 `LotResponse` | 404 |
| PUT | `/api/v1/lots/{id}` | `LotRequest` | 200 `LotResponse` | 400, 404, 409 не `DRAFT` |
| DELETE | `/api/v1/lots/{id}` *(нове)* | — | 204 | 404, 409 не `DRAFT` |
| POST | `…/publication`, `…/cancellation`, `…/approval` | як у №2 | 200 | як у №2 |

- `?status=` *(нове)*: без параметра — `findAllWithItems()`, з ним — `findAllByStatusWithItems(status)`. Обидва — **один** запит.
- `PUT` замінює позиції через `lot.replaceItems(...)`: старі зникають завдяки `orphanRemoval`.
- `DELETE`: дозволений **лише в `DRAFT`** (лот, з яким уже щось відбулося, лишається в історії); інакше `LotNotDraftException` → 409 (уже є). Позиції видаляються каскадом.
- Поріг скасувань донора (правило 1.3 з №3) рахується через `countByDonorOrgId` і `countByDonorOrgIdAndStatus(…, CANCELLED)` замість перебору всіх лотів.

**Ендпоінти позицій** *(усе нове; підресурс лоту)* — контролер `LotItemController`, сервіс `LotItemService` (інтерфейс + `…Impl`):

| Метод | URL | Тіло | Успіх | Помилки |
|-------|-----|------|-------|---------|
| POST | `/api/v1/lots/{lotId}/items` | `FoodItemRequest` | 201 + `Location` | 400, 404, 409 не `DRAFT` |
| GET | `/api/v1/lots/{lotId}/items` | — | 200 `List<FoodItemResponse>` | 404 |
| GET | `/api/v1/lots/{lotId}/items/{itemId}` | — | 200 `FoodItemResponse` | 404 |
| PUT | `/api/v1/lots/{lotId}/items/{itemId}` | `FoodItemRequest` | 200 `FoodItemResponse` | 400, 404, 409 не `DRAFT` |
| DELETE | `/api/v1/lots/{lotId}/items/{itemId}` | — | 204 | 404, 409 не `DRAFT` |

- Зміна позицій — лише в `DRAFT` (той самий принцип, що правило 1.2): `LotNotDraftException` → 409.
- Додавання — `lot.addItem(item)` (позиція зберігається каскадом, окремого `save` немає); видалення — `lot.removeItem(item)` (**`orphanRemoval`**, окремого `delete` немає).
- Читання — `FoodItemRepository.findByLotId(lotId)` і `findByIdAndLotId(itemId, lotId)`; позиція з чужого лоту → `FoodItemNotFoundException` → 404 (**нове**, у `lot`, успадковує `NotFoundException`). Неіснуючий лот → `LotNotFoundException` → 404.
- `FoodItemResponse` отримує поле `id` (потрібне для `PUT`/`DELETE`).

### Людина 2 — модуль `volunteer`: волонтери й улюблені пункти

**Сутність:** `VolunteerProfile` (**твоя**, у `volunteer`): `@Id UUID`, `@Version`, `fullName`, `email` (`unique = true`), `phone`, `transportType` (`@Enumerated STRING`), `activityZone`, `completedDeliveries`, `latePickups`, `noShows`, і зв'язок:

```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
        name = "volunteer_points",
        joinColumns = @JoinColumn(name = "volunteer_id"),
        inverseJoinColumns = @JoinColumn(name = "point_id"))
private Set<DestinationPoint> preferredPoints = new HashSet<>();
```

Зв'язок односторонній (зворотного `mappedBy` немає): двосторонній M:N між модулями дав би цикл. `VolunteerRepository` — інтерфейс Spring Data; `InMemoryVolunteerRepository` видалити. `VolunteerDemoData` зберігає демо-волонтера з id `DEMO_VOLUNTEER` через репозиторій.

```java
public interface VolunteerRepository extends ListCrudRepository<VolunteerProfile, UUID> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    @Query("SELECT DISTINCT v FROM VolunteerProfile v LEFT JOIN FETCH v.preferredPoints ORDER BY v.fullName")
    List<VolunteerProfile> findAllWithPoints();

    @Query("SELECT DISTINCT v FROM VolunteerProfile v LEFT JOIN FETCH v.preferredPoints WHERE v.id = :id")
    Optional<VolunteerProfile> findByIdWithPoints(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE VolunteerProfile v SET v.completedDeliveries = v.completedDeliveries + 1 WHERE v.id = :id")
    int incrementCompleted(@Param("id") UUID id);
}
```

Аналогічні `incrementNoShows` і `incrementLatePickups` — за тим самим шаблоном.

**Ендпоінти:**

| Метод | URL | Тіло | Успіх | Помилки |
|-------|-----|------|-------|---------|
| POST | `/api/v1/volunteers` | `VolunteerRequest` | 201 | 400, 409 дубль пошти |
| GET | `/api/v1/volunteers` *(нове)* | — | 200 `List<VolunteerResponse>` | — |
| GET | `/api/v1/volunteers/{id}` | — | 200 `VolunteerResponse` | 404 |
| PUT | `/api/v1/volunteers/{id}` *(нове)* | `VolunteerRequest` | 200 `VolunteerResponse` | 400, 404, 409 дубль пошти |
| DELETE | `/api/v1/volunteers/{id}` *(нове)* | — | 204 | 404, 409 є активні лоти |
| GET | `/api/v1/volunteers/{id}/preferred-points` *(нове)* | — | 200 `List<PreferredPointResponse>` | 404 |
| PUT | `/api/v1/volunteers/{id}/preferred-points/{pointId}` *(нове)* | — | 204 | 404 |
| DELETE | `/api/v1/volunteers/{id}/preferred-points/{pointId}` *(нове)* | — | 204 | 404 |
| POST, DELETE | `/api/v1/lots/{id}/reservation` | як у №2 | 201, 204 | як у №3 |

- `PreferredPointResponse(UUID id, String name)` — у `volunteer`. Це нове поле в `VolunteerResponse`: `List<PreferredPointResponse> preferredPoints`.
- `PUT` (правило 2.1 з №3): пошта унікальна серед **інших** волонтерів — `existsByEmailAndIdNot` → `DuplicateVolunteerException` → 409.
- `DELETE` (нове правило 2.5): якщо на волонтері є лот у `RESERVED` або `PICKED_UP` (`lotRepository.existsByReservedByVolunteerIdAndStatusIn(id, List.of(RESERVED, PICKED_UP))`) → `VolunteerHasActiveLotsException` → 409 (**нове**, у `volunteer`, успадковує `ConflictException`). Рядки `volunteer_points` видаляються разом із волонтером, самі пункти лишаються.
- `PUT …/preferred-points/{pointId}` додає пункт (повторний виклик нічого не міняє, `Set`); пункт шукається через `DestinationPointRepository.findById`, немає → `DestinationPointNotFoundException` (створена в базі, у `delivery`) → 404. Волонтера — `findByIdWithPoints`.
- `GET /volunteers` і `GET /volunteers/{id}` читають **через `JOIN FETCH`**: `preferredPoints` потрапляє у відповідь без додаткових запитів.

**Конкуренція (замість `synchronized`):**
- `reserve`: лот — сутність із `@Version`. Двоє одночасно резервують один лот → другий отримує `ObjectOptimisticLockingFailureException` → 409 (обробник у базі). Тест `reserve` перевіряє лише бізнес-логіку; конкуренцію — `@Version` у сутності.
- `recordOutcome` (слухач події, окремий потік): лічильники збільшуються **атомарними `UPDATE`** (`incrementCompleted` / `incrementNoShows` / `incrementLatePickups`), а не «прочитати, додати, зберегти». Тести `recordOutcome` перевіряють, які методи репозиторію викликано, і `verifyNoMoreInteractions`.

### Людина 3 — модуль `delivery`: пункти призначення й доставки

**Сутності** (обидві в кореневому пакеті `delivery`): `DestinationPoint` — створена базою (розділ 5.6), ти пишеш її сервіс, контролер і DTO. `Delivery` — **твоя**:

```java
@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    private UUID id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, unique = true)
    private FoodLot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_point_id")
    private DestinationPoint destinationPoint;

    private UUID volunteerId;
    ...
}
```

Решта полів (`pickedUpAt`, `pickupWeightKg`, `deliveredAt`, `confirmationCode`, `confirmedAt`, `receivedWeightKg`, `latePickup`) — як були. `Delivery.destinationPoint` порожнє до кроку `deliver`. `volunteerId` — `UUID`, **не** зв'язок (`delivery` не посилається на `volunteer`).

```java
public interface DeliveryRepository extends ListCrudRepository<Delivery, UUID> {

    boolean existsByDestinationPointId(UUID destinationPointId);

    @Query("SELECT d FROM Delivery d JOIN FETCH d.lot WHERE d.lot.id = :lotId")
    Optional<Delivery> findByLotIdWithLot(@Param("lotId") UUID lotId);

    @Query("SELECT d FROM Delivery d JOIN FETCH d.lot LEFT JOIN FETCH d.destinationPoint ORDER BY d.id")
    List<Delivery> findAllWithLotAndPoint();

    @Query("SELECT d FROM Delivery d JOIN FETCH d.lot LEFT JOIN FETCH d.destinationPoint WHERE d.id = :id")
    Optional<Delivery> findByIdWithLotAndPoint(@Param("id") UUID id);
}
```

`InMemoryDeliveryRepository` видалити. `pickup` створює `Delivery` (1:1 із лотом), `deliver` виставляє пункт, `confirm` — решту й публікує подію (№3, розділ 5).

**Ендпоінти:**

| Метод | URL | Тіло | Успіх | Помилки |
|-------|-----|------|-------|---------|
| POST | `/api/v1/destination-points` | `DestinationPointRequest` | 201 | 400, 409 дубль назви |
| GET | `/api/v1/destination-points` *(нове)* | — | 200 `List<DestinationPointResponse>` | — |
| GET | `/api/v1/destination-points/{id}` | — | 200 `DestinationPointResponse` | 404 |
| PUT | `/api/v1/destination-points/{id}` *(нове)* | `DestinationPointRequest` | 200 `DestinationPointResponse` | 400, 404, 409 дубль назви |
| DELETE | `/api/v1/destination-points/{id}` *(нове)* | — | 204 | 404, 409 пункт використовується |
| GET | `/api/v1/deliveries` *(нове)* | — | 200 `List<DeliveryResponse>` | — |
| GET | `/api/v1/deliveries/{id}` *(нове)* | — | 200 `DeliveryResponse` | 404 |
| POST | `/api/v1/lots/{id}/pickup`, `/delivery`, `/confirmation`; GET `/history` | як у №2 | 200 | як у №3 |

- `GET /destination-points` і `GET /destination-points/{id}` — через `findAllWithCategories` / `findByIdWithCategories` (**один** запит, `acceptedCategories` вже завантажені).
- `PUT` (правило 3.4 з №3): назва унікальна серед **інших** пунктів — `existsByNameAndIdNot` → `DuplicateDestinationPointException` → 409. Категорії замінюються цілком (`clear()` + `addAll`, `@ElementCollection` сам чистить рядки).
- `DELETE` (нове правило 3.5): є хоч одна доставка з цим пунктом (`existsByDestinationPointId`) → `DestinationPointInUseException` → 409 (**нове**, у `delivery`, успадковує `ConflictException`). Якщо на пункт посилаються **волонтери** (M:N), база не дасть його видалити: `DataIntegrityViolationException` перехоплює обробник із бази → теж 409. Перевіряти таблицю волонтерів у своєму коді не треба й не можна (цикл модулів).
- `GET /deliveries`, `GET /deliveries/{id}` — через `findAllWithLotAndPoint` / `findByIdWithLotAndPoint`: `lotStatus` і `destinationPointId` у відповіді беруться без додаткових запитів. Неіснуюча доставка → `DeliveryNotFoundException` → 404 (уже є).
- `DeliveryResponse` не змінюється.

---

## 8. Тести (R6)

**Що лишається:** усі тести з №3 — Mockito-тести сервісів (`@ExtendWith(MockitoExtension.class)`, без контексту, `verifyNoMoreInteractions`) і `@WebMvcTest` контролерів. Вони мусять бути зеленими; оновлюємо тільки те, що зламала зміна (наприклад, сервіс тепер повертає DTO, слухач викликає атомарний `UPDATE`).

**Що додає кожен** (на свій модуль):

| Що | Як |
|----|----|
| Тест репозиторію | `@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")` — це зрізовий тест: лише JPA й вбудована H2, без контролерів. **Не** `@SpringBootTest` (аудит з №3 лишається чинним) |
| Кожен derived-метод і кожен `@Query` | Мінімум один сценарій: зберегти дані, викликати, перевірити результат |
| Відсутність N+1 | Зберегти 4-5 батьківських записів із дітьми, `entityManager.flush()` і `entityManager.clear()` (інакше SQL не буде — спрацює кеш сесії), скинути статистику, викликати `find…With…` і перевірити, що `getPrepareStatementCount() == 1` |
| Зв'язок свого модуля | Людина 1: каскадне збереження, видалення позиції зі списку (`orphanRemoval`), каскадне видалення лоту. Людина 2: M:N — додати й прибрати пункт, рядок у `volunteer_points` з'являється й зникає. Людина 3: `@OneToOne` + `@ManyToOne` — `findByLotIdWithLot` одним запитом; `@ElementCollection` — категорії одним запитом |
| Нові контролери й ендпоінти | `@WebMvcTest` за шаблоном №2 §11 (4 сценарії). Сервіс — `@MockitoBean` |
| Нові правила сервісів | Mockito: порушення → `assertThrows` + `verify(repository, never()).save(any())`; `verifyNoMoreInteractions` |

Приклад перевірки N+1 у тесті репозиторію:

```java
entityManager.flush();
entityManager.clear();
Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
stats.clear();

lotRepository.findAllWithItems().forEach(lot -> lot.getItems().size());

assertEquals(1, stats.getPrepareStatementCount());
```

Контрольне порівняння (щоб побачити N+1 на власні очі) — той самий блок із `lotRepository.findAll()`: на 4 лотах дає 5 запитів. Цей варіант у проєкті **не лишаємо** — лише для розуміння й захисту.

---

## 9. README.md — що додаємо

Розділ **«База даних»** (скелет робить база, кожен пише свою частину):

1. Схема: діаграма з розділу 4.1 і таблиця зв'язків (тип, власник, каскад)
2. Як дивитися базу: `http://localhost:8080/h2-console`, JDBC URL із `application.properties`, логін `sa`, порожній пароль
3. Що таке N+1 і де ми його прибрали: кожен додає рядок «ендпоінт → запит із `JOIN FETCH` → 1 запит»
4. Каскад і `orphanRemoval`: що видаляється разом із лотом і з позицією (Людина 1)
5. M:N «улюблені пункти» і чому зв'язок односторонній (Людина 2)
6. Чому `Delivery.volunteerId` — `UUID`, а не зв'язок (Людина 3): межі модулів
7. Конкуренція: `@Version` і атомарні `UPDATE` замість `synchronized`

---

## 10. Готово, коли (як перевірятиме викладач)

Кожен перевіряє це на **розпакованому архіві** в порожній папці, а не в IDE.

**1. Тести (R6)**
```
./mvnw test
```
Очікуємо `BUILD SUCCESS`; серед тестів — `ModulesTest` (`verify()`) і `@DataJpaTest` кожного модуля.

**2. N+1 у журналі SQL (R3, R7)**

Запустити `./mvnw spring-boot:run`, очистити консоль (після стартових `insert`), викликати:

| Запит | У журналі має бути |
|-------|--------------------|
| `curl "http://localhost:8080/api/v1/lots"` | **один** `select distinct … from food_lots … left join food_items …` |
| `curl "http://localhost:8080/api/v1/volunteers"` | один `select distinct … from volunteers … left join volunteer_points … left join destination_points …` |
| `curl "http://localhost:8080/api/v1/destination-points"` | один `select distinct … left join destination_point_categories …` |
| `curl "http://localhost:8080/api/v1/deliveries"` | один `select … from deliveries … join food_lots … left join destination_points …` |

**Жодного** додаткового `select … from food_items where lot_id=?` (і подібних) після них. Якщо є — це N+1, шукаємо, де список читається не через `JOIN FETCH`.

**3. Список DTO (R8)**
```
curl "http://localhost:8080/api/v1/lots"
```
Повертає JSON-масив `LotResponse` (з `items`), а не помилку й не сутності.

**4. Аудит** — усі команди мають повернути **порожньо**:
```
grep -rn "@Autowired" src/main
grep -rnE "InMemory|ConcurrentHashMap|CopyOnWriteArrayList|NoOpTransactionManager" src/main
grep -rn "synchronized" src/main
grep -rn "FetchType.EAGER" src/main
grep -rnE "GenerationType.(IDENTITY|AUTO)" src/main
grep -rn "@SpringBootTest" src/test
grep -rn "spring-modulith-starter-jpa" pom.xml
```
і ця має повернути **рівно один** збіг: `grep -n "open-in-view=false" src/main/resources/application.properties`.

**5. Наскрізний сценарій через `curl`** — сценарій з №3, розділ 9, плюс:

| Крок | Очікуємо |
|------|----------|
| `GET /lots?status=PUBLISHED` | 200, лише опубліковані |
| `POST /lots/{id}/items`, потім `GET /lots/{id}` | позиція є у відповіді; `DELETE /lots/{id}/items/{itemId}` — 204, позиції немає |
| `PUT /volunteers/{id}/preferred-points/{pointId}`, `GET /volunteers/{id}` | пункт у `preferredPoints` |
| `DELETE /destination-points/{pointId}` для пункту, який хтось обрав | **409** |
| `DELETE /lots/{id}` для лоту не в `DRAFT` | **409**; для `DRAFT` — 204, позиції зникли |
| `confirm`, а в журналі | два рядки слухачів у потоках `task-N` (№3) |

---

## 11. Чого не робимо

Викладач цього не вимагає, тож не робимо:

- PostgreSQL, Testcontainers, Flyway/Liquibase — лише H2 (він сам це радив);
- MapStruct, Lombok, `@EntityGraph`, `Specification`, `@Query(nativeQuery = true)`;
- Spring Security, Swagger, кеш, планувальник (БК-4), Spring Batch (БК-5) — теми наступних лекцій;
- сутностей, яких немає в цьому контракті (`User`, `Role`, `Organization`, `Address`, `Notification`, `MonthlyReport`);
- нових бізнес-правил, окрім `DELETE`-обмежень (1, 2.5, 3.5) і унікальності назви/пошти при `PUT`;
- `@SpringBootTest` у тестах;
- справжнього логування — лише `show-sql` і `System.out.println` слухачів, як у №3.

---

## 12. Порядок тижня

Дедлайн невідомий (розділ 2), тому план — 4 дні з запасом; якщо часу більше, просто розтягується.

| Коли | Хто | Що |
|------|-----|----|
| День 0 | разом | Домовитися, хто Людина 1, 2 і 3. Прочитати розділи 4-7. Переконатися, що №3 злитий у `main` і `./mvnw test` зелений. З'ясувати дедлайн на distedu |
| День 0 — 1 | Людина 1 | База, розділ 5, одним злиттям у `main`. Людини 2 і 3 не чекають: читають, пишуть нові файли у своєму пакеті (DTO, винятки, класи тестів) |
| День 1 | кожен окремо | Нова гілка від `main` (`jpa-lot`, `jpa-volunteer`, `jpa-delivery`; старі не чіпаємо). Порядок у своєму треку: (1) сутність і репозиторій → (2) `@DataJpaTest` на кожен метод і на N+1 → (3) сервіс на JPA з DTO у транзакції → (4) контролер і нові ендпоінти → (5) оновити старі й додати нові тести. **Зливаємо в `main`, коли `./mvnw test` зелений** — чим частіше, тим краще |
| День 2 | кожен окремо | Свої правила `DELETE`/`PUT`, README-частина, прогін розділу 10, п. 2 на своїх ендпоінтах з `--spring.jpa.open-in-view=false`. Людина 3 збирає наскрізний сценарій |
| День 3 | разом | Усе в `main`. **Першим кроком `open-in-view=true` → `false`** і повний `./mvnw test`; далі пройти розділ 10 повністю на розпакованому архіві; виправити розбіжності |
| День 4 | разом | Запас. Архів на distedu. Кожен готовий пояснити **будь-яку** частину, не лише свою: чому `LAZY`, що таке N+1 і як його бачить журнал, навіщо `mappedBy`, `cascade` проти `orphanRemoval`, чому DTO не сутність |

Разом збираємось чотири рази: старт, після бази, злиття, здача. Між ними ніхто ні від кого не залежить: усе спільне вже в базі.

---

## 13. Ризики

| Ризик | Що робити |
|-------|-----------|
| Після переходу на JPA падають тести з №3 | Найчастіше — сервіс тепер повертає DTO або слухач викликає `UPDATE`. Оновлюємо лише зламане, нові сценарії не вигадуємо |
| `LazyInitializationException` у відповіді | Сутність вийшла з сервісу, або список прочитано не через `…With…`. Мапінг — лише в сервісі, у транзакції |
| `ModulesTest` червоний після додавання зв'язку | Зв'язок веде не в `common` і не у власний модуль, або тип лежить у підпакеті (розділ 4.2) |
| Двічі `SELECT` перед `INSERT` у журналі | Сутність із призначеним `id` без `@Version` |
| Старт падає: `NoSuchBeanDefinitionException … transactionManager` | Лишився `NoOpTransactionManager` |
| Здача з `target/` | Не пакуємо: тільки вихідники, `pom.xml`, `mvnw*`, `.mvn`, `README.md` |
