# Food Rescue — контракт №6: Spring Security, JWT

> **Актуальний контракт** (останній). Попередні №3-№5 лишаються чинними для своїх частин, але цей документ має пріоритет там, де вони розходяться.

Групове завдання лекції 6 (Spring Security). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №5 і злитий у `main`.** Усе з №2-№5 лишається. Це завдання додає автентифікацію й авторизацію над готовим API — ендпоінти, DTO й бізнес-логіка не змінюються, окрім одного рядка `@PreAuthorize` на кожному методі, що міняє дані.

> Лектор сам сказав наприкінці лекції: «завдання поки що не буде відкрите, я внесу деякі правки і так само додам приклад». Приклад (`example_project`, пакет `com.ukma.cctv`) уже з'явився й перевірений — розділи нижче оновлено під нього. Структура (JWT + ролі + `ProblemDetail`) співпадає зі слайдами; розбіжності — у деталях реалізації фільтрів і `ObjectMapper`, усі перелічені в розділі 2.

---

## 1. Що вимагає викладач

| # | Вимога | Звідки |
|---|--------|--------|
| R1 | Кілька тестових користувачів із різними ролями | слайд 110, п. 1 |
| R2 | Бін `PasswordEncoder`, у логах — приклад згенерованого хеша | слайд 110, п. 2 |
| R3 | `UserDetailsService` читає акаунти через JPA-репозиторій, кидає `UsernameNotFoundException` | слайд 110, п. 3 |
| R4 | `SessionCreationPolicy.STATELESS` у `SecurityFilterChain` (на відміну від сесійного індивідуального завдання) | слайд 111, п. 1 |
| R5 | CORS для веб-клієнта; обробка помилок безпеки через RFC 9457 `ProblemDetail` | слайд 111, п. 2 |
| R6 | Сервіс JWT-токенів; фільтри автентифікації й валідації із захистом через `AuthenticationEntryPoint` | слайд 111, п. 3 |
| R7 | Запит без токена на захищений ендпоінт → **401** | слайд 112, п. 1 |
| R8 | Логін із правильними обліковими даними → токен доступу | слайд 112, п. 2 |
| R9 | Запит з `Authorization: Bearer <token>` на захищений ресурс → **200** | слайд 112, п. 3 |
| R10 | Користувач з недостатніми правами → **403** | слайд 112, п. 4 |
| R11 | Архів проєкту **і посилання на репозиторій** на DistEdu | слайд 112, п. 5 |

---

## 2. Що перевірено на робочому прикладі

Усе нижче відтворено на прототипі (Boot 4.1.1, Java 25, JPA + H2, Spring Security 7.1.1) і зіставлено з офіційним `example_project` лектора (пакет `com.ukma.cctv`), перш ніж потрапити в контракт. Перелічені пункти — речі, про які ні слайди, ні приклад лектора **не попереджають прямо**, і без яких код не збирається або падає в рантаймі.

| Що | Результат |
|----|-----------|
| `Argon2PasswordEncoder` (слайд 32) | **Падає в рантаймі** з `NoClassDefFoundError: org.bouncycastle...`, якщо в `pom.xml` немає окремо доданого `org.bouncycastle:bcprov-jdk18on`. Spring Security сам цю залежність не тягне. Підтверджено й офіційним прикладом — у його `pom.xml` ця ж залежність є |
| Один REST-ендпоінт логіну (`AuthController`) чи два фільтри? | Слайди (п. R6 — «фільтри», множина) і офіційний приклад сходяться на **двох фільтрах**: `JwtLoginFilter extends UsernamePasswordAuthenticationFilter` (сам логін) + `JwtAuthenticationFilter` (перевірка `Bearer`-токена на решті запитів), а не окремий `@RestController` для логіну. Контракт нижче (розділ 4) переведено на цю схему |
| Один `JwtService` на все чи два сервіси? | Офіційний приклад розділяє генерацію і валідацію токена на `JwtService` (видає токен) і `JwtValidatorService` (парсить/перевіряє) — так само, як на слайдах 66-71. Контракт нижче розділено на два |
| `AntPathRequestMatcher` у `JwtLoginFilter` (є навіть в офіційному прикладі лектора) | Цей клас **прибрано** зі Spring Security 7.1.1 — `cannot find symbol` навіть у коді самого лектора проти поточних версій залежностей. Заміна на `PathPatternRequestMatcher.pathPattern(...)` **компілюється, але мовчки не працює** (`attemptAuthentication` просто не викликається — цей matcher потребує контексту розбору шляху з `DispatcherServlet`, якого ще нема на рівні фільтра безпеки). Перевірена робоча заміна — `RegexRequestMatcher.regexMatcher(HttpMethod.POST, "/api/v1/auth/login")` |
| `ObjectMapper` для ручного `ProblemDetail` (слайди 90-92) | У Spring Boot 4.1 автоконфігурований `ObjectMapper` застосунку — це вже Jackson 3 (`tools.jackson.databind.ObjectMapper`), а не класичний `com.fasterxml.jackson.databind.ObjectMapper` зі слайдів. Класичний Jackson сам по собі тягнеться лише транзитивно через `jjwt-jackson` і лише в `runtime`-області. **Але**: офіційний приклад додає `spring-boot-starter-jackson` і власний бін `JacksonConfig { ObjectMapper objectMapper() { return new ObjectMapper(); } }`, а в нашому проєкті вже є `springdoc-openapi` з №5 — вона сама тягне класичний `jackson-databind` у `compile`-області (Maven nearest-wins). Тобто класичний підхід лектора в нашому проєкті теж компілюється і працює — контракт нижче перейшов на нього для максимальної відповідності прикладу |
| `.setProperty("instance", ...)` у `ProblemDetail` (так само в офіційному прикладі лектора) | Дає **вкладений** `"properties":{"instance":"..."}`, а не плаский `"instance":"..."` top-level поле, якщо `ObjectMapper` — ручний (не автоконфігурований Boot'ом, як у нашому `JacksonConfig`). Правильно — викликати власний метод `ProblemDetail.setInstance(URI.create(...))`, він серіалізується пласким полем незалежно від того, який `ObjectMapper` активний. Це баг навіть у коді самого лектора — у контракті виправлено |
| `@WebMvcTest` + Spring Security (слайд 96) | У Boot 4.1 модульні тестові jar-и **не** підключають Spring Security до `MockMvc` автоматично. Треба самому зібрати `MockMvc`: `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()` у `@BeforeEach`, через `@Autowired WebApplicationContext` |
| `@MockitoBean` на самому класі `JwtAuthenticationFilter` | **Тиха помилка**: мок фільтра не викликає `filterChain.doFilter(...)`, увесь запит обривається, тест бачить `200` без жодного винятку. Мокати треба лише залежності фільтра (`JwtValidatorService`, `UserDetailsService`), а сам фільтр — імпортувати як є |
| `@Component`-фільтр (`JwtAuthenticationFilter`) у довільному `@WebMvcTest` | Автоматично підхоплюється в будь-якому `@WebMvcTest`-зрізі проєкту (на відміну від `@Service`/`@Repository`) і провалює створення контексту, якщо його залежності нема чим задовольнити. Кожен наявний `@WebMvcTest` (контролери з №2-№5) після злиття бази **зламається**, якщо не додати туди `@MockitoBean` на `JwtValidatorService` і `UserDetailsService` |
| `AuthenticationManager` як бін | У сучасному Spring Security не піднімається автоматично для `@Autowired`; треба явно `@Bean AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) { return cfg.getAuthenticationManager(); }` |
| Перевірка вручну (curl) одразу після "Started..." у логах | Spring Boot друкує рядок `Started XApplication in N seconds` **до** того, як виконає `ApplicationRunner`-и (`DemoUsers` тощо) — тільки після нього. Скрипт/curl, запущений одразу по цьому рядку, може «не бачити» ще не створених демо-користувачів і дати хибний `401`. Чекати варто на власний лог-маркер з самого `ApplicationRunner`, а не на "Started" |
| Повний сценарій: без токена → логін → `Bearer` → чужа роль, з усіма виправленнями вище | Усі чотири статуси (`401`/`200`(токен)/`200`/`403`) відтворені реальними запитами, обидва `ProblemDetail` (401 і 403) — плаский `instance`, без зайвого `"properties":null` (додано `setSerializationInclusion(NON_NULL)` у `JacksonConfig`, як і в контракті №2) |

---

## 3. Спільна частина (робить Людина 1 до старту) — пакет `common`, без підпакетів

Той самий принцип, що в №3-№5: крок, що зачіпає `common`, мусить відбутися першим і одним злиттям. Орієнтир — **2-2.5 години** (найважчий «базовий» крок з усіх контрактів, бо торкається авторизації кожного ендпоінта). Поки Людина 1 зайнята, Людина 2 і 3 читають лекцію й розділ 5 цього контракту — яке правило авторизації додати у свій модуль — але жодного файлу не чіпають.

| Крок | Що зробити |
|------|-----------|
| 1 | `pom.xml`: `spring-boot-starter-security`, `jjwt-api`/`jjwt-impl`/`jjwt-jackson` (версія `0.12.6`), `org.bouncycastle:bcprov-jdk18on` (без неї `Argon2PasswordEncoder` падає — розділ 2) |
| 2 | `AppRole` — enum `DONOR, VOLUNTEER, COORDINATOR, ADMIN` (ролі з ТЗ, розділ 3) |
| 3 | `AppUser` — `@Entity implements UserDetails`, поля `id (UUID)`, `username`, `password`, `role`, `enabled` |
| 4 | `UserRepository extends ListCrudRepository<AppUser, UUID>` з `findByUsername`, `existsByUsername` |
| 5 | `JpaUserDetailsService implements UserDetailsService` — `loadUserByUsername` кидає `UsernameNotFoundException`, якщо немає запису |
| 6 | `PasswordEncoderConfig` — бін `Argon2PasswordEncoder(16, 32, 1, 16384, 3)` (параметри зі слайда 32) + один `ApplicationRunner`, що виводить приклад хеша в лог при старті (R2) |
| 7 | `DemoUsers` — `ApplicationRunner`, що створює 4 демо-акаунти (по одному на роль) із фіксованими `UUID`, якщо їх ще нема — той самий принцип, що `DemoData` з №2 |
| 8 | `JacksonConfig` — бін класичного `com.fasterxml.jackson.databind.ObjectMapper` з `setSerializationInclusion(NON_NULL)` (розділ 2) |
| 9 | `JwtService` — генерація токена, і окремо `JwtValidatorService` — парсинг/перевірка (розділ 4.2) |
| 10 | `JwtAuthenticationFilter` — `OncePerRequestFilter`, читає `Authorization: Bearer`, валідує через `JwtValidatorService`, ставить `SecurityContextHolder` |
| 11 | `JwtLoginFilter extends UsernamePasswordAuthenticationFilter` — сам логін, `POST /api/v1/auth/login` (розділ 4.3) |
| 12 | `ProblemDetailAuthenticationEntryPoint` і `ProblemDetailAccessDeniedHandler` — `ProblemDetail` через `.setInstance(...)` (не `.setProperty("instance", ...)` — розділ 2), у тому самому форматі, що `GlobalExceptionHandler` з №2 (`type = https://api.foodrescue.local/errors/unauthorized` і `.../forbidden`) |
| 13 | `SecurityConfig` — `@EnableWebSecurity @EnableMethodSecurity`, `SecurityFilterChain` з двома фільтрами (розділ 4.1), `CorsConfigurationSource`, бін `AuthenticationManager` |
| 14 | `OpenApiConfig` з №5 — додати схему `BearerAuth`, щоб кнопка «Authorize» у Swagger UI працювала (слайд 85) |
| 15 | `postman/food-rescue-collection.json` з №5 — додати крок логіну на початок і заголовок `Authorization: Bearer {{accessToken}}` на решту запитів (розділ 6) — інакше колекція з №5 почне падати з 401 |
| 16 | README: скелет розділу «Безпека» (розділ 7) |

**Готово, коли:** `./mvnw test` зелений (з урахуванням правок з розділу 5.3 у наявних тестах); застосунок стартує; у логах видно приклад хеша Argon2id; `POST /api/v1/auth/login` з демо-акаунтом повертає токен; захищений ендпоінт без токена повертає `401` у форматі `ProblemDetail`.

### 3.1. Чому ролі саме такі

ТЗ (розділ 3) визначає чотири ролі: Донор, Волонтер, Координатор фонду, Адміністратор. Бізнес-кейси №2-№5 написані саме під ці ролі:

| Роль | Хто за ТЗ | Що робить у поточному API |
|------|-----------|---------------------------|
| `DONOR` | менеджер закладу | створює/редагує/скасовує/публікує лот |
| `VOLUNTEER` | кур'єр | резервує лот, виконує `pickup`/`delivery`/`confirmation` |
| `COORDINATOR` | диспетчер фонду | створює пункти призначення |
| `ADMIN` | технічний адміністратор | модерація лотів (`approval`) |

Отримувач (адресат) з ТЗ окремого акаунта в проєкті не має — підтвердження коду відбувається без прив'язки до ролі, як і зараз.

---

## 4. Технічні деталі бази (перевірено)

### 4.1. `SecurityConfig`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            ObjectMapper objectMapper,
            JwtAuthenticationFilter jwtFilter,
            ProblemDetailAuthenticationEntryPoint authEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {

        JwtLoginFilter jwtLoginFilter = new JwtLoginFilter(authenticationManager, jwtService, objectMapper, authEntryPoint);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAt(jwtLoginFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

`authorizeHttpRequests` навмисно **грубий**: лише «відкрито» (логін, документація) чи «потрібен будь-який валідний токен». Хто саме може викликати конкретний метод — вирішує `@PreAuthorize` на сервісі (розділ 5), а не URL-правило тут. Це єдиний спосіб, щоб троє людей могли додавати свої правила авторизації, не чіпаючи один спільний файл і не отримуючи конфліктів злиття.

`jwtAuthenticationFilterRegistration` зі `setEnabled(false)` — страхування від того, що Spring Boot сам ще раз зареєструє `@Component`-фільтр на рівні контейнера сервлетів, окремо від ланцюжка безпеки (так само робить і офіційний приклад лектора). Без цього біна помилки не буде (`OncePerRequestFilter` сам захищається від повторного виконання), але залишаємо для відповідності прикладу.

### 4.2. `JwtService`, `JwtValidatorService` і секрет

```java
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-minutes:15}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60 * 1000;
    }
    // generateToken, getExpirationMillis — як на слайдах 66-69, перевірено без змін
}

@Service
public class JwtValidatorService {

    private final SecretKey signingKey;

    public JwtValidatorService(@Value("${security.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    // extractAllClaims, extractUsername, isTokenValid — як на слайдах 70-71, перевірено без змін
}
```

Генерацію і валідацію розділено на два сервіси — так само, як на слайдах і в офіційному прикладі лектора (а не один `JwtService` на все).

```properties
security.jwt.secret=${JWT_SECRET:change-me-dev-only-food-rescue-secret-32-bytes-min}
security.jwt.expiration-minutes=15
```

Слайд 69 прямо називає типову помилку: «зберігати секретні ключі у відкритому вигляді у файлах конфігурації репозиторію». Значення за замовчуванням тут — явно позначений **dev-заповнювач** (і довший за мінімум 32 байти для HMAC-SHA, інакше `WeakKeyException`), а реальне значення для демонстрації викладачу можна передати змінною середовища `JWT_SECRET`, нічого не комітячи. Для самої здачі досить і заповнювача — це студентський проєкт, не прод.

### 4.3. `JwtLoginFilter`

Не окремий `@RestController`, а фільтр — підміняє стандартний `UsernamePasswordAuthenticationFilter` на шляху логіну (R6 — «фільтри», множина; так само в офіційному прикладі лектора):

```java
public class JwtLoginFilter extends UsernamePasswordAuthenticationFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtLoginFilter(AuthenticationManager authenticationManager, JwtService jwtService,
            ObjectMapper objectMapper, AuthenticationEntryPoint authenticationEntryPoint) {
        super(authenticationManager);
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.authenticationEntryPoint = authenticationEntryPoint;
        setRequiresAuthenticationRequestMatcher(
                RegexRequestMatcher.regexMatcher(HttpMethod.POST, "/api/v1/auth/login"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        var authRequest = UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.username(), loginRequest.password());
        return getAuthenticationManager().authenticate(authRequest);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain, Authentication authResult) throws IOException {
        UserDetails userDetails = (UserDetails) authResult.getPrincipal();
        String token = jwtService.generateToken(userDetails);
        AuthResponse authResponse = AuthResponse.of(token, jwtService.getExpirationMillis() / 1000);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), authResponse);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failed) throws IOException {
        authenticationEntryPoint.commence(request, response, failed);
    }
}
```

**Важливо (перевірено, розділ 2):** `setRequiresAuthenticationRequestMatcher` — не `AntPathRequestMatcher` (прибрано зі Spring Security 7.1.1, навіть у коді лектора не компілюється) і не `PathPatternRequestMatcher` (компілюється, але мовчки не спрацьовує на цьому рівні). Робочий варіант — `RegexRequestMatcher.regexMatcher(HttpMethod.POST, "/api/v1/auth/login")`.

Невірний пароль чи неіснуючий логін → `AuthenticationManager.authenticate` сам кидає `BadCredentialsException`/`UsernameNotFoundException`, їх ловить `unsuccessfulAuthentication` → той самий `ProblemDetailAuthenticationEntryPoint`, що й 401 на захищеному ресурсі (перевірено: обидва випадки дають однаковий формат `ProblemDetail`).

---

## 5. Авторизація по ролях і тести — кожен у своєму модулі

### 5.1. Правило для кожного мутуючого методу

Один рядок `@PreAuthorize` над методом сервісу — не над контролером (контролер лишається тонким, як вимагає R1 з №2):

| Хто | Де (метод) | Анотація |
|-----|------------|----------|
| Людина 1 (`lot`) | `LotService.create/update/cancel/publish` | `@PreAuthorize("hasRole('DONOR')")` |
| Людина 1 (`lot`) | `LotService.approve` | `@PreAuthorize("hasRole('ADMIN')")` |
| Людина 2 (`volunteer`) | `VolunteerService.reserve/cancelReservation` | `@PreAuthorize("hasRole('VOLUNTEER')")` |
| Людина 3 (`delivery`) | `DeliveryService.createDestinationPoint` | `@PreAuthorize("hasRole('COORDINATOR')")` |
| Людина 3 (`delivery`) | `DeliveryService.pickup/deliver/confirm` | `@PreAuthorize("hasRole('VOLUNTEER')")` |

Методи лише читання (`findAll`, `getById`, `getHistory` тощо) нічим не позначаємо — досить загального `anyRequest().authenticated()` з бази: будь-який залогінений користувач може читати.

### 5.2. Обов'язкове виправлення наявних `@WebMvcTest` (перевірено — без нього тести не збираються)

Як тільки база злита в `main`, у **кожному** наявному `@WebMvcTest` (з №2-№5, для своїх контролерів) треба додати три поля — інакше тест впаде ще на етапі підняття контексту (розділ 2):

```java
@MockitoBean
private JwtValidatorService jwtValidatorService;

@MockitoBean
private UserDetailsService userDetailsService;

@MockitoBean
private AuthenticationEntryPoint authenticationEntryPoint;
```

Більше нічого в цих тестах міняти не треба: без `@Import(SecurityConfig.class)` і без ручної збірки `MockMvc` вони й далі бачать ті самі статуси, що й раніше (перевірено).

### 5.3. Новий тест на авторизацію — по одному на людину

```java
@WebMvcTest(LotController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class,
         ProblemDetailAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class LotControllerSecurityTest {

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @MockitoBean JwtValidatorService jwtValidatorService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean LotService lotService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void create_WhenAnonymous_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/lots")...).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void create_WhenWrongRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/lots")...).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void create_WhenDonorRole_ShouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/v1/lots")...).andExpect(status().isCreated());
    }
}
```

Шаблон перевірений — три сценарії саме в такому вигляді пройшли на прототипі (розділ 2). `@Import` обов'язково включає `JwtAuthenticationFilter.class` поряд із `SecurityConfig.class` — інакше `HttpSecurity`-бін не піднімається в зрізі (перевірено).

---

## 6. Postman-колекція з №5 — оновити, інакше вона зламається

Колекція з контракту №5 після цього тижня працюватиме, лише якщо додати:

1. **Перший запит** `POST /api/v1/auth/login` з тілом демо-донора; у `Tests` зберегти токен: `pm.environment.set("accessToken", pm.response.json().accessToken);`
2. На **кожному** наступному запиті — заголовок `Authorization: Bearer {{accessToken}}`.

Якщо потрібен інший актор (наприклад, резервування — волонтер), додати другий запит логіну (`POST /api/v1/auth/login` з волонтерським акаунтом) перед відповідним кроком сценарію і перезаписати `accessToken`.

---

## 7. README.md — що додаємо

Один розділ «Безпека», кожен пише 2-3 речення про свою частину:

1. Яка роль потрібна для яких дій (таблиця з розділу 5.1).
2. Як отримати токен: `POST /api/v1/auth/login`, тіло, список демо-логінів/паролів для перевірки на захисті.
3. Чому `SessionCreationPolicy.STATELESS` і чому вимкнений CSRF (одне речення — API без браузерних сесій).

---

## 8. Готово, коли (як перевірятиме викладач)

**1. Тести**
```
./mvnw test
```
`BUILD SUCCESS`, включно з новими тестами авторизації (розділ 5.3) і виправленими наявними `@WebMvcTest` (розділ 5.2).

**2. Сценарій зі слайда 112 — рівно так, як вимагає викладач**
```
curl -i http://localhost:8080/api/v1/lots
```
→ `401`, тіло — `ProblemDetail`.
```
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"username":"donor1","password":"donorpass1"}'
```
→ `200`, тіло з `accessToken`.
```
curl -i http://localhost:8080/api/v1/lots -H "Authorization: Bearer <accessToken>"
```
→ `200`.
```
curl -i -X POST http://localhost:8080/api/v1/destination-points -H "Authorization: Bearer <donor-token>" ...
```
→ `403`, тіло — `ProblemDetail` (донор не має права створювати пункти призначення).

**3. Хеш пароля в логах**
```
./mvnw spring-boot:run | grep Argon2id
```
Один рядок із хешем у форматі `$argon2id$v=19$m=16384,t=3,p=1$...`.

**4. CORS**
```
curl -i -X OPTIONS http://localhost:8080/api/v1/lots \
  -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: GET"
```
→ `200` із заголовком `Access-Control-Allow-Origin`.

**5. Postman**
```
npx newman run postman/food-rescue-collection.json -e postman/food-rescue-environment.json
```
`failed: 0` (з урахуванням кроку логіну з розділу 6).

**6. Архів і посилання (R11)**
На DistEdu — і архів, і посилання на репозиторій, не лише архів, як у попередніх тижнях.

---

## 9. Чого не робимо

- `Refresh Token` і його ротацію (слайди 61-63) — лекція згадує це як продакшн-патерн, не як вимогу завдання; тільки короткоживучий `access token`;
- `Scoped Values` (слайди 15-19) — для Java 25/віртуальних потоків; наш застосунок працює на звичайних потоках Tomcat, `SecurityContextHolder` з режимом за замовчуванням (`ThreadLocal`) не створює жодної проблеми при нашому навантаженні;
- `Session Fixation`/`HttpSessionEventPublisher`/обмеження одночасних сесій (слайди 48-51) — це про сесійну модель, а наш груповий API безсесійний (`STATELESS`). Офіційний приклад лектора має такий другий `SecurityFilterChain` (`@Order(1)`, окремо на `/session/**`) — це явно матеріал іншої (пізнішої) лекції, у нашому прикладі він не підключений до жодного ендпоінта з ТЗ і в контракт не йде;
- `SecurityScopeRunner`/`ScopedValue`-демонстрацію з прикладу лектора — перевірено (`grep`), що цей клас ніде не підключений до реального флоу автентифікації, мертвий демо-код; не копіюємо;
- `OAuth 2.0`, `PKCE`, `Passkeys`, `SSO` (слайди 99-103) — лектор прямо назвав це «перспективами розвитку», не завданням;
- `GraalVM Native Image` (слайд 104) — не наш стек;
- маскування токена в логах окремим перетворювачем Logback — простіше правило (розділ 10): токен ніде не логуємо, крім самого значення в тілі відповіді `/login`;
- нових бізнес-правил чи ендпоінтів — усе з контрактів №2-№5 лишається як є, окрім `@PreAuthorize` і правок тестів.

---

## 10. Ризики

| Ризик | Що робити |
|-------|-----------|
| Усі наявні `@WebMvcTest` червоніють одразу після злиття бази | Очікувано (розділ 2, 5.2) — додати три `@MockitoBean` в кожен (розділ 5.2), нічого більше не міняти |
| Postman-колекція з №5 раптом усюди 401 | Додати крок логіну на початок (розділ 6) — без цього й раніше зелена колекція тепер провалиться |
| `Argon2PasswordEncoder` падає при старті з `NoClassDefFoundError` | Забули `bcprov-jdk18on` у `pom.xml` (розділ 2, 3 крок 1) |
| У логах чи в токені — пароль, персональні дані | Заборонено (слайд 60, 106); у payload JWT — лише `username` і ролі |
| Хтось скопіював `AntPathRequestMatcher` зі слайда чи з прикладу лектора — помилка компіляції | Використовувати `RegexRequestMatcher.regexMatcher(HttpMethod.POST, "...")` (розділ 2, 4.3); `PathPatternRequestMatcher` компілюється, але мовчки ламає логін |
| Ручна перевірка curl одразу після рядка `Started...` у логах дає хибний 401 | `ApplicationRunner` (демо-користувачі) виконується **після** цього рядка (розділ 2) — почекати секунду-дві або власний лог-маркер |
| `ProblemDetail` повертає `"instance":null` і вкладений `"properties":{"instance":...}` | Використати `.setInstance(URI.create(...))`, не `.setProperty("instance", ...)` (розділ 2, 4.1 — це баг навіть у прикладі лектора) |

---

## 11. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| Зараз | разом | Домовитися, хто Людина 1, 2, 3 (як і в №3-№5). Прочитати розділи 3-6. Перевірити на distedu, чи завдання вже відкрите (приклад лектора вже враховано в контракті) |
| До старту (~2-2.5 год) | Людина 1 | База: розділ 3, одним злиттям у `main`. Людина 2 і 3 читають і готують, яке `@PreAuthorize`-правило й який тест авторизації будуть їхніми |
| Основна частина (~2 год) | кожен окремо | Виправити свої наявні `@WebMvcTest` (розділ 5.2), додати `@PreAuthorize` у своєму сервісі (розділ 5.1), написати новий тест авторизації (розділ 5.3), розділ README |
| Перед здачею (~30 хв) | разом | Злити все в `main`; оновити Postman-колекцію (розділ 6); пройти розділ 8 повністю на розпакованому архіві |
| Здача | разом | Архів **і посилання на репозиторій** на distedu (R11). Кожен готовий пояснити будь-яку частину |
