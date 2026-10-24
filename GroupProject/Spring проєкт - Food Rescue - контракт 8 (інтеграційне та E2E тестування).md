# Food Rescue — контракт №8: інтеграційне та end-to-end тестування

> **Актуальний контракт** (останній). Попередні №3-№7 лишаються чинними для своїх частин, але цей документ має пріоритет там, де вони розходяться.

Групове завдання лекції 8 (інтеграційне та end-to-end тестування). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №7 і злитий у `main`.** Робочий код майже не змінюється: це завдання про тести. Єдине нове в `src/main` — один вихідний HTTP-виклик (розділ 5, Людина 3) і дві властивості; без нього нічого замінювати заглушкою WireMock.

> Лектор сказав, що приклад до завдання відкриє на distedu. Перед стартом перевірити, чи він уже є. **Дедлайн групового — середа, 28.10.2026, 23:59.**

---

## 1. Що вимагає викладач

| # | Вимога | Звідки |
|---|--------|--------|
| G1 | Тестові залежності Testcontainers і клас `TestcontainersConfiguration` з `@ServiceConnection`: тести працюють з PostgreSQL у Docker | слайд 68, п. 1 |
| G2 | Спільна анотація з `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(TestcontainersConfiguration.class)`: усі класи — один контекст і один контейнер | слайд 68, п. 2 |
| G3 | Інтеграційні тести через MockMvc на CRUD основних сутностей; результат перевіряється безпосередньо в таблицях через `JdbcClient` | слайд 68, п. 3 |
| G4 | Інтеграційні тести ключового сценарію від HTTP-запиту до таблиці, зокрема **права різних ролей** | слайд 69, п. 1 |
| G5 | Зовнішні HTTP-сервіси замінені заглушками WireMock; перевірено обробку помилок. Усно: якщо зовнішніх викликів немає — додати новий запит і дати йому WireMock | слайд 69, п. 2 і слова лектора |
| G6 | End-to-end тести REST API через `RestTestClient` для основного сценарію | слайд 69, п. 3 |
| G7 | Браузерні тести Playwright для двох-трьох ключових сценаріїв інтерфейсу з №7, з Page Object | слайд 69, п. 4 |
| G8 | End-to-end тести відокремлені від швидких (Failsafe) | слайд 70, п. 1 |
| G9 | Звіт: структура тестів за рівнями, час виконання, результат `./mvnw verify`, знімки екрана або trace браузерних тестів | слайд 70, п. 2 |
| G10 | Архів проєкту без `target` | слайд 70, п. 3 |

Уточнення лектора усно: тести на CRUD обов'язкові; основну логіку покрити інтеграційними; `RestTestClient` — для основних сценаріїв; Playwright — для входу й однієї основної дії користувача (2-3 сценарії); E2E відокремити від решти, «як вам подобається».

---

## 2. Що перевірено на робочому прикладі

Усе нижче відтворено на прототипі (Boot 4.1.1, Java 25, JPA, Spring Modulith 2.1.1, два ланцюги безпеки з №7, Docker Desktop, PostgreSQL 17) перед тим, як потрапити в контракт.

| Що | Результат |
|----|-----------|
| Docker | Без запущеного Docker Desktop `docker info` дає `failed to connect to the docker API`, а тести з Testcontainers падають. Перед усім: Docker Desktop працює, `docker info` без помилок. Перший прогін тягне образ `postgres:17-alpine` (потрібен інтернет) |
| Робочі налаштування БД з №4 (`spring.datasource.url=jdbc:h2:...;MODE=PostgreSQL`, `driver-class-name=org.h2.Driver`, `ddl-auto=update`) | **Нічого міняти не треба.** Коли в тест імпортовано `TestcontainersConfiguration`, бін `@ServiceConnection` перекриває ці властивості, і тест працює на PostgreSQL; `ddl-auto=update` створює таблиці й там. Робочий застосунок далі на H2 |
| Схема на PostgreSQL | Якщо `ddl-auto` у проєкті не задано, на справжній PostgreSQL таблиці **не створюються** (`relation "..." does not exist`) — на відміну від вбудованої H2. Лікується властивістю `spring.jpa.hibernate.ddl-auto=create-drop` у `properties` анотації тесту, не в робочому `application.properties` |
| Наявні `@DataJpaTest` з №4 | Лишаються на вбудованій H2 і проходять без змін, хоча Testcontainers у класпаті: PostgreSQL вмикається лише там, де імпортовано `TestcontainersConfiguration` |
| Два контексти й два контейнери | `@IntegrationTest` (MOCK) і `@E2ETest` (RANDOM_PORT) мають різні ключі кешу контекстів, тому дають **два** контексти й два контейнери (E2E-клас стартує ~30 с). Усередині кожної групи контекст і контейнер один |
| Безпека в `MockMvc` повного контексту | З `spring-boot-starter-security-test` анонімний запит у `@SpringBootTest` + `@AutoConfigureMockMvc` дає `401`/редірект на вхід, `@WithMockUser(roles = "DONOR")` працює на stateless-ланцюгу `/api/**` (повний контекст) і на ланцюгу сторінок (зріз з №7). Додаткових налаштувань не потрібно |
| Версії бібліотек | WireMock — `wiremock-standalone` **3.13.2** (на Maven Central «останньою» показується `4.0.0-beta`, її не брати); Playwright — **1.64.0**; Testcontainers (2.0.5), Awaitility — з Boot BOM, версій не вказуємо. `spring-boot-resttestclient` — модуль Boot 4.1, версії не потребує |
| Імпорт `post` | У класі з MockMvc і WireMock статичні `post(...)` з `MockMvcRequestBuilders` і `WireMock` **конфліктують** (`cannot find symbol: method contentType`). Залишаємо статичний імпорт лише MockMvc, а WireMock — як `WireMock.post("/alerts")` |
| JDK `HttpClient` і WireMock | `RestClient` на `JdkClientHttpRequestFactory` за замовчуванням шле запит з пропозицією HTTP/2, і заглушка WireMock отримує **порожнє тіло**. Лікується створенням клієнта з `HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)` (розділ 5, Людина 3) |
| Асинхронний слухач події | `@ApplicationModuleListener` працює в окремому потоці, тож тест чекає результат Awaitility (`await().atMost(5 с)`), а не паузою. І головне: **запізнілий вебхук з попереднього тесту** потрапляє в журнал заглушки наступного, тому `verify(1, postRequestedFor(...))` без фільтра за вмістом падає (`Expected exactly 1 requests ... but received 2`). Завжди додавати `withRequestBody(containing(<унікальне значення цього тесту>))` |
| Помилка й тайм-аут вебхука | Відповідь `500` і відповідь із затримкою 1,5 с (тайм-аут клієнта `500ms`, заданий у `properties` спільної анотації) обробляються в слухачі: HTTP-відповідь API лишається `201`, у таблиці рядок є |
| `@Sql` після кожного тесту | Кілька скриптів у `@Sql(scripts = {...})` виконуються в заданому порядку, скрипт із `select 1;` — безпечна заглушка. Працює і з `MockMvc`, і з `RANDOM_PORT` |
| `*Test` та `*E2EIT` | `./mvnw test` запускає лише `*Test` (швидкі тести й MockMvc-інтеграційні); `./mvnw verify` додає Failsafe і прогін `*E2EIT`. Достатньо оголосити `maven-failsafe-plugin` у `<build>` без налаштувань — `executions` приходять із `spring-boot-starter-parent` |
| `RestTestClient` | Бін підключається `@AutoConfigureRestTestClient` (пакет `org.springframework.boot.resttestclient.autoconfigure`) і сам знає порт. JWT беремо з `POST /api/v1/auth/login`, далі `headers(h -> h.setBearerAuth(token))` |
| Playwright | Перший `Playwright.create()` **сам завантажує Chromium** (перший прогін ~1,5 хв, потрібен інтернет), далі ~10 с на клас. Окремої команди встановлення локально не треба |
| Локатори Playwright | `getByLabel(...)` знаходить поле лише коли в шаблоні є `<label for="...">`; `AriaRole.ALERT` — лише коли повідомлення має `role="alert"`; `AriaRole.ROW` працює на `<tr>`. Шаблони з №7 (`login`, форми) без цього **не підходять** — розділ 3, крок 7 |
| Trace | `context.tracing().start(...)` / `stop(path)` створює `target/playwright/trace-*.zip` для кожного тесту; відкривається `npx playwright show-trace <файл>` |
| Повний прогін | `./mvnw test`: 18 тестів зелені; `./mvnw verify`: ті самі 18 + 7 `*E2EIT` зелені, `BUILD SUCCESS` |

---

## 3. Спільна частина (робить Людина 1 до старту)

Той самий принцип, що в №3-№7: крок, що торкається спільних файлів (`pom.xml`, `application.properties`, спільні тестові класи), мусить відбутися першим і одним злиттям. Орієнтир — **1,5-2 години**. Поки Людина 1 зайнята, Людина 2 і 3 читають лекцію та розділ 5 і, за потреби, пишуть свої тести локально, не чіпаючи спільних файлів.

| Крок | Що зробити |
|------|-----------|
| 1 | `pom.xml` — залежності й Failsafe (розділ 4.1). Запустити Docker Desktop |
| 2 | `application.properties`: `foodrescue.alerts.url=http://localhost:9999/alerts`, `foodrescue.alerts.timeout=2s` (адреса вигадана: у робочому режимі вихідний виклик нікуди не дійде — помилку ловить слухач, розділ 5) |
| 3 | `src/test/.../TestcontainersConfiguration` (розділ 4.2) |
| 4 | `src/test/.../AlertsStubConfiguration` — WireMock як бін контексту (розділ 4.3) |
| 5 | Спільні анотації `@IntegrationTest` і `@E2ETest` (розділ 4.4) |
| 6 | Три скрипти очищення в `src/test/resources/sql/`: `reset-delivery.sql`, `reset-volunteer.sql`, `reset-lot.sql`, у кожному поки що `select 1;` (розділ 4.5). Кожну таблицю потім заповнює її власник |
| 7 | Шаблони з №7: до `login.html` і форм додати `<label for="...">` до кожного поля, а повідомлення про помилки (вхід, валідація) — `role="alert"` (розділ 2, «Локатори Playwright»). Це правка шаблонів Людини 1; шаблони Людини 2 і 3 вони роблять самі за тим самим правилом |
| 8 | `BrowserTestSupport` (розділ 4.6) |
| 9 | Зелений `./mvnw test` на порожній базі тестів; наявні `@DataJpaTest` не змінювати |
| 10 | README: скелет розділу «Тестування» (розділ 7) |

**Готово, коли:** `./mvnw test` зелений; `./mvnw verify` зелений; Docker Desktop у списку процесів, а в журналі видно рядок `Container postgres:17-alpine started`.

---

## 4. Технічні деталі бази (перевірено)

### 4.1. `pom.xml`

```xml
<properties>
    <wiremock.version>3.13.2</wiremock.version>
    <playwright.version>1.64.0</playwright.version>
</properties>
```

Усе нижче — у `<dependencies>`, `scope` = `test`:

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-resttestclient</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.wiremock</groupId><artifactId>wiremock-standalone</artifactId><version>${wiremock.version}</version><scope>test</scope></dependency>
<dependency><groupId>com.microsoft.playwright</groupId><artifactId>playwright</artifactId><version>${playwright.version}</version><scope>test</scope></dependency>
```

`spring-boot-starter-security-test` (для `@WithMockUser`) уже є з №6, а Awaitility приходить зі стартерами тестів Boot (перевірено компіляцією `import static org.awaitility.Awaitility.await`). У `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
</plugin>
```

### 4.2. `TestcontainersConfiguration`

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
```

Імпорт `PostgreSQLContainer` — `org.testcontainers.postgresql.PostgreSQLContainer`.

### 4.3. `AlertsStubConfiguration` — WireMock як бін

```java
@TestConfiguration(proxyBeanMethods = false)
public class AlertsStubConfiguration {

    @Bean(destroyMethod = "stop")
    WireMockServer alertsServer() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        server.stubFor(WireMock.post("/alerts").willReturn(ok()));
        return server;
    }

    @Bean
    DynamicPropertyRegistrar alertsProperties(WireMockServer alertsServer) {
        return registry -> registry.add("foodrescue.alerts.url", () -> alertsServer.baseUrl() + "/alerts");
    }
}
```

`DynamicPropertyRegistrar` (а не `@DynamicPropertySource`) не ламає кеш контекстів (слайд 43).

### 4.4. Спільні анотації

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "foodrescue.alerts.timeout=500ms")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AlertsStubConfiguration.class})
@Sql(scripts = {"/sql/reset-delivery.sql", "/sql/reset-volunteer.sql", "/sql/reset-lot.sql"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public @interface IntegrationTest {
}
```

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "foodrescue.alerts.timeout=500ms")
@AutoConfigureRestTestClient
@Import({TestcontainersConfiguration.class, AlertsStubConfiguration.class})
@Sql(scripts = {"/sql/reset-delivery.sql", "/sql/reset-volunteer.sql", "/sql/reset-lot.sql"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public @interface E2ETest {
}
```

Тайм-аут `500ms` задано тут, а не в тестовому класі, щоб не створювати новий контекст на кожен клас (слайд 16). Якщо в проєкті на `PostgreSQL` потрібна `ddl-auto` (розділ 2), додається в `properties` обох анотацій.

### 4.5. Скрипти очищення

Порядок у `@Sql` — від таблиць, що посилаються на інші, до таблиць, на які посилаються (`delivery` → `volunteer` → `lot`). Якщо за зовнішніми ключами треба інакше — міняємо один раз у базі. Кожен скрипт містить лише `delete from <таблиця>;` для таблиць свого модуля, у порядку ключів усередині модуля. Користувачів (`app_users`) **не чіпаємо** — вхід у тестах спирається на `DemoUsers`.

Рядки `DemoData` у таблицях лотів, волонтерів і т. д. скрипти теж видаляють, тому **правило для всіх тестів**: нічого не очікувати від демо-даних; перевіряти за унікальним значенням свого тесту або порівнювати кількість рядків до й після.

### 4.6. `BrowserTestSupport`

```java
public abstract class BrowserTestSupport {

    static Playwright playwright;
    static Browser browser;

    @LocalServerPort int port;
    BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setBaseURL("http://localhost:" + port));
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        context.tracing().stop(new Tracing.StopOptions()
                .setPath(Paths.get("target/playwright/trace-" + System.nanoTime() + ".zip")));
        context.close();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }
}
```

Один браузер на клас, новий `BrowserContext` (свої куки й сесія) на кожен тест, відносні адреси (`page.navigate("/login")`) завдяки базовій адресі з `@LocalServerPort`.

---

## 5. Треки по людях — кожен тестує свій модуль

Усі три працюють паралельно, ніхто нікого не чекає: тестові дані кожен готує сам через REST-виклики наявних ендпоінтів (як у №2-№6) або `INSERT`, і ніщо не залежить від тестів інших. Назви класів: `…IntegrationTest` (запускає `test`), `…E2EIT` (запускає `verify`).

| | Людина 1 (`lot`) | Людина 2 (`volunteer`) | Людина 3 (`delivery`) |
|---|---|---|---|
| **G3, G4: MockMvc + `JdbcClient`** (`@IntegrationTest`) | CRUD лотів. `DONOR`: створити, оновити, скасувати; `VOLUNTEER` → `403`; без токена → `401`; некоректні дані → `400` і кількість рядків не змінилась. Форма `POST /lots` з `csrf()` → рядок у таблиці | Створення волонтера, резервування й скасування: `VOLUNTEER` → зміни в таблицях; `DONOR` → `403` | CRUD пунктів призначення (`COORDINATOR`; чужа роль → `403`); `pickup` → `deliver` → `confirm` (`VOLUNTEER`), статуси в таблицях |
| **G5: WireMock** | — | — | Вихідний виклик і його тести (нижче) |
| **G6: `RestTestClient`** (`@E2ETest`) | `LotApiE2EIT`: токен `DONOR` створює лот, `VOLUNTEER` не може | `ReservationApiE2EIT`: токен `VOLUNTEER` резервує лот | `DeliveryFlowE2EIT`: **основний сценарій** — послідовність із Postman-колекції (№5): пункт → волонтер → лот → публікація → резервування → `pickup` → `deliver` → `confirm`, кожен крок токеном своєї ролі, у кінці стан у таблицях |
| **G7: Playwright** (`@E2ETest`, Page Object) | `LotUiE2EIT`: донор входить і створює лот через форму, бачить рядок у списку; неправильний пароль → повідомлення `role="alert"` | `ReservationUiE2EIT`: волонтер входить і резервує лот на сторінці резервувань | `DestinationPointUiE2EIT`: координатор входить і створює пункт призначення |
| **Таблиці для `reset-*.sql`** | `reset-lot.sql` | `reset-volunteer.sql` | `reset-delivery.sql` |

Page Object — у тому самому пакеті, що й тест: `LotPages`, `ReservationPages`, `DestinationPointPages`. Спільна дія входу — `loginAs(username, password)` (шукає поля за підписом `getByLabel`, кнопку за роллю `AriaRole.BUTTON`); локатори тільки за роллю, підписом чи текстом, не за CSS (слайд 50). Один сценарій — один тест; перевірки валідації в браузері не повторюємо (вони вже в MockMvc, слайд 64).

### 5.1. Вихідний HTTP-виклик (G5) — Людина 3

У проєкті зовнішніх HTTP-сервісів немає, тож додаємо один мінімальний (за словами лектора). У модулі `delivery` — слухач `DeliveryFinishedEvent` (з №3), що повідомляє зовнішній сервіс сповіщень:

```java
@Component
public class DeliveryAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAlertNotifier.class);

    private final RestClient client;
    private final String url;

    public DeliveryAlertNotifier(@Value("${foodrescue.alerts.url}") String url,
                                 @Value("${foodrescue.alerts.timeout:2s}") Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
        this.url = url;
    }

    @ApplicationModuleListener
    void on(DeliveryFinishedEvent event) {
        try {
            client.post().uri(url)
                    .body(Map.of("title", "DELIVERY_" + event.outcome(), "message", "lot " + event.lotId()))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Alert webhook failed: {}", ex.getMessage());
        }
    }
}
```

Слухач, як і в №3, тонкий; помилку зовнішнього сервісу він **не пробиває** назад у доставку. `HTTP_1_1` обов'язковий (розділ 2).

Тести (клас `DeliveryAlertIntegrationTest`, `@IntegrationTest`):

- після `confirm` заглушка отримала `POST /alerts`: `Content-Type: application/json`, `$.title` = `DELIVERY_CONFIRMED`, тіло містить `lotId` цього тесту (Awaitility, `atMost` 5 с);
- заглушка повертає `500`: відповідь `confirm` лишається успішною, у таблиці статус збережено;
- заглушка відповідає із затримкою 1,5 с: клієнт перериває на `500ms`, відповідь API успішна;
- перед кожним тестом `alertsServer.resetAll()` і повторне налаштування `stubFor(WireMock.post("/alerts").willReturn(ok()))`; кожне `verify` фільтрується за унікальним вмістом тіла (розділ 2).

### 5.2. Правила для всіх тестів

- Перевірка результату — окремо від того, що його записало: читаємо таблицю через `JdbcClient`, не через той самий сервіс (слайд 63). `@Transactional` на тестах не ставимо (слайд 63);
- ніяких `Thread.sleep`: чекаємо Awaitility; ніяких фіксованих портів — лише `@LocalServerPort` і динамічний порт WireMock;
- тест не залежить від порядку запуску й не розраховує на `DemoData` (розділ 4.5);
- різні `@MockitoBean` у різних класах створюють нові контексти — у `@IntegrationTest`/`@E2ETest` їх не використовуємо, а зовнішнє підміняємо на рівні HTTP заглушкою (слайд 16).

---

## 6. Звіт (G9)

Один файл `report-tests.md` у корені репозиторію й в архіві. Складає Людина 1, решта дають свої числа й скриншоти:

1. Таблиця рівнів: швидкі тести (`*Test`), інтеграційні MockMvc (`@IntegrationTest`), `RestTestClient` (`*E2EIT`), Playwright (`*E2EIT`) — кількість класів і тестів на кожному.
2. Час виконання: беремо з `target/surefire-reports` і `target/failsafe-reports` (атрибут `time` у `TEST-*.xml`) — час найповільніших класів і загальний.
3. Результат `./mvnw verify` — скриншот підсумку (`Tests run: …, BUILD SUCCESS`).
4. Браузерні тести: скриншоти сторінок для різних ролей **або** `target/playwright/trace-*.zip` (відкриваються `npx playwright show-trace <файл>`).
5. Одне речення про те, чому контейнер один на контекст і чому E2E винесені окремо.

---

## 7. README.md — що додаємо

Розділ «Тестування», кожен 2-3 речення про свої тести:

1. Як запустити: Docker Desktop має працювати; `./mvnw test` (швидкі + MockMvc) і `./mvnw verify` (додає E2E).
2. Що саме покриває кожен рівень у вашому модулі.
3. Де звіт і де лежать trace браузерних тестів.

---

## 8. Готово, коли (як перевірятиме викладач)

**1. Швидкі тести**
```
./mvnw test
```
`BUILD SUCCESS`; у журналі `postgres:17-alpine`; наявні `@DataJpaTest` і `ModulesTest` зелені.

**2. Усі тести, E2E окремо**
```
./mvnw verify
```
`BUILD SUCCESS`; у `target/failsafe-reports` лише `*E2EIT`; у `target/playwright` є `trace-*.zip`.

**3. Таблиці, а не сервіси**
У кожному інтеграційному тесті результат читається з таблиці через `JdbcClient`.

**4. Ролі**
У кожному модулі є тест, де чужа роль отримує `403`, а запис у таблиці не з'являється.

**5. Заглушка**
`DeliveryAlertIntegrationTest`: успіх, `500`, тайм-аут.

**6. Звіт і архів (G9, G10)**
`report-tests.md` у корені; архів без `target`.

---

## 9. Чого не робимо

- `@ApplicationModuleTest` (слайди 36-38) і `Scenario` — немає у вимогах групового завдання (достатньо `ModulesTest` з №3);
- `@RestClientTest`/`MockRestServiceServer`, контрактні тести Spring Cloud Contract/Pact (слайд 40) — замінює WireMock;
- `@DataJpaTest` на Testcontainers (слайд 27) — не вимагається; наявні `@DataJpaTest` лишаються на H2;
- бін `Clock` у `JwtService` та тест простроченого токена (слайд 60) — немає у вимогах;
- Selenium/Cypress, Gradle-варіанти, тести в нативному образі GraalVM, CI-пайплайн (слайди 56-58, 62);
- `@Testcontainers(disabledWithoutDocker = true)`: пропущені тести дають зелену збірку без жодної перевірки (слайд 23);
- `TestApplication` для запуску з контейнерами в розробці та `spring-boot-docker-compose` (слайд 26);
- переведення робочого застосунку з H2 на PostgreSQL — робоча конфігурація з №4 не змінюється;
- нових бізнес-правил і ендпоінтів — окрім одного вихідного виклику з розділу 5.1.

---

## 10. Ризики

| Ризик | Що робити |
|-------|-----------|
| Усі тести з контейнером падають: `Could not find a valid Docker environment` або `failed to connect to the docker API` | Запустити Docker Desktop, перевірити `docker info`. Це не помилка тестів чи конфігурації Spring (слайд 23) |
| `relation "..." does not exist` | `ddl-auto` не задано: додати `spring.jpa.hibernate.ddl-auto=create-drop` у `properties` спільних анотацій (розділ 2) |
| Схема не створюється на PostgreSQL через назву таблиці чи колонки (наприклад, зарезервоване слово) | Це якраз те, що H2 приховувала (слайд 21): перейменувати в `@Table`/`@Column`. Перша перевірка — перший прогін `@IntegrationTest` |
| `Expected exactly 1 requests ... but received 2` у WireMock | Запит із попереднього тесту; фільтрувати `verify` за унікальним значенням у тілі (розділ 2) |
| WireMock отримує порожнє тіло | Клієнт створено без `HttpClient.Version.HTTP_1_1` (розділ 2) |
| `cannot find symbol: method contentType(MediaType)` | Конфлікт статичних `post(...)`: WireMock — тільки як `WireMock.post(...)` |
| Playwright не знаходить поле чи повідомлення | У шаблоні немає `<label for>` або `role="alert"` (розділ 3, крок 7) |
| Перший запуск Playwright «зависає» 1-2 хвилини | Завантажується Chromium; потрібен інтернет |
| Два контейнери замість одного, довгий старт | Очікувано (MOCK і RANDOM_PORT — різні контексти); зайві контексти створюють різні `@MockitoBean`/`properties` у класах — не додавати (розділ 5.2) |
| Тест залежить від порядку запуску чи демо-даних | Заборонено (розділ 4.5, 5.2); перевіряти за унікальним значенням або дельтою кількості |
| Docker немає на машині викладача | Лектор у лекції назвав вимогу Docker «не такою критичною», бо більшість машин із цим справляються; у звіті й README вказати `docker info` як передумову |

---

## 11. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| Зараз | разом | Домовитися, хто Людина 1, 2, 3 (як у №3-№7). Прочитати розділи 3-5. Запустити Docker Desktop, перевірити `docker info`. Перевірити на distedu, чи є приклад від лектора |
| До старту (~1,5-2 год) | Людина 1 | База: розділ 3, одним злиттям у `main`. Людини 2 і 3 читають і готують тести локально |
| Основна частина (~3 год) | кожен окремо | Свій трек із розділу 5: MockMvc-інтеграційні, `RestTestClient`, один сценарій Playwright; Людина 3 додатково вихідний виклик і WireMock; свої рядки в `reset-*.sql` і README |
| Перед здачею (~45 хв) | разом | Злити все в `main`; `./mvnw verify` на чистій копії; звіт (розділ 6); пройти розділ 8 |
| Здача (середа, 28.10.2026, 23:59) | разом | Архів без `target` і звіт на distedu. Кожен готовий пояснити будь-яку частину |
