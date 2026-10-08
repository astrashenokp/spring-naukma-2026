# Food Rescue — контракт №6: Spring Security, JWT

> **Актуальний контракт** (останній). Попередні №3-№5 лишаються чинними для своїх частин, але цей документ має пріоритет там, де вони розходяться.

Групове завдання лекції 6 (Spring Security). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №5 і злитий у `main`.** Усе з №2-№5 лишається. Це завдання додає автентифікацію й авторизацію над готовим API — ендпоінти, DTO й бізнес-логіка не змінюються, окрім одного рядка `@PreAuthorize` на кожному методі, що міняє дані.

> Лектор сам сказав наприкінці лекції: «завдання поки що не буде відкрите, я внесу деякі правки і так само додам приклад». Перш ніж починати — перевірити на distedu, чи з'явилася уточнена версія й приклад. Нижче — те, що є на слайдах і підтверджено на прототипі; якщо уточнена версія відрізнятиметься в дрібницях, структура (JWT + ролі + `ProblemDetail`) зміниться мало.

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

Усе нижче відтворено на прототипі (Boot 4.1.1, Java 25, JPA + H2, Spring Security 7.1.1) перед тим, як потрапити в контракт. Три пункти — речі, про які лекція **не попередила**, і без яких код не збирається або падає в рантаймі.

| Що | Результат |
|----|-----------|
| `Argon2PasswordEncoder` (приклад зі слайда 32) | **Падає в рантаймі** з `NoClassDefFoundError: org.bouncycastle...`, якщо в `pom.xml` немає окремо доданого `org.bouncycastle:bcprov-jdk18on`. Spring Security сам цю залежність не тягне |
| `ObjectMapper` для ручного `ProblemDetail` (приклад зі слайдів 90-92) | У Spring Boot **4.1** власний `ObjectMapper`, яким реально користується застосунок, — це вже **Jackson 3** (`tools.jackson.databind.ObjectMapper`), а не класичний `com.fasterxml.jackson.databind.ObjectMapper`, який показано на слайдах. Класичний Jackson є в проєкті лише транзитивно (через `jjwt-jackson`) і лише в `runtime`-області — в коді він не компілюється. Імпортувати треба `tools.jackson.databind.ObjectMapper` |
| `@WebMvcTest` + Spring Security (приклад зі слайда 96) | У Boot 4.1 модульні тестові jar-и **не** підключають Spring Security до `MockMvc` автоматично (на відміну від того, що показано на слайді). Треба самому зібрати `MockMvc`: `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()` у `@BeforeEach`, через `@Autowired WebApplicationContext`, а не покладатися на автоін'єктований `MockMvc` |
| `@MockitoBean` на самому класі `JwtAuthenticationFilter` | **Тиха помилка**: мок фільтра не викликає `filterChain.doFilter(...)`, увесь запит обривається, і тест бачить `200` замість очікуваного статусу — без жодного винятку. Мокати треба лише залежності фільтра (`JwtService`, `UserDetailsService`), а сам фільтр — імпортувати як є |
| `@Component`-фільтр (`JwtAuthenticationFilter`) у довільному `@WebMvcTest` | Такий фільтр **автоматично підхоплюється** в будь-якому `@WebMvcTest`-зрізі проєкту (на відміну від `@Service`/`@Repository`) і провалює створення контексту, якщо його залежності (`JwtService`, `UserDetailsService`) нема чим задовольнити. Кожен наявний `@WebMvcTest` у проєкті (контролери з №2-№5) після цього злиття **зламається**, якщо не додати туди `@MockitoBean JwtService jwtService` і `@MockitoBean UserDetailsService userDetailsService` |
| Той самий `@WebMvcTest` **без** `@Import(SecurityConfig.class)` і без ручного `springSecurity()` | Якщо додати лише два `@MockitoBean` вище (без інших змін), наявний тест далі бачить **200**, як і раніше — повноцінна перевірка авторизації (401/403) вмикається лише там, де її явно імпортують |
| `AuthenticationManager` як бін | У сучасному Spring Security він **не** піднімається автоматично для `@Autowired`; треба явно `@Bean AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) { return cfg.getAuthenticationManager(); }` |
| Повний сценарій: без токена → логін → `Bearer` → чужа роль | Усі чотири статуси (`401`/`200`(токен)/`200`/`403`) відтворені реальними запитами, обидва `ProblemDetail` (401 і 403) мають очікувану форму (`type`/`title`/`detail`/`status`/`instance`) |

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
| 8 | `JwtService` — генерація й валідація токена (розділ 4.2) |
| 9 | `JwtAuthenticationFilter` — `OncePerRequestFilter`, читає `Authorization: Bearer`, валідує, ставить `SecurityContextHolder` |
| 10 | `ProblemDetailAuthenticationEntryPoint` і `ProblemDetailAccessDeniedHandler` — `ProblemDetail` у тому самому форматі, що `GlobalExceptionHandler` з №2 (`type = https://api.foodrescue.local/errors/unauthorized` і `.../forbidden`) |
| 11 | `SecurityConfig` — `@EnableWebSecurity @EnableMethodSecurity`, `SecurityFilterChain` (розділ 4.1), `CorsConfigurationSource`, бін `AuthenticationManager` |
| 12 | `AuthController` — `POST /api/v1/auth/login` (розділ 4.3) |
| 13 | `OpenApiConfig` з №5 — додати схему `BearerAuth`, щоб кнопка «Authorize» у Swagger UI працювала (слайд 85) |
| 14 | `postman/food-rescue-collection.json` з №5 — додати крок логіну на початок і заголовок `Authorization: Bearer {{accessToken}}` на решту запитів (розділ 6) — інакше колекція з №5 почне падати з 401 |
| 15 | README: скелет розділу «Безпека» (розділ 7) |

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
            JwtAuthenticationFilter jwtFilter,
            ProblemDetailAuthenticationEntryPoint authEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
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
                .build();
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

### 4.2. `JwtService` і секрет

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
    // generateToken / extractUsername / isTokenValid — як на слайдах 66-71, перевірено без змін
}
```

```properties
security.jwt.secret=${JWT_SECRET:change-me-dev-only-food-rescue-secret-32-bytes-min}
security.jwt.expiration-minutes=15
```

Слайд 69 прямо називає типову помилку: «зберігати секретні ключі у відкритому вигляді у файлах конфігурації репозиторію». Значення за замовчуванням тут — явно позначений **dev-заповнювач** (і довший за мінімум 32 байти для HMAC-SHA, інакше `WeakKeyException`), а реальне значення для демонстрації викладачу можна передати змінною середовища `JWT_SECRET`, нічого не комітячи. Для самої здачі досить і заповнювача — це студентський проєкт, не прод.

### 4.3. `AuthController`

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails);
        return AuthResponse.of(token, jwtService.getExpirationMillis() / 1000);
    }
}
```

Невірний пароль чи неіснуючий логін → `AuthenticationManager.authenticate` сам кидає `BadCredentialsException`/`UsernameNotFoundException`; їх ловить `ProblemDetailAuthenticationEntryPoint` так само, як і 401 на захищеному ресурсі (перевірено: обидва випадки дають однаковий формат `ProblemDetail`).

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

Як тільки база злита в `main`, у **кожному** наявному `@WebMvcTest` (з №2-№5, для своїх контролерів) треба додати два рядки — інакше тест впаде ще на етапі підняття контексту (розділ 2):

```java
@MockitoBean
private JwtService jwtService;

@MockitoBean
private UserDetailsService userDetailsService;
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

    @MockitoBean JwtService jwtService;
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
- `Session Fixation`/`HttpSessionEventPublisher`/обмеження одночасних сесій (слайди 48-51) — це про сесійну модель, а наш груповий API безсесійний (`STATELESS`);
- `OAuth 2.0`, `PKCE`, `Passkeys`, `SSO` (слайди 99-103) — лектор прямо назвав це «перспективами розвитку», не завданням;
- `GraalVM Native Image` (слайд 104) — не наш стек;
- маскування токена в логах окремим перетворювачем Logback — простіше правило (розділ 10): токен ніде не логуємо, крім самого значення в тілі відповіді `/login`;
- нових бізнес-правил чи ендпоінтів — усе з контрактів №2-№5 лишається як є, окрім `@PreAuthorize` і правок тестів.

---

## 10. Ризики

| Ризик | Що робити |
|-------|-----------|
| Усі наявні `@WebMvcTest` червоніють одразу після злиття бази | Очікувано (розділ 2, 5.2) — додати два `@MockitoBean` в кожен, нічого більше не міняти |
| Postman-колекція з №5 раптом усюди 401 | Додати крок логіну на початок (розділ 6) — без цього й раніше зелена колекція тепер провалиться |
| `Argon2PasswordEncoder` падає при старті з `NoClassDefFoundError` | Забули `bcprov-jdk18on` у `pom.xml` (розділ 2, 3 крок 1) |
| У логах чи в токені — пароль, персональні дані | Заборонено (слайд 60, 106); у payload JWT — лише `username` і ролі |
| Лектор відкриє уточнену версію завдання з прикладом | Перевірити на distedu до старту; структура (JWT + ролі + `ProblemDetail`) малоймовірно зміниться, бо збігається зі слайдами |
| Хтось скопіював приклад `ObjectMapper` зі слайда й отримав помилку компіляції | Імпортувати `tools.jackson.databind.ObjectMapper`, не `com.fasterxml...` (розділ 2) |

---

## 11. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| Зараз | разом | Домовитися, хто Людина 1, 2, 3 (як і в №3-№5). Прочитати розділи 3-6. Перевірити на distedu, чи завдання вже відкрите й чи є приклад від лектора |
| До старту (~2-2.5 год) | Людина 1 | База: розділ 3, одним злиттям у `main`. Людина 2 і 3 читають і готують, яке `@PreAuthorize`-правило й який тест авторизації будуть їхніми |
| Основна частина (~2 год) | кожен окремо | Виправити свої наявні `@WebMvcTest` (розділ 5.2), додати `@PreAuthorize` у своєму сервісі (розділ 5.1), написати новий тест авторизації (розділ 5.3), розділ README |
| Перед здачею (~30 хв) | разом | Злити все в `main`; оновити Postman-колекцію (розділ 6); пройти розділ 8 повністю на розпакованому архіві |
| Здача | разом | Архів **і посилання на репозиторій** на distedu (R11). Кожен готовий пояснити будь-яку частину |
