# Food Rescue — контракт №7: Thymeleaf і вебінтерфейс

> **Актуальний контракт** (останній). Попередні №3-№6 лишаються чинними для своїх частин, але цей документ має пріоритет там, де вони розходяться (єдиний `SecurityFilterChain` з №6 тут розділяється на два).

Групове завдання лекції 7 (Web: Thymeleaf і SPA). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №6 і злитий у `main`.** REST API, JWT, ролі, `@PreAuthorize` лишаються як є. Це завдання додає над ними вебсторінки з формою входу.

> Лектор сказав наприкінці лекції, що приклад до завдання з'явиться на distedu. Перед стартом перевірити, чи він уже є; якщо відрізнятиметься в дрібницях, структура (два ланцюги безпеки, сторінки, фрагменти, сторінки помилок) зміниться мало, бо збігається зі слайдами 75-76.

---

## 1. Що вимагає викладач

| # | Вимога | Звідки |
|---|--------|--------|
| G1 | Щонайменше дві сторінки Thymeleaf для ключового сценарію: список записів і форма створення з валідацією JSR-380 та патерном Post-Redirect-Get | слайд 75, п. 1 |
| G2 | Спільні елементи (навігація, ім'я користувача, кнопка виходу) — у `th:fragment`; стилі з локальних статичних ресурсів із версіонуванням за хешем вмісту | слайд 75, п. 2 |
| G3 | Сторінка входу та власні `error/403.html` і `error/404.html` у стилі проєкту | слайд 75, п. 3 |
| G4 | REST API під `/api/**` лишається на JWT; для сторінок — другий `SecurityFilterChain` з формою входу та **увімкненим** CSRF | слайд 76, п. 1 |
| G5 | Вхід на сторінки для користувачів і ролей, які вже в базі даних після №6 | слайд 76, п. 2 |
| G6 | Кнопки й розділи залежно від ролі через діалект `sec:`; ті самі права перевіряються на сервері (правила `SecurityFilterChain` або `@PreAuthorize`) | слайд 76, п. 3 |
| G7 | Звіт: ланцюги безпеки, ролі, скриншоти сторінок для різних ролей; архів проєкту | слайд 76, п. 4 і слова лектора: «які правила ви додали і сам архів проекту» |

Уточнення лектора усно: мінімум — вхід і вихід, плюс бізнесова сторінка; «2-3 сторінки, щоб я розумів, що ви щось можете зробити»; REST-частину не викидати; користувач з однією роллю не може робити те, що йому не дозволено.

---

## 2. Що перевірено на робочому прикладі

Усе нижче відтворено на прототипі (Boot 4.1.1, Java 25, JPA + H2, Spring Security 7.1.1, Spring Modulith 2.1.1, структура модулів `common` / `lot` з підпакетом `lot.web`) перед тим, як потрапити в контракт.

| Що | Результат |
|----|-----------|
| Два ланцюги: `@Order(1)` + `securityMatcher("/api/**")` (JWT, STATELESS, CSRF вимкнений) і `@Order(2)` без матчера (форма входу, CSRF увімкнений) | Працюють одночасно з **одним** `UserDetailsService` (користувачі з БД, Argon2). `POST /api/v1/auth/login` → `200` з токеном; форма входу `donor1` → редірект на `/lots`; анонімний `GET /lots` → `302 /login`; анонімний `GET /api/v1/lots` → `401` у форматі `ProblemDetail` (не редірект) |
| Сесія й токен не змішуються | Запит на `/api/**` лише з сесійною кукою → `401`. Сторінка з сесією та сміттєвим заголовком `Authorization: Bearer garbage` → `200` (за умови `FilterRegistrationBean.setEnabled(false)` з №6) |
| `springdoc` (Swagger UI) | Шляхи `/swagger-ui/**` і `/v3/api-docs/**` не під `/api/**`, тому потрапляють у ланцюг сторінок. Їх треба додати в `permitAll` **цього** ланцюга, інакше вони редіректять на `/login`. З додаванням: обидва → `200` анонімно |
| Версіонування за хешем | З трьох властивостей зі слайда 16 `th:href="@{/css/app.css}"` перетворюється на `/css/app-<md5>.css` |
| Фрагменти | `th:replace="~{fragments/layout :: head('Заголовок')}"` у `<head>` і `~{fragments/layout :: nav}` у `<body>` працюють; ім'я користувача й кнопка виходу (форма POST) приходять із фрагмента |
| Сторінки `error/403.html`, `error/404.html` | Рендеряться **лише** коли клієнт просить HTML (`Accept: text/html`, як робить браузер). `curl` без заголовка й `fetch` отримують JSON. Перевіряти через `curl -H "Accept: text/html"` або браузер |
| Права лише на сервісі (`@PreAuthorize`), без URL-правил у ланцюгу сторінок | `POST /lots` від `VOLUNTEER` → `403` і власна сторінка `403.html`. Форму `GET /lots/new` після входу відкриває будь-хто залогінений (`200`), забороняється саме відправлення — так само, як в індивідуальному завданні |
| Forms і CSRF | `th:action` додає приховане поле `_csrf`; `POST` без токена → `403` |
| Валідація | Некоректні дані → повторний показ форми (`200`, без редіректу) з повідомленнями під полями; успіх → `302` на список |
| Апостроф у повідомленні валідації | Звичайний `'` у `@NotBlank(message = "...обов'язковою")` **зникає** з виводу (Hibernate Validator проганяє рядок через `MessageFormat`, де `'` — спецсимвол). Писати `’` (U+2019): `"Назва є обов’язковою"` |
| Spring Modulith | `ApplicationModules.verify()` зелений, коли вебконтролер лежить у підпакеті модуля (`lot.web`), а `common` лишається без підпакетів. Контролер звертається лише до `LotService` свого модуля |
| Наявні REST `@WebMvcTest` після додавання Thymeleaf | Проходять без змін із трьома `@MockitoBean` з №6 (`JwtValidatorService`, `UserDetailsService`, `AuthenticationEntryPoint`) |
| `@WebMvcTest` для вебконтролера з безпекою | Потрібен ручний `MockMvc` із `springSecurity()` (як у №6) і **чотири** мокі: `JwtService`, `JwtValidatorService`, `UserDetailsService` + сервіс модуля, бо `SecurityConfig` приймає `JwtService` для `apiFilterChain`. У `MockMvc` редірект на вхід — відносний: `redirectedUrl("/login")`, не `**/login` |
| `JwtLoginFilter` з №6 | `unsuccessfulAuthentication` має оголошувати `throws IOException, ServletException`, бо `AuthenticationEntryPoint.commence` кидає обидва (приклад у №6 виправлено) |

---

## 3. Спільна частина (робить Людина 1 до старту) — пакет `common`, без підпакетів

Той самий принцип, що в №3-№6: крок, що зачіпає `common`, мусить відбутися першим і одним злиттям. Орієнтир — **1,5-2 години**. Поки Людина 1 зайнята, Людина 2 і 3 читають лекцію та розділ 5 — яку сторінку роблять у своєму модулі — і готують шаблони й форму в себе локально, але спільних файлів не чіпають.

| Крок | Що зробити |
|------|-----------|
| 1 | `pom.xml`: `spring-boot-starter-thymeleaf`, `org.thymeleaf.extras:thymeleaf-extras-springsecurity6` (без другої `sec:` не працює). `spring-boot-starter-validation` уже є з №2 |
| 2 | `SecurityConfig` з №6 розділити на `apiFilterChain` (`@Order(1)`) і `webFilterChain` (`@Order(2)`) за розділом 4.1; swagger-шляхи перенести в `permitAll` ланцюга сторінок |
| 3 | `WebConfig implements WebMvcConfigurer` у `common`: `addViewController("/login").setViewName("login")` |
| 4 | `src/main/resources/templates/fragments/layout.html` — фрагменти `head(title)` і `nav` (розділ 4.2). У `nav` одразу лежать усі три посилання на сторінки з розділу 5, кожне в `sec:authorize` для своєї ролі, щоб Людини 2 і 3 не правили цей файл |
| 5 | `templates/login.html` (розділ 4.3) |
| 6 | `templates/error/403.html` і `templates/error/404.html`: використовують `head(...)`, текст українською, посилання на `/lots` |
| 7 | `static/css/app.css` + три властивості версіонування в `application.properties` (розділ 4.4). Bootstrap із CDN **не** підключаємо — стилі лише локальні |
| 8 | `lot.web`: `LotWebController` + `CreateLotForm` + `templates/lot/list.html` і `lot/create-form.html` — це частина Людини 1 з розділу 5, але вона робить їх у базі, щоб решта бачила робочий зразок |
| 9 | Оновити `ModulesTest` (має лишитися зеленим) і тест безпеки для вебконтролера за зразком із розділу 5.3 |
| 10 | README: скелет розділу «Вебінтерфейс» (розділ 7) |

**Готово, коли:** `./mvnw test` зелений; застосунок стартує; у браузері `/lots` без входу веде на `/login`; вхід `donor1` відкриває список лотів; REST-сценарій з №6 (`401` / токен / `200` / `403`) працює так само.

---

## 4. Технічні деталі бази (перевірено)

### 4.1. `SecurityConfig` — два ланцюги

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            ObjectMapper objectMapper,
            JwtAuthenticationFilter jwtFilter,
            ProblemDetailAuthenticationEntryPoint authEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {

        JwtLoginFilter loginFilter = new JwtLoginFilter(authenticationManager, jwtService, objectMapper, authEntryPoint);

        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/error", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/lots"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }

    // FilterRegistrationBean<JwtAuthenticationFilter> (setEnabled(false)), AuthenticationManager
    // і corsConfigurationSource() — без змін з №6
}
```

Ланцюг сторінок **навмисно грубий**, як і в №6: «відкрито» або «будь-який залогінений». Хто саме може виконати дію, вирішує `@PreAuthorize` на сервісі (його вже поставили за №6), тож троє людей не правлять один спільний файл. CSRF у ланцюгу сторінок **не вимикаємо** (G4).

`/error` у `permitAll` — за слайдом 51: інакше помилка анонімного користувача перетворюється на редірект на вхід.

### 4.2. `fragments/layout.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security" lang="uk">
<head th:fragment="head(title)">
    <meta charset="UTF-8">
    <title th:text="${title}">Food Rescue</title>
    <link rel="stylesheet" th:href="@{/css/app.css}"/>
</head>
<body>
<nav th:fragment="nav">
    <a sec:authorize="isAuthenticated()" th:href="@{/lots}">Лоти</a>
    <a sec:authorize="hasRole('VOLUNTEER')" th:href="@{/reservations}">Резервування</a>
    <a sec:authorize="hasRole('COORDINATOR')" th:href="@{/destination-points}">Пункти призначення</a>
    <span sec:authorize="isAuthenticated()">
        <span sec:authentication="name">user</span>
        <form th:action="@{/logout}" method="post" style="display:inline"><button type="submit">Вийти</button></form>
    </span>
</nav>
</body>
</html>
```

Підключення на сторінці: `<head th:replace="~{fragments/layout :: head('Лоти')}"></head>` і `<nav th:replace="~{fragments/layout :: nav}"></nav>`. Вихід — лише формою POST (`th:action` додає `_csrf`).

### 4.3. `login.html`

Звичайна форма `th:action="@{/login}" method="post"` з полями `username` і `password`; блок `th:if="${param.error}"` з однаковим повідомленням для невідомого користувача й неправильного пароля. `head(...)` береться з фрагмента.

### 4.4. Версіонування статики

```properties
spring.web.resources.chain.enabled=true
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**
spring.web.resources.chain.cache=true
```

Посилання лише через `th:href="@{/css/app.css}"`, інакше ім'я з хешем не підставиться.

---

## 5. Сторінки по людях і тести

Кожен веде **свою сторінку наскрізь** у своєму модулі: контролер у підпакеті `web` модуля, форма з `@NotBlank`/`@Size`/`@Min`, шаблони в `templates/<модуль>/`, тест. Контролер викликає **лише публічний сервіс свого модуля** (Modulith `verify()` це перевіряє). Бізнес-логіка сервісів не змінюється.

| Хто | Модуль | Сторінки | Хто бачить / виконує |
|-----|--------|----------|----------------------|
| Людина 1 | `lot` | `GET /lots` (список), `GET /lots/new` + `POST /lots` (форма, PRG); у базі | список — усі залогінені; кнопка «Додати лот» і створення — `DONOR` |
| Людина 2 | `volunteer` | `GET /reservations` (список резервувань і форма резервування), `POST /reservations` | `VOLUNTEER` |
| Людина 3 | `delivery` | `GET /destination-points` (список), `GET /destination-points/new` + `POST /destination-points` (форма, PRG) | `COORDINATOR` |

Методи сервісів, які викликають форми, — ті самі, що вже мають `@PreAuthorize` з №6 (`LotService.create`, `VolunteerService.reserve`, `DeliveryService.createDestinationPoint`). Нових правил у `SecurityConfig` не додаємо.

### 5.1. Правила для кожної форми

- `th:action="@{...}"` і `th:object`; повідомлення про помилки — `th:errors` під полем;
- після успішного збереження `return "redirect:/..."` (PRG), після помилки валідації — повернути ту саму сторінку без редіректу;
- у повідомленнях валідації апостроф — лише `’`;
- користувацький текст виводимо тільки через `th:text` (`th:utext` заборонено — слайд 44).

### 5.2. Наявні тести не чіпаємо

REST `@WebMvcTest` з №6 далі проходять із трьома `@MockitoBean`, нічого додавати не треба (перевірено).

### 5.3. Новий тест безпеки сторінки — по одному на людину

```java
@WebMvcTest(LotWebController.class)
@Import({SecurityConfig.class, JacksonConfig.class, ProblemDetailAuthenticationEntryPoint.class,
         ProblemDetailAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class LotWebControllerSecurityTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @MockitoBean JwtService jwtService;
    @MockitoBean JwtValidatorService jwtValidatorService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean LotService lotService;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/lots")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/lots").param("title", "abc").param("quantity", "1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void donorCreatesAndIsRedirected() throws Exception {
        mvc.perform(post("/lots").with(csrf()).param("title", "abc").param("quantity", "1"))
                .andExpect(redirectedUrl("/lots"));
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void invalidFormStaysOnForm() throws Exception {
        mvc.perform(post("/lots").with(csrf()).param("title", "").param("quantity", "0"))
                .andExpect(status().isOk()).andExpect(view().name("lot/create-form"));
    }
}
```

Шаблон перевірений — усі чотири сценарії пройшли. Перевірку «неправильна роль → 403» робимо **не** в цьому зрізі: сервіс там мок, тож `@PreAuthorize` не діє. Її покриває ручна перевірка з розділу 8 (скриншот `403.html`).

---

## 6. Звіт (G7)

Один файл (`.md` або `.pdf`) у корені репозиторію й в архіві. Складає Людина 1, решта дають свої скриншоти й рядки:

1. Два ланцюги безпеки: що матчить кожен, чим автентифікує (JWT / форма), чому в одного CSRF вимкнений, а в іншого ні (одне речення).
2. Таблиця «роль → що бачить на сторінках → що може виконати» (за розділом 5).
3. Скриншоти для різних ролей: список лотів під `DONOR` (є кнопка) і під `VOLUNTEER` (кнопки нема), сторінка помилок `403`, `404`, форма з помилками валідації, сторінка входу з повідомленням про помилку.
4. Підпис кожного скриншота — користувач, адреса, код відповіді.

---

## 7. README.md — що додаємо

У розділ «Безпека» з №6 дописуємо підрозділ «Вебінтерфейс», кожен 2-3 речення про свою сторінку:

1. Які адреси сторінок і яка роль потрібна.
2. Демо-логіни/паролі для входу через форму (ті самі, що для JWT).
3. Як зібрати й запустити, де звіт.

---

## 8. Готово, коли (як перевірятиме викладач)

**1. Тести**
```
./mvnw test
```
`BUILD SUCCESS`, включно з `ModulesTest` і новими тестами безпеки сторінок (розділ 5.3).

**2. Вхід і ролі (браузер, зі скриншотами)**
- `/lots` без входу → `/login`; неправильний пароль → повідомлення про помилку;
- вхід `donor1` → список лотів, кнопка «Додати лот» є, ім'я `donor1` і кнопка «Вийти» у навігації;
- вхід `volunteer1` → кнопки «Додати лот» нема; відкрита за прямою адресою форма на відправлення дає `403` зі сторінкою `403.html`;
- форма з порожніми полями → помилки під полями; успішне створення → редірект на список, оновлення сторінки не дублює запис;
- `<script>alert(1)</script>` у назві → показано як текст.

**3. Сторінки помилок**
```
curl -i -H "Accept: text/html" http://localhost:8080/nope
```
(після входу) → `404` і HTML з `error/404.html`.

**4. Хеш у назві стилів**
У коді сторінки `<link href="/css/app-...css">` з хешем, у формі є `<input type="hidden" name="_csrf">`.

**5. REST не зламався**
Сценарій слайда 112 з №6: `401` без токена → логін → `200` з `Bearer` → `403` для чужої ролі; `npx newman run postman/food-rescue-collection.json -e postman/food-rescue-environment.json` → `failed: 0`.

**6. Архів і звіт (G7)**
На DistEdu — архів проєкту (і посилання на репозиторій, як і в №6), звіт у корені.

---

## 9. Чого не робимо

- SPA і патерн BFF (слайди 54-67, `csrf.spa()`, `/bff/api/**`, збірка React/Angular) — лектор прямо сказав, що вимога — 2-3 сторінки, а не повний SPA;
- багатомовність (`messages*.properties`, `LocaleChangeInterceptor`, слайд 33) — немає в завданні;
- заголовки безпеки CSP/Referrer-Policy (слайди 49-50) та `ETag`/Brotli/`EncodedResourceResolver` (слайди 12-14) — не вимагаються; з версіонування беремо лише три властивості зі слайда 16;
- завантаження користувацьких файлів поза JAR (слайди 18-20), `spring.threads.virtual.enabled`;
- Bootstrap з CDN чи WebJars — стилі лише локальні, один `app.css`;
- нових бізнес-правил і REST-ендпоінтів — усе з №2-№6 лишається як є;
- реєстрація користувачів через форму — вхід лише для наявних акаунтів з бази (G5).

---

## 10. Ризики

| Ризик | Що робити |
|-------|-----------|
| Swagger UI після розділення ланцюгів редіректить на `/login` | Шляхи `/swagger-ui/**` і `/v3/api-docs/**` у `permitAll` ланцюга сторінок (розділ 4.1) |
| Сторінки помилок не показуються, приходить JSON | Перевіряти з `Accept: text/html` або в браузері (розділ 2) |
| У повідомленні валідації зникає апостроф | Писати `’`, не `'` (розділ 2, 5.1) |
| Форма повертає `403` навіть для правильної ролі | Забули `th:action` — токен `_csrf` не додався (слайд 39) |
| Тест вебконтролера не бачить авторизації або падає на `JwtService` | Збирати `MockMvc` вручну й мокати чотири біни (розділ 2, 5.3) |
| Тест безпеки не проходить, бо очікує `**/login` | У `MockMvc` редірект відносний: `redirectedUrl("/login")` |
| `JwtLoginFilter` не компілюється (`unreported exception ServletException`) | `unsuccessfulAuthentication ... throws IOException, ServletException` (виправлено в №6) |
| Дві людини правлять `layout.html` і `SecurityConfig` | Не мають: усі три посилання вже у `nav`, права — у `@PreAuthorize` на сервісі |
| Лектор відкриє приклад, що відрізняється | Звірити на distedu до старту; зміни, найімовірніше, дрібні |

---

## 11. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| Зараз | разом | Домовитися, хто Людина 1, 2, 3 (як у №3-№6). Прочитати розділи 3-5. Перевірити на distedu, чи є приклад від лектора |
| До старту (~1,5-2 год) | Людина 1 | База: розділ 3, одним злиттям у `main`. Людини 2 і 3 читають і готують шаблони та форму локально |
| Основна частина (~2 год) | кожен окремо | Своя сторінка наскрізь (розділ 5): контролер у `web`, форма, шаблони, тест безпеки, скриншоти для своєї ролі, рядки в README |
| Перед здачею (~30 хв) | разом | Злити все в `main`; звіт (розділ 6); пройти розділ 8 на розпакованому архіві |
| Здача | разом | Архів і посилання на репозиторій на distedu (G7). Кожен готовий пояснити будь-яку частину |
