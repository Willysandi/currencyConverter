# Currency Converter API

![CI](https://github.com/Willysandi/currencyConverter/actions/workflows/ci.yml/badge.svg)

A Spring Boot REST API that converts between ~170 currencies using live exchange rates from [ExchangeRate-API](https://www.exchangerate-api.com/), with in-memory rate caching, declarative request validation, and consistent JSON error handling.

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/convert?from=USD&to=EUR&amount=100` | Convert an amount between two currencies |
| GET | `/api/currencies` | List all supported currency codes |
| GET | `/api/rates/{base}` | All exchange rates for a base currency |

### Example

```
GET /api/convert?from=USD&to=EUR&amount=100
```

```json
{ "from": "USD", "to": "EUR", "amount": 100, "rate": 0.8666, "result": 86.66 }
```

Invalid input returns a clean error with the right status code:

```
GET /api/convert?from=XX&to=EUR&amount=100   → 400
{ "error": "'from' must be a 3-letter currency code" }

GET /api/convert?from=ZZZ&to=EUR&amount=10   → 400
{ "error": "Unsupported currency code: ZZZ" }
```

If the upstream rate provider is unreachable, the API responds `502 Bad Gateway` instead of leaking a stack trace.

## Design Highlights

- **Caching** — exchange rates and the supported-codes list are cached in memory with [Caffeine](https://github.com/ben-manes/caffeine) (30-minute TTL). Repeat requests are served in single-digit milliseconds without hitting the third-party API, reducing latency and API quota usage.
- **Validation** — request parameters are validated declaratively with Jakarta Bean Validation (`@Pattern`, `@Positive`); unknown currencies are rejected against the provider's live code list.
- **Error handling** — a global `@RestControllerAdvice` maps every failure mode to a consistent `{"error": "..."}` JSON body: bad input → 400, provider failure → 502.
- **Money math** — all arithmetic uses `BigDecimal` with explicit `HALF_UP` rounding to 2 decimal places. No floating point.
- **Layered architecture** — controller (HTTP) → service (business logic) → client (third-party integration), each independently unit-tested with mocks.

## Running Locally

Prerequisites: JDK 21+, Maven, and a free API key from [exchangerate-api.com](https://www.exchangerate-api.com/).

1. Create a `.env` file in the project root (it is gitignored):
   ```
   EXCHANGE_RATE_API_KEY=your_api_key_here
   ```
2. Start the server:
   ```bash
   mvn spring-boot:run
   ```
3. Try it:
   ```bash
   curl "http://localhost:8080/api/convert?from=USD&to=EUR&amount=100"
   ```

## Tests

```bash
mvn test
```

14 tests across two layers, no network required:

- `ExchangeRateServiceTest` — conversion math (rounding, scale, edge cases) and unknown-currency rejection, with the API client mocked (Mockito)
- `ConversionControllerTest` — HTTP layer via `@WebMvcTest` + `MockMvc`: JSON response shape, all 400 validation cases, 502 on upstream failure

## Continuous Integration

Every push and pull request runs the full build and test suite on GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)). Tests mock all external calls, so CI needs no secrets.

## Project Structure

```
src/main/java/com/willysandi/currency/
├── CurrencyApplication.java        Spring Boot entry point
├── api/
│   ├── ConversionController.java   REST endpoints + request validation
│   └── GlobalExceptionHandler.java Maps exceptions to JSON error responses
├── client/
│   └── ExchangeRateApiClient.java  RestClient wrapper for ExchangeRate-API (cached)
├── config/
│   └── CacheConfig.java            Enables Spring caching
├── dto/                            Request/response records (Jackson)
├── exception/                      Domain exceptions
└── service/
    └── ExchangeRateService.java    Conversion logic
```

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21, Spring Boot 4 | Application framework |
| Spring `RestClient` | Outbound HTTP to ExchangeRate-API |
| Caffeine | In-memory caching with TTL |
| Jakarta Bean Validation | Request validation |
| JUnit 5, Mockito, MockMvc | Testing |
| GitHub Actions | CI |

## Documentation

For an in-depth walkthrough of the architecture and the concepts behind it (REST, dependency injection, caching internals, test slices), see [DOCUMENTATION.md](DOCUMENTATION.md).
