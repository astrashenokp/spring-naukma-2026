# Food Rescue — контракт №9: розгортання застосунку

> **Актуальний контракт** (останній). Попередні №3-№8 лишаються чинними для своїх частин, але цей документ має пріоритет там, де вони розходяться.

Групове завдання лекції 9 (розгортання). Тут записано **лише те, що вимагає викладач**, і те, що технічно потрібно, щоб це працювало. Усе, що не згадане нижче, не робимо.

**Вихідна точка — код, зроблений за контрактом №8 і злитий у `main`.** Це завдання майже не міняє код: воно про запуск готового застосунку в цільовому оточенні. У `src` змінюється лише `application.properties` (два рядки); усе інше — нові файли в корені репозиторію.

> Лектор сказав, що завдання викладе ввечері 28.10 «так само, вони не змінюються» (тобто слайди 52-53 і є текст завдання). **Дедлайн групового в транскрипті не названо.** До старту уточнити на distedu. Попередні дедлайни: контракт 8 — середа, 28.10.2026, 23:59.

---

## 1. Що вимагає викладач

| # | Вимога | Звідки |
|---|--------|--------|
| G1 | Підготувати командний застосунок до розгортання в цільовому оточенні: на **VPS через юніт systemd** або в **ізольованому Docker-середовищі** | слайд 52, п. 1 |
| G2 | Автоматичний запуск сервісу, ізоляція прав користувача, конфігураційні параметри — через змінні середовища | слайд 52, п. 2 |
| G3 | Політика безпечного завершення `server.shutdown=graceful` | слайд 52, п. 3 |
| G4 | Зовнішня мережева доступність розгорнутого застосунку з публічної мережі Інтернет | слайд 53, п. 1 |
| G5 | Тестування зовнішнього доступу: переконатися, що вхідні запити успішно обробляються | слайд 53, п. 2 |
| G6 | Короткий звіт про обрану архітектуру розгортання, з файлами налаштувань; результати — на DistEdu | слайд 53, п. 3 |

Уточнення лектора усно (останній блок лекції):
- «Якщо ви працюєте з Docker, можете брати Docker. Якщо ні — пропоную VPS; треба налаштувати процес systemd, який запускатиме застосунок, підтримуватиме graceful shutdown і щоб був доступ (домен від провайдера або IP-адреса)»;
- VPS-рішення зазвичай мають безкоштовний пробний період на тиждень чи місяць — цього вистачить; якщо не вдасться знайти, лектор допоможе підібрати («будь-який, який дає тріал, підійде»);
- на VPS: Linux, на ньому Java, systemd і застосунок; з'єднання, як правило, по SSH (різні провайдери — різні нюанси).

### 1.1. Зауваження й критерії викладача з №8 діють і тут

Усе з розділів 1.1-1.3 контракту №8 (зауваження про CSRF, `{noop}`, `X-Trace-Id`; критерії чистоти коду, UI, функціоналу, шарів, тестів) лишається чинним. Для розгортання це означає: **жодних секретів у репозиторії** (критерій 1) — тому нижче всі паролі й ключі передаються змінними середовища (G2), а файл із ними не комітиться.

---

## 2. Що перевірено на робочому прикладі

Усе нижче відтворено на прототипі (Boot 4.1.1, Java 25, JPA + H2, Spring Modulith, два ланцюги безпеки з №7, JWT з №6; Docker Desktop, образ `eclipse-temurin:25-jre`) перед тим, як потрапити в контракт.

| Що | Результат |
|----|-----------|
| Збірка | `./mvnw -DskipTests package` дає самодостатній `target/<artifactId>-<version>.jar` (~80 МБ); `java -jar` запускає його без жодного іншого встановлення |
| Багатоетапний `Dockerfile`, що збирає jar **усередині** Docker | Працює з архіву без `target` (`mvnw`, `.mvn`, `pom.xml`, `src`): перша збірка ~1,5 хв (завантаження залежностей). `mvnw` має бути з кінцями рядків **LF** (його захищає `.gitattributes` зі Spring Initializr: не втратити файл при переносі), інакше в Linux-образі `./mvnw` не запуститься |
| `.dockerignore` | Якщо в одноетапному `Dockerfile` (`COPY target/*.jar`) виключити `target`, збірка падає `"/target/...jar": not found`. У багатоетапному — `target` у `.dockerignore` безпечний і потрібен (щоб не копіювати старий jar) |
| Процес не від root (G2) | `RUN groupadd --system app && useradd --system ... app` + `USER app`: `docker exec ... id -un` → `app`, власник PID 1 — `app` |
| Конфігурація змінними (G2) | `JWT_SECRET` перевизначає `security.jwt.secret` (є `${JWT_SECRET:...}` з №6). Паролі демо-користувачів із №8 (`foodrescue.demo.passwords.<user>`) перевизначаються **змінними з підкресленнями у верхньому регістрі**: `FOODRESCUE_DEMO_PASSWORDS_DONOR1=...` (relaxed binding працює й для ключів `Map`). Перевірено входом: новий пароль → `200`, старий із `application.properties` → `401` |
| Graceful shutdown (G3) | У Boot 4.1 він уже **типово** `graceful` (у журналі `Commencing graceful shutdown. Waiting for active requests to complete`, `Graceful shutdown complete` навіть без властивості), але завдання вимагає задати його явно — задаємо в `application.properties` |
| **`docker stop` без `-t` обриває запит** | У цьому середовищі `docker stop` ... **вбив контейнер за ~3 с, код 137**, запит «у польоті» (повільний ендпоінт на 6 с) **обірвався** (`HTTP 000`). З `docker stop -t 30` — код **143**, запит завершився `200`, у журналі `Graceful shutdown complete`. Тому в `compose.yaml` **обов'язковий `stop_grace_period`**, більший за `spring.lifecycle.timeout-per-shutdown-phase` (слайд 46) |
| `docker compose stop` | З `stop_grace_period: 40s` і `timeout-per-shutdown-phase=30s` запит «у польоті» дочекався завершення, код **143**, `HTTP 200` |
| Автозапуск (G2) | `restart: unless-stopped`: після завершення процесу зсередини контейнера (`kill -TERM 1`) Docker перезапустив контейнер (`RestartCount=1`), API знову відповідає. **Увага:** контейнер, зупинений вручну чи через `docker kill`, **не** перезапускається — це не помилка політики |
| Юніт systemd (G1, альтернатива) | Юніт зі слайда 21, адаптований під проєкт, пройшов `systemd-analyze verify` у Linux-контейнері (Ubuntu 24.04) без помилок. Запускати його як сервіс я не міг (Windows-хост) — це перевіряється на VPS, розділ 4.3 |
| `SuccessExitStatus=143` | Код 143 (SIGTERM) — штатний для зупинки; без цього рядка systemd вважатиме зупинку збоєм (слайди 45) |
| H2 у контейнері | Застосунок на H2 в пам'яті (як у №4) стартує й обслуговує запити; **дані зникають при перезапуску** — для демонстрації це припустимо |
| Зовнішній доступ (G4, G5) | **НЕ перевірено.** У мене немає ні VPS, ні облікового запису Ngrok/Cloudflare. Розділ 4.4 — інструкція, яку ви перевіряєте на своїй машині. Це головний ризик завдання — починати з нього |

---

## 3. Спільна частина (робить Людина 1 до старту)

Той самий принцип, що в №3-№8. Розгортання — не бізнес-кейс, тому розділити «по модулях» не можна; роботу ділимо **за артефактами** (розділ 5). Орієнтир бази — **1 година**.

| Крок | Що зробити |
|------|-----------|
| 1 | `application.properties`: додати `server.shutdown=graceful` і `spring.lifecycle.timeout-per-shutdown-phase=30s` (G3). Більше нічого в `src` не міняти |
| 2 | Створити в корені `Dockerfile` і `.dockerignore` (розділ 4.1) |
| 3 | Створити `compose.yaml` і `deploy.env.example` (розділ 4.2) |
| 4 | У `.gitignore` додати `deploy.env` (справжні секрети **ніколи** не комітимо) |
| 5 | `./mvnw test` зелений, `docker compose up --build` піднімає застосунок (розділ 8, п. 1-2) |
| 6 | README: скелет розділу «Розгортання» (розділ 7) |

**Готово, коли:** `docker compose up -d --build` піднімає застосунок; вхід `POST /api/v1/auth/login` паролем із `deploy.env` → `200`; `docker compose stop` завершується кодом 143 і в журналі є `Graceful shutdown complete`.

---

## 4. Технічні деталі (перевірено)

### 4.1. `Dockerfile` і `.dockerignore`

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /opt/foodrescue
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```
target
deploy.env
.git
```

`-DskipTests` у збірці образу навмисно: тести з Testcontainers (№8) потребують Docker усередині збірки; їх ганяємо окремо `./mvnw verify` (розділ 8, п. 1).

### 4.2. `compose.yaml` і `deploy.env.example`

```yaml
services:
  app:
    build: .
    image: foodrescue:latest
    env_file: deploy.env
    ports:
      - "8080:8080"
    restart: unless-stopped
    stop_grace_period: 40s
```

`stop_grace_period` (40 с) **більший** за `timeout-per-shutdown-phase` (30 с): інакше Docker вб'є контейнер раніше за Spring (розділ 2).

`deploy.env.example` лежить у репозиторії як шаблон **без справжніх значень**; кожен копіює його в `deploy.env` (той у `.gitignore`) і підставляє свої:

```
JWT_SECRET=<випадковий рядок щонайменше 32 байти>
FOODRESCUE_DEMO_PASSWORDS_DONOR1=<пароль>
FOODRESCUE_DEMO_PASSWORDS_VOLUNTEER1=<пароль>
FOODRESCUE_DEMO_PASSWORDS_COORDINATOR1=<пароль>
FOODRESCUE_DEMO_PASSWORDS_ADMIN1=<пароль>
```

По одному рядку на кожен демо-акаунт із №6/№8 (імена — як у `application.properties`). JWT-секрет коротший за 32 байти дає `WeakKeyException` при старті (№6).

Запуск: `docker compose up -d --build`; зупинка: `docker compose stop` (**не** `docker kill` і не `docker stop` без `-t`).

### 4.3. Альтернатива: VPS і юніт systemd

Якщо Docker на вашій машині не працює (лектор прямо дозволив VPS). Юніт (перевірено `systemd-analyze verify`):

```ini
[Unit]
Description=Food Rescue Service
After=network.target

[Service]
Type=simple
User=foodrescue
Group=foodrescue
WorkingDirectory=/opt/foodrescue
EnvironmentFile=/etc/foodrescue/foodrescue.env
ExecStart=/usr/bin/java -jar /opt/foodrescue/foodrescue.jar
SuccessExitStatus=143
TimeoutStopSec=40
Restart=always
RestartSec=10
ProtectSystem=full
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

Кроки на VPS (Linux, не root для застосунку): встановити Java 25; створити системного користувача `foodrescue` і каталоги `/opt/foodrescue`, `/etc/foodrescue`; покласти jar у `/opt/foodrescue/foodrescue.jar`; файл змінних `/etc/foodrescue/foodrescue.env` (ті самі ключі, що в `deploy.env`) з правами `600`, власник `foodrescue`; юніт — у `/etc/systemd/system/foodrescue.service`; далі `systemctl daemon-reload`, `systemctl enable --now foodrescue`, `journalctl -u foodrescue -f`. `User=foodrescue` забороняє запуск від root, `ProtectSystem=full` і `PrivateTmp=true` ізолюють процес, `EnvironmentFile` передає секрети (слайд 22). `TimeoutStopSec=40` — більше за 30 с Spring (слайд 46). **На VPS я це не запускала** — пройти й виправити за повідомленнями `systemctl status`.

### 4.4. Зовнішній доступ (G4, G5)

**Не перевірено мною — це перше, що треба зробити.** Варіанти:

| Варіант | Як | Коментар |
|---------|----|----------|
| Тунель до Docker-застосунку (найшвидше) | Ngrok: `ngrok http 8080` (потрібен безкоштовний акаунт і `authtoken`); або Cloudflare: `cloudflared tunnel --url http://localhost:8080` (слайд 7) | Адреса публічна, але живе, поки працює тунель і ваш комп'ютер. Для «зовнішнього доступу з Інтернету» (G4) і звіту підходить, але не є «серверним» розгортанням — у звіті чесно вказати |
| VPS із публічним IP (рекомендований лектором для systemd) | Пробний VPS із Linux; у фаєрволі провайдера відкрити порт 8080 (або 80/443 за зворотним проксі) | Доступ за IP (лектор допускає) або доменом від провайдера. Відкритий порт Tomcat 8080 у відкритий Інтернет — антипатерн (слайд 47), тож у звіті пояснити, що це демонстрація |

**Що важливо для тесту G5:** безпека з №6/№7 лишається; публічну адресу перевіряємо запитами, що реально відпрацьовують:
```
curl -i https://<публічна-адреса>/api/v1/lots                    → 401 (ProblemDetail)
curl -X POST https://<публічна-адреса>/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"donor1","password":"<з deploy.env>"}'   → 200 + accessToken
curl -i https://<публічна-адреса>/api/v1/lots -H "Authorization: Bearer <accessToken>"   → 200
```
і сторінка входу `https://<публічна-адреса>/login` у браузері. Запит має бути **з іншої мережі** (наприклад, телефон через мобільний Інтернет), не з тієї самої машини.

Через тунель адреса починається з `https://`, а застосунок бачить `http` і `127.0.0.1`: редіректи форми входу можуть вести на `http://`. Лектор для цього називає `server.forward-headers-strategy=native` (слайд 33), але це **не** вимога завдання й мною не перевірялося — додавати лише якщо побачите проблему, і тоді перевірити.

---

## 5. Розподіл по людях

Усі три роблять паралельно, ніхто нікого не чекає (база — розділ 3).

| | Людина 1 | Людина 2 | Людина 3 |
|---|---|---|---|
| **Артефакт** | База (розділ 3): `application.properties`, `Dockerfile`, `compose.yaml`, `deploy.env.example`, `.gitignore`. Запуск у Docker | **Зовнішній доступ (G4, G5)**: тунель або VPS (розділ 4.4), тест запитів із зовнішньої мережі, скриншоти | **Юніт systemd і VPS-альтернатива (розділ 4.3)**: перевірити юніт у Linux (VPS або WSL/віртуальна машина з systemd), `systemctl enable`, `journalctl`; **graceful shutdown**: довести з повільним запитом (розділ 5.1) |
| **Звіт** | Розділ 6, пп. 1, 2 (архітектура, файли налаштувань) | Розділ 6, п. 3 (зовнішній доступ) | Розділ 6, пп. 4, 5 (автозапуск, graceful) |
| **README** | Розділ 7, п. 1 | Розділ 7, п. 2 | Розділ 7, п. 3 |

Якщо в команди немає VPS і systemd, Людина 3 дає у звіті юніт з розділу 4.3 як **підготовлену, не запущену** альтернативу (з чесним зазначенням) і додатково перевіряє `systemd-analyze verify` у Linux-контейнері; основне розгортання — Docker.

### 5.1. Доказ graceful shutdown

Свідчення, які бере звіт (G3):
1. `application.properties` містить `server.shutdown=graceful` і `spring.lifecycle.timeout-per-shutdown-phase=30s`;
2. `docker compose stop` → у `docker compose logs app` рядки `Commencing graceful shutdown. Waiting for active requests to complete` і `Graceful shutdown complete`; `docker inspect <контейнер> --format '{{.State.ExitCode}}'` → `143`;
3. (за бажанням) запит «у польоті»: поки йде довгий запит, віддати `docker compose stop` — запит завершується `200`. Довгий ендпоінт у проєкті є не в усіх: тимчасовий `Thread.sleep(6000)` лише на час демонстрації **в коді не лишаємо** (критерій «мертвий код»).

---

## 6. Звіт (G6)

Один файл `report-deploy.md` у корені репозиторію й в архіві. Складають по частинах (розділ 5), збирає Людина 1:

1. **Обрана архітектура:** що (Docker Compose / VPS + systemd), де (машина, провайдер), чому саме так — одне-два речення; схема «клієнт → (тунель або публічний IP) → контейнер → застосунок → H2».
2. **Файли налаштувань:** `Dockerfile`, `compose.yaml`, `application.properties` (властивості graceful), `deploy.env.example`, за потреби юніт systemd — вставлені в звіт або з посиланням на файл. Справжні секрети **не** вставляти.
3. **Зовнішній доступ:** публічна адреса (на момент здачі), скриншоти запитів із зовнішньої мережі з кодами відповідей (`401`, `200`, `200`), скриншот сторінки входу.
4. **Автозапуск і ізоляція:** `restart: unless-stopped` (або `systemctl enable`), користувач `app`/`foodrescue` замість root (`docker exec ... id -un`), конфігурація змінними оточення.
5. **Graceful shutdown:** докази з розділу 5.1.
6. **Обмеження** (одне-два речення): H2 в пам'яті — дані зникають при перезапуску; якщо це тунель — публічна адреса тимчасова.

---

## 7. README.md — що додаємо

Розділ «Розгортання», кожен 2-3 речення про свою частину:

1. Як запустити в Docker: `cp deploy.env.example deploy.env`, підставити значення, `docker compose up -d --build`; як зупинити (`docker compose stop`).
2. Як перевірити зовнішній доступ: команди з розділу 4.4.
3. Як розгорнути на VPS через systemd (розділ 4.3) і де лежить звіт.

---

## 8. Готово, коли (як перевірятиме викладач)

**1. Тести**
```
./mvnw verify
```
`BUILD SUCCESS` (як у №8; потребує Docker Desktop).

**2. Запуск у Docker із чистого архіву**
```
cp deploy.env.example deploy.env      # підставити значення
docker compose up -d --build
```
Контейнер працює; процес не від root; вхід паролем із `deploy.env` → `200`, старим паролем із `application.properties` → `401`.

**3. Graceful shutdown**
```
docker compose stop
docker compose logs app | grep -i graceful
```
Видно `Commencing graceful shutdown...` і `Graceful shutdown complete`; код виходу `143`.

**4. Автозапуск**
`docker inspect <контейнер> --format '{{.HostConfig.RestartPolicy.Name}}'` → `unless-stopped` (або `systemctl is-enabled foodrescue` → `enabled`).

**5. Зовнішній доступ (G4, G5)**
Запити з розділу 4.4 із зовнішньої мережі: `401`, `200`, `200`; сторінка входу відкривається.

**6. Звіт і архів (G6)**
`report-deploy.md` у корені; архів без `target`; у ньому **немає** `deploy.env` (лише `deploy.env.example`), а `grep -rn "<значення пароля>" .` по репозиторію не знаходить справжніх секретів.

**7. Критерії викладача (№8, розділ 1.2)**
Нічого з нових файлів не порушує чистоту коду: жодних закоментованих блоків у `Dockerfile`/`compose.yaml`, жодних невикористаних налаштувань.

---

## 9. Чого не робимо

- Nginx як зворотний проксі, SSL/TLS, mkcert, SSL Bundles, `server.ssl.*`, Let's Encrypt/Certbot (слайди 12-13, 31, 42-43) — немає в завданні; лектор називає їх прикладами. HTTPS дає тунель сам;
- IPv6 Dual Stack `server.address="::"` (слайд 15), `server.forward-headers-strategy` і `remoteip.internal-proxies` (слайди 32-33) — немає в завданні (див. розділ 4.4, коли це може знадобитися);
- Blue-Green, Rolling Updates, CDN, Cache-Control/ETag (слайди 18-29), `ShallowEtagHeaderFilter`;
- Spring Cloud Function і PaaS (Railway/Render/Fly.io) для командного проєкту — це індивідуальне завдання (слайди 35-41);
- PostgreSQL у `compose.yaml`: не вимагається, робочий застосунок лишається на H2 (контракт №4). Перехід на PostgreSQL потребує окремої перевірки схеми й не входить у завдання;
- `application-prod.properties` і профілі для розгортання — не вимагаються; секрети й паролі беруться зі змінних середовища;
- Kubernetes, Docker Swarm, CI-пайплайни;
- нових бізнес-правил, ендпоінтів і міграцій — окрім двох рядків у `application.properties`.

---

## 10. Ризики

| Ризик | Що робити |
|-------|-----------|
| Не вдається забезпечити зовнішній доступ (тунель блокується, немає акаунта Ngrok/VPS) | Починати саме з цього (Людина 2), не з кінця. Cloudflare-тунель не потребує акаунта; VPS-триал — лектор готовий допомогти підібрати (написати йому) |
| `docker stop` обриває запити (код 137) | Лише `docker compose stop` з `stop_grace_period` (розділ 2); не `docker kill`, не `docker stop` без `-t` |
| Контейнер не підіймається після `docker kill`/ручної зупинки | Це поведінка `unless-stopped` (розділ 2): `docker compose up -d` |
| `./mvnw: not found` або `bad interpreter` у збірці образу | Кінці рядків CRLF у `mvnw`: має бути LF (`.gitattributes` зі Spring Initializr; перевірити `file mvnw`) |
| `"/target/...jar": not found` | `target` у `.dockerignore` при одноетапному `Dockerfile` — використовувати багатоетапний (розділ 4.1) |
| Застосунок падає при старті з `WeakKeyException` | `JWT_SECRET` у `deploy.env` коротший за 32 байти |
| Вхід паролем із `deploy.env` дає `401` | Ім'я змінної не збігається: `FOODRESCUE_DEMO_PASSWORDS_<КОРИСТУВАЧ>` великими літерами з підкресленнями; пароль у `application.properties` лишився старий — це нормально |
| Тунель: після входу редірект на `http://...` | Розділ 4.4 (`server.forward-headers-strategy`); перевірити перед додаванням |
| Sonar: секрети в репозиторії | Справжній `deploy.env` у `.gitignore`; у репозиторії лише `deploy.env.example` без значень |
| Збірка образу довга й залежить від мережі | Перша ~1,5 хв; потрібен Інтернет (залежності, образ `eclipse-temurin:25-jre`); робити заздалегідь, не під час демо |
| Docker на машині викладача відсутній | Звіт містить скриншоти і команди; юніт systemd — альтернатива |

---

## 11. Порядок дня

| Коли | Хто | Що |
|------|-----|----|
| Зараз | разом | Домовитися, хто Людина 1, 2, 3. Уточнити дедлайн на distedu. Людина 2 одразу пробує тунель до **будь-якого** локального застосунку — це найбільший ризик |
| До старту (~1 год) | Людина 1 | База: розділ 3, одним злиттям у `main`. Людини 2 і 3 читають розділи 4-5; Людина 3 готує VPS/Linux для юніта |
| Основна частина (~2 год) | кожен окремо | Свій артефакт із розділу 5, свої пункти звіту й README |
| Перед здачею (~30 хв) | разом | Злити в `main`; з чистого архіву пройти розділ 8; зібрати звіт (розділ 6) |
| Здача | разом | Архів без `target` і `deploy.env` та звіт на distedu. Кожен готовий пояснити будь-яку частину |
