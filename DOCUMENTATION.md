# Currency Converter API — Learning Documentation

This document explains every part of this project: what the code does, why it is designed this way, and the concepts behind each decision. The project started as a command-line app in a single class; it is now a Spring Boot REST API with caching, validation, structured error handling, and CI. This document covers the new system — and uses the conversion itself as a case study in how real applications are structured.

---

## Table of Contents

1. [What the App Does](#1-what-the-app-does)
2. [What Is a REST API?](#2-what-is-a-rest-api)
3. [Spring Boot in a Nutshell](#3-spring-boot-in-a-nutshell)
4. [The Layered Architecture](#4-the-layered-architecture)
5. [Life of a Request](#5-life-of-a-request)
6. [Class-by-Class Walkthrough](#6-class-by-class-walkthrough)
7. [Validation](#7-validation)
8. [Error Handling](#8-error-handling)
9. [Caching — The Deep Dive](#9-caching--the-deep-dive)
10. [Configuration and Secrets](#10-configuration-and-secrets)
11. [Java Records and Jackson](#11-java-records-and-jackson)
12. [Money Math with BigDecimal](#12-money-math-with-bigdecimal)
13. [Testing Strategy](#13-testing-strategy)
14. [Continuous Integration](#14-continuous-integration)
15. [Lessons Learned the Hard Way](#15-lessons-learned-the-hard-way)

---

## 1. What the App Does

The app is a web service. It does not print prompts or read keyboard input — it listens on a network port (8080) and answers HTTP requests:

- `GET /api/convert?from=USD&to=EUR&amount=100` → converts an amount using the live exchange rate
- `GET /api/currencies` → lists every supported currency code
- `GET /api/rates/{base}` → returns all rates for one base currency

Under the hood it calls a third-party service ([ExchangeRate-API](https://www.exchangerate-api.com/)) for live rates, but it **caches** those rates in memory for 30 minutes, so most requests never leave the server.

**Why was the CLI converted to an API?** A CLI can only be used by one person sitting at the terminal. An API can be called by anything — a web frontend, a mobile app, another service, a `curl` command. Almost all professional backend work is building services like this one.

---

## 2. What Is a REST API?

**HTTP** is the protocol browsers and services use to talk to each other. Every interaction is a request/response pair:

```
Request:   GET /api/convert?from=USD&to=EUR&amount=100
Response:  Status: 200 OK
           Body:   {"from":"USD","to":"EUR","amount":100,"rate":0.8666,"result":86.66}
```

**REST** (Representational State Transfer) is a style of designing HTTP APIs. The parts that matter here:

- **Resources live at URLs.** `/api/rates/USD` is "the rates for USD". `/api/currencies` is "the list of currencies".
- **HTTP methods express intent.** `GET` reads data (this API is read-only, so everything is GET). `POST` creates, `PUT` updates, `DELETE` removes.
- **Status codes communicate outcomes.** The client should be able to tell what happened without parsing the body:

| Code | Meaning | When this API uses it |
|---|---|---|
| 200 OK | Success | Valid conversion |
| 400 Bad Request | *Your* input was wrong | Bad currency format, negative amount, unknown currency |
| 404 Not Found | No such resource | Wrong URL path |
| 500 Internal Server Error | *The server* broke | An unexpected bug (should never happen) |
| 502 Bad Gateway | A server *we depend on* broke | ExchangeRate-API is down or returns an error |

The 400-vs-502 distinction matters: 4xx means "fix your request", 5xx means "not your fault, try later". Choosing the right code is part of API design.

- **JSON is the body format.** Structured, human-readable, and every language can parse it.

---

## 3. Spring Boot in a Nutshell

Spring Boot is the framework that turns our classes into a running web server. Three core ideas:

### 3.1 The IoC Container and Beans

Normally *you* create objects: `new ExchangeRateService(new ExchangeRateApiClient(...))`. With Spring, the framework creates and wires the objects for you. Objects managed by Spring are called **beans**, and the registry holding them is the **container** (or *application context*).

At startup, Spring performs **component scanning**: it looks at every class in `com.willysandi.currency` and below, and any class annotated with `@Component`, `@Service`, `@RestController`, or `@Configuration` becomes a bean.

> This is why the package layout matters. Component scanning starts from the package of the `@SpringBootApplication` class (`com.willysandi.currency`) and only descends *downward*. A controller in a sibling package like `com.willysandi.api` would compile fine but silently never be registered — every request to it would 404.

### 3.2 Dependency Injection (DI)

When a bean needs another bean, it declares it as a constructor parameter, and Spring passes it in:

```java
@Service
public class ExchangeRateService {
    private final ExchangeRateApiClient client;

    public ExchangeRateService(ExchangeRateApiClient client) {  // Spring injects this
        this.client = client;
    }
}
```

The service never writes `new ExchangeRateApiClient(...)` — it just states *what it needs*, not *how to build it*. This is **inversion of control**: object creation is inverted from the class to the framework.

Why this matters: in tests we can hand the service a **fake** client instead of the real one. That's the entire basis of the testing strategy in section 13. DI makes code testable.

### 3.3 Auto-configuration and Starters

The `pom.xml` lists **starters** like `spring-boot-starter-webmvc`. A starter is a bundle of dependencies plus auto-configuration: adding the webmvc starter gives you an embedded Tomcat web server, JSON serialization (Jackson), and request routing — with zero configuration code. Spring Boot looks at what's on the classpath and configures sensible defaults ("convention over configuration"). The same is true for caching: because Caffeine is on the classpath and `spring.cache.*` properties exist, Spring builds a Caffeine-backed cache manager automatically.

---

## 4. The Layered Architecture

The old app was one class. The new app is split into layers, each with one responsibility:

```
            HTTP request
                 │
                 ▼
┌────────────────────────────────────┐
│  api/  — ConversionController      │  Web layer: parse the request, validate
│          GlobalExceptionHandler    │  params, return JSON, map errors to codes
└────────────────┬───────────────────┘
                 ▼
┌────────────────────────────────────┐
│  service/ — ExchangeRateService    │  Business logic: which currencies are
│                                    │  legal, how conversion math works
└────────────────┬───────────────────┘
                 ▼
┌────────────────────────────────────┐
│  client/ — ExchangeRateApiClient   │  Integration layer: HTTP calls to the
│            (cached with Caffeine)  │  third-party API, response checking
└────────────────┬───────────────────┘
                 ▼
        ExchangeRate-API (internet)
```

Supporting packages: `dto/` (the data shapes passed between layers and serialized to JSON), `exception/` (domain exceptions), `config/` (framework configuration).

**Why layers?** Each layer can change independently:

- Switch rate providers → only `client/` changes.
- Change the rounding rule → only `service/` changes.
- Add a new endpoint → only `api/` changes.

And each layer can be **tested in isolation** by replacing the layer below it with a mock. The dependency rule is strict: arrows point downward only. The service never knows HTTP exists; the client never knows what a controller is.

This is the same Single Responsibility Principle from the CLI version — applied at the architecture level instead of the method level.

---

## 5. Life of a Request

What happens, in order, when `GET /api/convert?from=usd&to=eur&amount=100` arrives:

```
1. Embedded Tomcat accepts the TCP connection and parses the HTTP request.
2. Spring MVC routes the path "/api/convert" to ConversionController.convert()
   (because of @RequestMapping("/api") + @GetMapping("/convert")).
3. Spring binds query parameters to method arguments:
   "usd" → String from, "eur" → String to, "100" → BigDecimal amount.
4. Bean Validation runs the constraint annotations:
   @Pattern("[A-Za-z]{3}") on from/to, @Positive on amount.
   ── If a constraint fails → HandlerMethodValidationException →
      GlobalExceptionHandler turns it into 400 + {"error": ...}. Done.
5. The controller calls service.convert("usd", "eur", 100).
6. The service trims/uppercases the codes, then checks both against
   client.getSupportedCodes().
   ── Unknown code → UnknownCurrencyException → 400. Done.
7. The service calls client.getRates("USD").
   ── Cache HIT (fetched < 30 min ago): the cached Map returns instantly.
   ── Cache MISS: the client sends a real HTTPS request to ExchangeRate-API,
      checks the response, stores the Map in the cache, returns it.
      If the upstream call fails → UpstreamApiException → 502. Done.
8. The service computes rate.multiply(amount).setScale(2, HALF_UP)
   and wraps everything in a ConversionResponse record.
9. Jackson serializes the record to JSON; Spring writes a 200 response.
```

Steps 1–4 and 9 are pure framework — we wrote no code for them. Our code is steps 5–8.

---

## 6. Class-by-Class Walkthrough

### `CurrencyApplication`

```java
@SpringBootApplication
public class CurrencyApplication {
    public static void main(String[] args) {
        SpringApplication.run(CurrencyApplication.class, args);
    }
}
```

The entry point. `@SpringBootApplication` combines three annotations: `@Configuration` (this class can define beans), `@EnableAutoConfiguration` (turn on Boot's defaults), and `@ComponentScan` (scan this package and below). `main` starts the container, which starts Tomcat, which listens forever.

Note what is *not* here: no feature annotations. `@EnableCaching` deliberately lives in `config/CacheConfig` — see section 15 for the bug that taught us why.

### `api/ConversionController`

Declares the three endpoints. Its only jobs: bind/validate request parameters, delegate to the service, return DTOs. It contains **no logic** — you should be able to read it in 30 seconds. The `@RestController` annotation means every return value is serialized to JSON automatically.

### `api/GlobalExceptionHandler`

A `@RestControllerAdvice` — a cross-cutting interceptor that catches exceptions from *any* controller and converts them to responses. Covered in section 8.

### `service/ExchangeRateService`

The business core. Normalizes input (`trim().toUpperCase()` — "normalize at the boundary", same principle as the CLI version), enforces the "currency must be supported" rule, and does the conversion math. It is deliberately free of HTTP, JSON, and caching concerns — which is why its unit tests need no framework at all.

### `client/ExchangeRateApiClient`

The only class that knows ExchangeRate-API exists. It builds a `RestClient` (Spring's modern HTTP client) pointed at the base URL + API key, and exposes two methods: `getRates(base)` and `getSupportedCodes()`. It performs two levels of response checking, exactly like the old `fetchJson()` did:

1. **Transport level** — non-2xx status or network failure → `UpstreamApiException`
2. **Application level** — HTTP 200 but body says `"result": "error"` → `UpstreamApiException`

Both `@Cacheable` annotations live here, on the methods that actually cost money and time. Caching at the integration boundary means every caller — current and future — benefits automatically.

### `dto/` records

`ConversionResponse` is what *we* send out. `RatesApiResponse` and `CodesApiResponse` mirror what *the upstream API* sends us. Keeping "their shape" and "our shape" as separate types means the upstream provider can change its JSON without that change rippling past the client layer.

### `exception/` classes

`UnknownCurrencyException` (the caller asked for a currency that doesn't exist → their fault → 400) and `UpstreamApiException` (the provider failed → not the caller's fault → 502). Custom exceptions carry *meaning*; the handler maps meaning to status codes.

---

## 7. Validation

The CLI version had hand-written validation methods (`isValidCurrency`, `isValidAmount`) inside loops that re-prompted the user. An API can't re-prompt — it rejects the request and tells the caller why. The hand-written checks became **declarative annotations**:

```java
@GetMapping("/convert")
public ConversionResponse convert(
        @RequestParam @Pattern(regexp = "[A-Za-z]{3}", message = "'from' must be a 3-letter currency code") String from,
        @RequestParam @Pattern(regexp = "[A-Za-z]{3}", message = "'to' must be a 3-letter currency code") String to,
        @RequestParam @NotNull @Positive(message = "'amount' must be a positive number") BigDecimal amount) {
```

This is **Jakarta Bean Validation**: you annotate the constraint, the framework enforces it before your method runs. `@Pattern` checks a regular expression (`[A-Za-z]{3}` = exactly three letters), `@Positive` checks `> 0`. Compare with the old code — same rules, but now the rule is *data attached to the parameter* instead of *code you must remember to call*.

Validation happens in two stages, in two places, on purpose:

1. **Format validation** (controller, annotations): "is this *shaped like* a currency code?" — cheap, no I/O.
2. **Domain validation** (service): "is `ZZZ` an actual currency?" — requires the supported-codes list from the API (cached).

This mirrors the old `isValidCurrency` / `isSupportedCurrency` split. Cheap structural checks first, expensive semantic checks second.

---

## 8. Error Handling

Without any error handling, an exception inside a controller produces an HTML error page or a JSON blob with a stack trace — useless or dangerous (stack traces reveal internals). The fix is one class:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(String error) {}

    @ExceptionHandler(UnknownCurrencyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleUnknownCurrency(UnknownCurrencyException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler(UpstreamApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiError handleUpstream(UpstreamApiException e) {
        return new ApiError("Exchange rate provider is currently unavailable");
    }
    // ... plus handlers for validation, type-mismatch and missing-parameter errors
}
```

How it works: when any controller throws, Spring looks for a matching `@ExceptionHandler` in any `@RestControllerAdvice` class, calls it, and uses its return value (plus `@ResponseStatus`) as the response. The result is a **contract**: every error this API ever returns has the shape `{"error": "..."}` and a meaningful status code.

Two details worth noticing:

- The upstream handler does **not** echo `e.getMessage()` to the caller. The internal message ("connection refused to https://...") is for logs; the caller gets a stable, non-leaking sentence.
- Each exception type maps to exactly one status code. The *type* of the exception is the API's error taxonomy.

This replaces the old `try { ... } catch (IOException e) { System.err.println(...) }` in `main()` — same idea (one central place that decides how failures look), upgraded for a server.

---

## 9. Caching — The Deep Dive

### Why cache at all?

Without caching, every `/api/convert` request triggers an HTTPS round-trip to ExchangeRate-API:

- **Latency**: ~100–300 ms per upstream call vs ~3 ms from cache (measured on this app).
- **Quota/cost**: free API tiers allow a limited number of requests per month. 1,000 users converting USD→EUR should cost *one* upstream call, not 1,000.
- **Resilience**: while a rate is cached, the upstream provider can be briefly down and our API still answers.

The trade-off is **staleness**: a cached rate can be up to 30 minutes old. For a display-purposes converter that's fine; for an actual trading system it would be unacceptable. Cache TTL is always a business decision, not a technical one.

### The pattern: cache-aside

```
request → is the result in the cache?
            yes → return it                       (hit)
            no  → compute/fetch it, store it,     (miss)
                  return it
```

### The implementation: three pieces

**1. The annotation** (on the client methods):

```java
@Cacheable("rates")
public Map<String, BigDecimal> getRates(String base) { ... }
```

`@Cacheable("rates")` means: before running this method, look in the cache named `rates` using the arguments (`base`) as the key. Hit → return the cached value, *the method body never runs*. Miss → run the body, store the result under that key.

**2. The cache engine** (`application.properties`):

```properties
spring.cache.cache-names=rates,codes
spring.cache.caffeine.spec=expireAfterWrite=30m,maximumSize=200
```

Caffeine is a high-performance in-memory cache library. `expireAfterWrite=30m` is the TTL (time-to-live): 30 minutes after an entry is written, it is evicted, and the next request becomes a miss that fetches fresh data. `maximumSize=200` caps memory usage; if exceeded, Caffeine evicts the least-recently-used entries.

**3. The switch** (`config/CacheConfig`):

```java
@Configuration
@EnableCaching
public class CacheConfig { }
```

Without `@EnableCaching`, the `@Cacheable` annotations are silently ignored — no error, just no caching.

### How `@Cacheable` actually works: proxies

This is the part most tutorials skip. Spring does not rewrite your method. Instead, when it sees `@Cacheable` on a bean, it wraps the bean in a **proxy** — an auto-generated object that sits in front of the real one:

```
service ──calls──▶ [proxy: check cache first] ──maybe──▶ real client method
```

Everyone who gets the client injected actually gets the proxy. The crucial consequence: **only calls that come from *outside* the bean go through the proxy**. If a method inside `ExchangeRateApiClient` called `this.getRates(...)`, that call would bypass the proxy — and the cache — entirely, because `this` is the real object, not the proxy.

That is why the cache annotations sit on the client (called by the service, so always through the proxy) and why you should be suspicious any time you see a bean calling its own annotated method.

### What this cache is NOT

This is a **single-instance, in-memory** cache. If the app ran as 3 replicas behind a load balancer, each replica would have its own private cache (3× the upstream calls, possibly inconsistent answers). The standard fix at that scale is an external shared cache like **Redis** — same `@Cacheable` code, different backend. Knowing this limitation, and its fix, is a classic interview question.

---

## 10. Configuration and Secrets

### `application.properties`

Spring Boot's central config file. Anything that varies between environments (URLs, keys, cache settings, ports) belongs here rather than in code:

```properties
exchange.api.base-url=https://v6.exchangerate-api.com/v6
exchange.api.key=${EXCHANGE_RATE_API_KEY}
```

Code reads these with `@Value("${exchange.api.base-url}")`.

### The `${...}` placeholder — name, not value

`${EXCHANGE_RATE_API_KEY}` means "at startup, substitute the value of the variable **named** EXCHANGE_RATE_API_KEY" — from environment variables, the imported `.env` file, or system properties. The committed file contains the variable's *name*; the secret *value* lives only in `.env`, which `.gitignore` keeps out of git.

```
application.properties  (committed)   →  exchange.api.key=${EXCHANGE_RATE_API_KEY}
.env                    (gitignored)  →  EXCHANGE_RATE_API_KEY=<the actual secret>
```

> ⚠️ A mistake actually made during this project: putting the real key inside the braces — `exchange.api.key=${9522...}`. That fails twice. First, Spring tries to find a variable *named* `9522...` and crashes at startup ("could not resolve placeholder"). Second, the secret is now sitting in a committed file, one `git push` away from being public. If a secret ever lands in git history, deleting it later does not help — history is forever; the key must be rotated (regenerated).

This pattern is called **externalized configuration**. The same jar can run in dev, CI, and production with different behavior, purely by changing environment variables — never by editing code.

---

## 11. Java Records and Jackson

### Records

A **record** is Java's concise syntax (Java 16+) for an immutable data carrier:

```java
public record ConversionResponse(String from, String to, BigDecimal amount,
                                 BigDecimal rate, BigDecimal result) { }
```

That one line generates: a constructor, accessor methods (`response.from()`), `equals`, `hashCode`, and `toString`. Fields are `final` — once created, a record cannot change, which makes it safe to pass around and cache. Records replaced what would have been ~50 lines of getter/setter boilerplate per class.

### Jackson — JSON ↔ objects

Jackson is the library (bundled with Spring) that converts between JSON text and Java objects, in both directions:

- **Outbound**: the controller returns a `ConversionResponse`; Jackson writes `{"from":"USD",...}` by reading the record's components.
- **Inbound**: the client receives ExchangeRate-API's JSON; Jackson populates a `RatesApiResponse` by matching JSON field names to record components.

When the names don't match Java conventions, `@JsonProperty` provides the mapping:

```java
public record RatesApiResponse(String result,
        @JsonProperty("conversion_rates") Map<String, BigDecimal> conversionRates,
        @JsonProperty("error-type") String errorType) { }
```

(`conversion_rates` uses snake_case and `error-type` contains a hyphen — neither is a legal Java identifier style.)

This replaced the old `org.json` approach (`json.getJSONObject("conversion_rates").getBigDecimal(toCurrency)`). The difference: with `JSONObject` you navigate untyped data with string keys at every call site; with Jackson + records you declare the shape *once* and get typed, autocomplete-friendly objects everywhere else.

---

## 12. Money Math with BigDecimal

Unchanged from the CLI version, because it was already right. For money, never use `double`:

```java
double a = 0.1 + 0.2;        // 0.30000000000000004  ← binary floating point
```

`BigDecimal` stores exact decimal values. The conversion line is:

```java
rate.multiply(amount).setScale(2, RoundingMode.HALF_UP);
```

- `multiply` returns the exact product.
- `setScale(2, HALF_UP)` rounds to 2 decimal places using "school rounding" (5 rounds up).

Rules that still apply everywhere in this codebase: construct `BigDecimal` from a `String` (never a `double`), and compare with `compareTo`, not `equals` (`equals` treats `1.0` and `1.00` as different).

---

## 13. Testing Strategy

The 14 tests are split by layer, and neither suite touches the network. This is what makes them fast (~1 s) and runnable anywhere, including CI.

### Service tests — plain unit tests with a mock

```java
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {
    @Mock        private ExchangeRateApiClient client;   // a fake
    @InjectMocks private ExchangeRateService service;    // real, given the fake

    @Test
    void convert_roundsHalfUp() {
        when(client.getSupportedCodes()).thenReturn(Set.of("USD", "EUR", "GBP"));
        when(client.getRates("USD")).thenReturn(Map.of("EUR", new BigDecimal("1.005")));

        assertEquals(new BigDecimal("100.50"),
                service.convert("USD", "EUR", new BigDecimal("100")).result());
    }
}
```

A **mock** is a stand-in object whose behavior you script with `when(...).thenReturn(...)`. The service under test is real; its dependency is fake. This is dependency injection paying off: because the service receives its client from outside, tests can hand it anything.

These tests port the old `convert_*` tests (rounding, scale, large values) and add the unknown-currency rules.

### Controller tests — slice tests with `@WebMvcTest`

```java
@WebMvcTest(ConversionController.class)
class ConversionControllerTest {
    @Autowired   private MockMvc mockMvc;
    @MockitoBean private ExchangeRateService service;

    @Test
    void convert_invalidCurrencyFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/convert")
                        .param("from", "XX").param("to", "EUR").param("amount", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("'from' must be a 3-letter currency code"));
    }
}
```

`@WebMvcTest` is a **test slice**: it boots a miniature Spring context containing only the web layer — the controller, the exception handler, validation, JSON — and nothing else. `@MockitoBean` swaps the real service for a mock *inside that context*. `MockMvc` fires simulated HTTP requests without opening a real port.

What this slice verifies that the service tests cannot: URL routing, parameter binding, the validation annotations, the exact JSON field names, and every `GlobalExceptionHandler` mapping (400s and the 502).

### What deliberately isn't tested

The real `ExchangeRateApiClient` HTTP call (would need the network) and the caching behavior (verified manually: first request logs "Fetching…" and takes ~200 ms; the repeat takes ~3 ms and logs nothing). Both *can* be tested — `MockRestServiceServer` for the client, a `@SpringBootTest` with a mocked downstream for the cache — and make good future improvements.

---

## 14. Continuous Integration

`.github/workflows/ci.yml` tells GitHub: on every push to `master` and every pull request, run the build on a fresh Linux machine:

```yaml
steps:
  - uses: actions/checkout@v4          # clone the repo
  - uses: actions/setup-java@v4        # install Temurin JDK 21 + cache Maven deps
    with: { distribution: temurin, java-version: '21', cache: maven }
  - run: mvn -B verify                 # compile + run all 14 tests
```

Why this matters even for a solo project: it proves the build is reproducible from a clean checkout ("works on my machine" is not enough), it catches commits that break tests immediately, and the green badge on the README is verifiable evidence the code works.

Note that CI needs **no API key**: because every test mocks the external boundary, the suite runs fully offline. That was a design constraint, not luck — tests that need real credentials can't run on public CI.

---

## 15. Lessons Learned the Hard Way

Real problems hit during this conversion, kept here because each one teaches a general rule.

**1. Package location is behavior.** Source folders must mirror the package declaration (`com.willysandi.currency.dto` → `com/willysandi/currency/dto/`), and Spring only scans below the application class's package. Files in the wrong folder can *compile* and still produce an app where every endpoint 404s.

**2. One public type per file, named after the file.** Pasting three records into one `.java` file produces a wall of confusing compiler errors ("class, interface, enum, or record expected"). The file is the unit of compilation in Java.

**3. `${...}` takes a variable name, never a value.** See section 10. Bonus rule: a secret that touches git history is burned — rotate it.

**4. Framework annotations have a scope.** The first controller test run failed with `No qualifying bean of type 'CacheManager'`: `@EnableCaching` sat on the application class, which `@WebMvcTest` uses as its configuration root — so the slice demanded caching infrastructure it doesn't load. Moving `@EnableCaching` to a dedicated `@Configuration` class (which slices ignore) fixed it. General rule: keep the application class minimal; give every feature its own config class.

**5. Major framework versions move things.** Spring Boot 4 relocated `@WebMvcTest` from `...test.autoconfigure.web.servlet` to `...webmvc.test.autoconfigure`. Tutorials (and AI assistants) trained on Boot 3 give the old import. When an import "does not exist", check your actual jar — your classpath is the truth, not the tutorial.

**6. A passing build only proves what it actually built.** At one point `mvn compile` said BUILD SUCCESS while compiling *1 of 10* source files — the other 9 were in folders Maven doesn't look at. Read what the tool says it did ("Compiling 10 source files"), not just the green text at the bottom.

---

*End of documentation.*
