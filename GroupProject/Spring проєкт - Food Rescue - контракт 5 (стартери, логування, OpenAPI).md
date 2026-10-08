# Food Rescue — контракт №5: стартери, логування, документація API

> Замінено контрактом №6 (Spring Security, JWT) як останнім. Цей документ лишається чинним для своєї частини (автоконфігурація, логування, OpenAPI).

Групове завдання лекції 5 (автоконфігурація Spring Boot, журналювання, OpenAPI). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №4 і злитий у `main`.** Усе з №2-№4 (URL, DTO, винятки, JPA, зв'язки, `JOIN FETCH`) лишається. Це завдання не чіпає бізнес-логіку й базу даних — воно додає інфраструктурний шар над готовим проєктом.

> Викладач сам сказав, що групове цього тижня «не має зайняти дуже багато часу». Орієнтир — **один день**, не тиждень, як було з JPA.

---

## 1. Що вимагає викладач

| # | Вимога | Звідки |
|---|--------|--------|
| R1 | Власний стартер або конфігураційний модуль, що надає спільний функціонал (єдиний для всього проєкту) | слайд 101, п. 1 |
| R2 | Ієрархія налаштувань модуля з профілями `application-dev` і `application-prod` | слайд 101, п. 2 |
| R3 | Тест автоконфігурації через `ApplicationContextRunner`, що перевіряє наявність біна за різних прапорців | слайд 101, п. 3 |
| R4 | `logback-spring.xml` із `RollingFileAppender` (ротація за днями, архівація старих файлів) | слайд 102, п. 1 |
| R5 | Маскування чутливих полів у логах (паролі, платіжні реквізити й подібне — у нашому проєкті найближчий відповідник) | слайд 102, п. 2 |
| R6 | Аудит `catch`-блоків команди: винятки ніде не ковтаються мовчки | слайд 102, п. 3; слова: «переконатися у відсутності приховування винятків у блоках catch» |
| R7 | Ендпоінти задокументовані у Swagger UI, **мінімум 1 приклад** схеми моделі (`example`) | слайд 103, п. 1 |
| R8 | Колекція Postman на **один ключовий інтеграційний сценарій**, збережена в репозиторії | слайд 103, п. 2 |
| R9 | Сценарій успішно виконується автоматично: `npx newman run collection.json -e env.json` | слайд 103, п. 3 |

Додатково зі слів лектора: профілі можуть відрізнятися «за якимись правилами» — «може бути `prod`, може бути якась регіональна частина, тут вже на ваш вибір». Беремо найочевидніший варіант — `dev`/`prod`.

---

## 2. Що перевірено на робочому прикладі

Усе нижче відтворено на прототипі (Boot 4.1.1, Java 25) перед тим, як потрапити в контракт.

| Що | Результат |
|----|-----------|
| `@AutoConfiguration` + `@ConditionalOnProperty` + `@ConfigurationProperties`(record) + файл `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` **у тому самому модулі** (не в окремому jar) | Працює: при звичайному запуску (`@SpringBootTest`, `spring-boot:run`) біт підхоплюється автоматично, без жодного додаткового `@Import` |
| Той самий тест, але через **голий** `new ApplicationContextRunner().run(...)` без `.withConfiguration(AutoConfigurations.of(...))` | **Не працює** — `ApplicationContextRunner` сам по собі не читає `AutoConfiguration.imports`, він не проганяє `@EnableAutoConfiguration`. Тестувати автоконфігурацію можна лише `.withConfiguration(AutoConfigurations.of(ВашКлас.class))` — так, як показано на слайді 34 |
| `ApplicationContextRunner` із `trace-id-enabled=true`, `=false` і без властивості (`matchIfMissing = true`) | Усі три сценарії дають очікуваний результат: біт є / біта немає / біт є за замовчуванням |
| `logback-spring.xml` з `RollingFileAppender` і `%X{traceId:-}` у шаблоні | Застосунок стартує, файл `logs/....log` створюється й пишеться; поза запитом `traceId` — порожній рядок, не `null` і не помилка |
| `springdoc-openapi-starter-webmvc-ui 2.8.5` на Boot 4.1.1 | Компілюється й запускається без правок; `/v3/api-docs` віддає `openapi: 3.1.0`, `@Schema(example = ...)` потрапляє у схему; `/swagger-ui/index.html` відповідає 200 |
| Postman-колекція + `npx newman run` на реальному ендпоінті | Обидва тести (`status 200`, наявність заголовка) зелені |
| Прив'язка `@ConfigurationProperties`-record з **додатковим** конструктором без аргументів | **Ламає прив'язку**: Spring бере конструктор без аргументів, і значення з `application.properties` (наприклад `trace-header=X-Req-Id`) тихо ігноруються. Тому конструктор без аргументів у record **не додаємо** — лишається тільки канонічний з `null`-захистом у компактному конструкторі |
| `spring.jpa.hibernate.ddl-auto=validate` з **in-memory H2** | **Ламає запуск**: `Schema validation: missing table [probe]` — `validate` перевіряє існуючу схему, а в пам'яті вона порожня. Тому в `application-prod.properties` цього рядка **немає** |
| `@AutoConfiguration` всередині пакета, який сканує `@SpringBootApplication` | Працює, але Spring Boot рекомендує виключати такі класи з component scan. Додаємо `@ComponentScan(excludeFilters = ...AutoConfiguration.class)` у головний клас — після цього всі тести зелені, `TraceIdFilter` підхоплюється через `AutoConfiguration.imports` |

---

## 3. Спільна частина (робить Людина 1 до старту) — пакет `common`, без підпакетів

Як і в №3-№4: це єдиний крок, що мусить відбутися **першим**, бо зачіпає `common`. Орієнтир — **1 година**. Поки Людина 1 зайнята, Людина 2 і 3 читають лекцію й розділ 4, готують фрагменти логів і DTO, які потребують маскування чи `@Schema`, — але в `common` нічого не чіпають.

| Крок | Що зробити |
|------|-----------|
| 1 | `pom.xml`: `springdoc-openapi-starter-webmvc-ui` версії **2.8.5** (перевірено на Boot 4.1.1) |
| 2 | `ObservabilityProperties` — `record` з `@ConfigurationProperties(prefix = "foodrescue.observability")`: `boolean traceIdEnabled` (типово `true`), `String traceHeader` (типово `"X-Trace-Id"`) |
| 3 | `ObservabilityAutoConfiguration` — `@AutoConfiguration`, `@EnableConfigurationProperties(ObservabilityProperties.class)`, `@ConditionalOnProperty(prefix = "foodrescue.observability", name = "trace-id-enabled", havingValue = "true", matchIfMissing = true)`, один `@Bean` `TraceIdFilter` з `@ConditionalOnMissingBean` |
| 4 | `TraceIdFilter(String traceHeader)` — `OncePerRequestFilter`: той самий код, що в індивідуальному завданні лекції 5 (`MDC.put`/`response.setHeader`/`finally { MDC.remove(...) }`), але назву заголовка приймає в конструкторі (її дає `ObservabilityProperties.traceHeader()`) |
| 5 | Файл `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` з одним рядком — повним ім'ям класу `ObservabilityAutoConfiguration` |
| 6 | `application-dev.properties`: `logging.level.com.example.foodrescue=DEBUG`, `spring.jpa.show-sql=true` (успадковано з №4) | 
| 7 | `application-prod.properties`: лише `logging.level.root=INFO`. Схему не чіпаємо: у in-memory H2 `validate` не працює (розділ 2) |
| 8 | `logback-spring.xml` у корені ресурсів (розділ 5) |
| 9 | `SensitiveDataMasker` — утилітний клас у `common` (розділ 6) |
| 10 | `ApplicationModulesTest`/`ModulesTest` і наявні тести лишаються зеленими: нова автоконфігурація лежить прямо в `common`, підпакетів не створюємо |
| 11 | `OpenApiConfig` — один `@Bean OpenAPI` з `title`/`version` (розділ 7.1) |

**Готово, коли:** `./mvnw test` зелений; застосунок стартує з профілем за замовчуванням; `GET /v3/api-docs` і `/swagger-ui/index.html` відповідають; у `logs/` з'являється файл після першого запиту.

---

## 4. Автоконфігурація (R1-R3)

### 4.1. Чому саме це — спільна функція

Лектор навів приклади «єдиний формат відповідей або сповіщення». У нас уже є єдиний формат помилок (`ProblemDetail` + `GlobalExceptionHandler` з №2). Другий природний кандидат на «спільну функцію для всього проєкту» — наскрізний `traceId`: він одразу пов'язує це завдання з вимогою R4 (`%X{traceId}` у `logback-spring.xml`) і з індивідуальним завданням цього тижня, яке кожен уже писав окремо. Тому робимо один раз у `common`, а не тричі в кожному модулі.

### 4.2. Код (перевірено)

```java
@ConfigurationProperties(prefix = "foodrescue.observability")
public record ObservabilityProperties(boolean traceIdEnabled, String traceHeader) {

    public ObservabilityProperties {
        if (traceHeader == null) {
            traceHeader = "X-Trace-Id";
        }
    }
}
```

Головний клас (`FoodRescueApplication`) — з виключенням автоконфігурацій із component scan:

```java
@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = AutoConfiguration.class))
public class FoodRescueApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodRescueApplication.class, args);
    }
}
```

```java
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "foodrescue.observability", name = "trace-id-enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter(ObservabilityProperties properties) {
        return new TraceIdFilter(properties.traceHeader());
    }
}
```

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.example.foodrescue.common.ObservabilityAutoConfiguration
```

Файл лежить у тому самому модулі (не в окремому jar) — лектор сам допустив цей варіант: «якщо зможете, зробіть краще окремим модулем, не зможете — тоді просто модуліт окремий сегмент». Окремий Maven-модуль ламає наявну структуру Spring Modulith і дає значно більше роботи, не додаючи нічого до оцінки; тому робимо сегментом у `common`.

### 4.3. Профілі (R2)

```properties
# application-dev.properties
logging.level.com.example.foodrescue=DEBUG
spring.jpa.show-sql=true
```

```properties
# application-prod.properties
logging.level.root=INFO
```

Запуск: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (або `prod`). Без прапорця лишається дефолтний `application.properties` з №4.

### 4.4. Тест автоконфігурації (R3) — у `common`, пише Людина 1

```java
class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void registersFilterByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(TraceIdFilter.class));
    }

    @Test
    void registersFilterWhenPropertyTrue() {
        runner.withPropertyValues("foodrescue.observability.trace-id-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TraceIdFilter.class));
    }

    @Test
    void skipsFilterWhenPropertyFalse() {
        runner.withPropertyValues("foodrescue.observability.trace-id-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
    }
}
```

**Важливо (перевірено):** `.withConfiguration(AutoConfigurations.of(...))` обов'язковий. Без нього `ApplicationContextRunner` не бачить `AutoConfiguration.imports` узагалі, і тест завжди падає незалежно від прапорця — це не помилка в коді, а властивість самого інструмента.

---

## 5. Логування (R4, R5, R6)

### 5.1. `logback-spring.xml` — у корені `src/main/resources`, пише Людина 1 як частину бази

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} %5p [%X{traceId:-}] [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/foodrescue.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/archived/foodrescue-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>14</maxHistory>
            <totalSizeCap>200MB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{ISO8601} %5p [%X{traceId:-}] [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

`%X{traceId:-}` — те саме MDC-значення, яке ставить `TraceIdFilter` із розділу 4. Поза HTTP-запитом (наприклад, у слухачі події чи в тесті) там порожньо — перевірено, не падає.

### 5.2. Маскування (R5) — `SensitiveDataMasker` у `common`, пише Людина 1 як частину бази

У проєкті немає паролів чи номерів карток — найближчі аналоги: **пошта волонтера** (персональні дані) і **код підтвердження доставки** (по суті одноразовий код доступу, компрометація якого дозволяє підтвердити чужу доставку). Маскуємо обидва там, де вони потрапляють у лог.

```java
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String maskCode(String code) {
        if (code.length() <= 1) {
            return "*".repeat(code.length());
        }
        return "*".repeat(code.length() - 1) + code.charAt(code.length() - 1);
    }
}
```

Перевірено: `maskEmail("volunteer@example.com")` → `v***@example.com`; `maskCode("482910")` → `*****0`.

**Де застосовувати — кожен у своєму модулі** (розділ 7): лише в тих `log.info`/`log.warn`, які самі додає ця команда. Не потрібно обгортати всі існуючі рядки — їх і так небагато, і жоден наявний лог зараз не друкує пошту чи код.

### 5.3. Аудит `catch` (R6) — кожен перевіряє свій модуль

```
grep -rn "catch" src/main/java/com/example/foodrescue/<свій_пакет>
```

Для кожного знайденого блоку: виняток або **прокидається далі** (можна з контекстом, як у прикладі лекції — `log.error("...", e); throw new ...(e)`), або обробляється по суті (а не просто `catch (Exception e) {}`). У поточному коді (№2-№4) усі бізнес-перевірки **кидають** типізовані винятки, а не ловлять — `catch` із бізнес-логікою, найімовірніше, не зустрінеться взагалі. Якщо аудит нічого не знайшов — це законний результат, записуємо в README одним реченням: «блоків `catch`, що ковтають виняток, не знайдено».

---

## 6. Документація та Postman (R7, R8, R9)

### 6.1. Глобальна конфігурація OpenAPI — у `common`, пише Людина 1

```java
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI foodRescueOpenApi() {
        return new OpenAPI().info(new Info().title("Food Rescue API").version("1.0.0"));
    }
}
```

Нічого складнішого (авторизація, теги за замовчуванням) не додаємо — цього не вимагає лекція.

### 6.2. По одному прикладу `@Schema` — кожен у своєму модулі

Вимога — **мінімум один** приклад на весь проєкт, але щоб кожен відповідав за свою частину на захисті, кожен додає **один** `@Schema(example = ...)` на полі власного DTO:

| Хто | Де |
|-----|-----|
| Людина 1 | `LotRequest.title` — `@Schema(example = "Випічка з кінця дня")` |
| Людина 2 | `VolunteerRequest.email` — `@Schema(example = "volunteer@example.com")` |
| Людина 3 | `DestinationPointRequest.name` — `@Schema(example = "Їдальня \"Тепла хата\"")` |

Більше анотацій (`@Tag`, `@Operation`, `@ApiResponses`) — **за бажанням**, лекція їх не вимагає для цього завдання.

### 6.3. Postman-колекція на наскрізний сценарій (R8, R9) — пише Людина 3

Один файл `postman/food-rescue-collection.json` і один `postman/food-rescue-environment.json` (`baseUrl = http://localhost:8080`) у корені репозиторію — сценарій **той самий**, що вже є в контракті №4, розділ 10, п. 5: створити пункт → створити волонтера → створити лот → опублікувати → зарезервувати → `pickup` → `deliver` → `confirm`. Переносити його в Postman нічого не винаходячи — кроки вже описані.

Мінімум по тестах у колекції (за прикладом лекції):
```javascript
pm.test("Статус відповіді 200 (або 201 для create-кроків)", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});
```
і хоча б одна перевірка тіла останнього кроку (`confirm` повертає `lotStatus: "CONFIRMED"`).

Запуск і перевірка:
```
npx newman run postman/food-rescue-collection.json -e postman/food-rescue-environment.json
```
Повинен завершитися `BUILD SUCCESS`-еквівалентом Newman — нуль `failed` у підсумковій таблиці. Перед прогоном застосунок має бути запущений (`./mvnw spring-boot:run`), а дані — свіжі (H2 в пам'яті: перезапуск очищає базу).

---

## 7. Розподіл — рівно порівну

| | Людина 1 | Людина 2 | Людина 3 |
|---|---|---|---|
| Крім свого: | **база** (розділ 3) — до старту | — | **Postman-колекція** (розділ 6.3) |
| `@Schema`-приклад | `LotRequest` | `VolunteerRequest` | `DestinationPointRequest` |
| Маскування у своєму модулі | `donorOrgId`/контакти, якщо щось логується (зараз, швидше за все, нічого) | лог реєстрації волонтера: `SensitiveDataMasker.maskEmail(email)` | лог невірного коду підтвердження: `SensitiveDataMasker.maskCode(code)` |
| Аудит `catch` | свій пакет `lot` | свій пакет `volunteer` | свій пакет `delivery` |
| Розділ README | «Автоконфігурація» | «Логування і маскування» | «OpenAPI і Postman» |

Людина 1 знову бере базу **додатково, до старту** — так само, як у №3 і №4: це той самий принцип «ніхто нікого не чекає», і він тут природний, бо автоконфігурація й `logback-spring.xml` — спільні файли, а не частина окремого бізнес-кейсу. Навантаження в підсумку рівне: база невелика (оцінка лектора — «не дуже багато»), а Людина 3 натомість бере Postman-колекцію, яку іншим двом робити не треба.

**Для всіх трьох однаково:**
- Маскування — лише в **нових** рядках логування, які сама команда додає цього тижня. Переписувати весь наявний код не потрібно.
- `@Schema` — одна анотація, без зміни самого поля чи валідації.
- Аудит `catch` — лише читання й, за потреби, виправлення; якщо нічого не знайдено, це теж результат.

---

## 8. README.md — що додаємо

Три короткі розділи (по одному на людину), кожен 3-5 речень:

1. **Автоконфігурація**: що вмикає `ObservabilityAutoConfiguration`, як вимкнути (`foodrescue.observability.trace-id-enabled=false`), як запустити з профілем (`--spring.profiles.active=dev`/`prod`).
2. **Логування і маскування**: де лежить `logback-spring.xml`, що ротується і куди архівується, які два поля маскуються і чому саме вони, результат аудиту `catch`.
3. **OpenAPI і Postman**: адреси `/swagger-ui/index.html` і `/v3/api-docs`, де лежить колекція, команда для `newman`.

---

## 9. Готово, коли (як перевірятиме викладач)

**1. Тести**
```
./mvnw test
```
`BUILD SUCCESS`; серед тестів — `ObservabilityAutoConfigurationTest` (3 сценарії) і `ModulesTest` з №3.

**2. Автоконфігурація вмикається й вимикається**
```
./mvnw spring-boot:run -Dspring-boot.run.arguments=--foodrescue.observability.trace-id-enabled=false
curl -i http://localhost:8080/api/v1/destination-points
```
Заголовка `X-Trace-Id` немає. З прапорцем `=true` (чи без нього) — заголовок є.

**3. Профілі**
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```
У журналі — рівень `INFO`, без рядків `DEBUG` від Hibernate.

**4. Файл логів**
Після кількох запитів у `logs/foodrescue.log` є рядки з `[traceId]`, що збігається із заголовком відповіді `X-Trace-Id`.

**5. Маскування**
```
grep -n "@" logs/foodrescue.log
grep -En "[0-9]{6}" logs/foodrescue.log
```
Повної пошти чи повного 6-значного коду в журналі немає.

**6. OpenAPI**
```
curl http://localhost:8080/v3/api-docs | grep -o "example"
```
Хоча б один збіг. `http://localhost:8080/swagger-ui/index.html` відкривається.

**7. Postman**
```
npx newman run postman/food-rescue-collection.json -e postman/food-rescue-environment.json
```
Усі кроки зелені, `failed: 0`.

---

## 10. Чого не робимо

- Окремого Maven-модуля для стартера (розділ 4.2) — сегмент у `common` того самого модуля технічно так само є «автоконфігурацією», і саме це дозволив лектор;
- Log4j2 (слайди 40-47 — лише порівняння, вибір не впливає на оцінку; лишаємось на Logback, що вже є типово);
- схеми безпеки (`BearerAuth`), `@Tag`/`@Operation` на кожному ендпоінті, `ApiResponses` — не вимагає цього завдання;
- генерації коду за специфікацією (`openapi-generator-maven-plugin`) — це Contract-First підхід, ми йдемо Code-First через SpringDoc, як і радить лектор для невеликої команди;
- повної санітизації всіх полів і всіх логів — лише два поля з розділу 5.2, і лише в нових рядках;
- CI-пайплайна (GitHub Actions) для Newman — лектор показав це як приклад для великих команд, не як вимогу щотижневого завдання;
- нових бізнес-правил чи сутностей — усе з ТЗ і контрактів №1-№4 лишається як є.

---

## 11. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| Зараз | разом | Домовитися, хто Людина 1, 2, 3 (як і в №3-№4). Прочитати розділи 3-6 |
| До старту (~1 год) | Людина 1 | База: розділ 3, одним злиттям у `main`. Людини 2 і 3 читають і готують, які рядки логів і яке поле DTO будуть їхніми (не чіпаючи `common`) |
| Основна частина (~2-3 год) | кожен окремо | Свій `@Schema`, маскування у своєму модулі, аудит `catch`, розділ README. Людина 3 додатково збирає Postman-колекцію |
| Перед здачею (~30 хв) | разом | Злити все в `main`; пройти розділ 9 повністю |
| Здача | разом | Архів на distedu (без `target/`, без `logs/`). Кожен готовий пояснити будь-яку частину |

---

## 12. Наступна тема (для орієнтиру, не для цього завдання)

Лектор двічі згадав, що **наступна практика — Security**: токени/сесії, фільтри для захисту ендпоінтів (той самий `OncePerRequestFilter`, яким ми щойно писали `TraceIdFilter`, розшириться на автентифікацію). Також обіцяв розглянути **інтеграційні тести** «наступного разу». Нічого з цього не потрібно для контракту №5 — лише щоб не дивуватися, звідки в лекції 6 візьметься продовження теми фільтрів.
