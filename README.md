# Currency Converter

A command-line Java application that converts between currencies using live exchange rates from the [ExchangeRate-API](https://www.exchangerate-api.com/).

## Features

- Fetches live exchange rates from ExchangeRate-API v6
- Supports ~170 currencies worldwide
- Validates currency codes against the API's supported list
- Handles invalid input gracefully — prompts again without crashing
- Precise decimal arithmetic using `BigDecimal`

## Prerequisites

- Java 11 or higher
- Maven
- A free API key from [exchangerate-api.com](https://www.exchangerate-api.com/)

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/currency.git
   cd currency
   ```

2. Create a `.env` file in the project root:
   ```
   EXCHANGE_RATE_API_KEY=your_api_key_here
   ```

3. Install dependencies:
   ```bash
   mvn install
   ```

## Usage

Run the application:
```bash
mvn exec:java
```

You will be prompted to enter:
1. The currency to convert **from** (e.g. `USD`)
2. The currency to convert **to** (e.g. `EUR`)
3. The **amount** to convert (e.g. `100`)

Example output:
```
Type Currency
USD
Convert To
EUR
Input Value
100
EUR 91.50
```

## Running Tests

```bash
mvn test
```

## Project Structure

```
currency/
├── .env                          ← Your API key (never committed)
├── .gitignore
├── pom.xml                       ← Maven config and dependencies
├── DOCUMENTATION.md              ← In-depth code walkthrough
└── src/
    ├── main/java/com/
    │   └── CurrencyConverter.java
    └── test/java/com/
        └── CurrencyConverterTest.java
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| OkHttp3 | 4.10.0 | HTTP requests |
| org.json | 20231013 | JSON parsing |
| dotenv-java | 3.0.0 | Load `.env` file |
| JUnit 5 | 5.10.2 | Unit testing |

## Documentation

For a detailed walkthrough of the code, programming concepts, and design decisions, see [DOCUMENTATION.md](DOCUMENTATION.md).
