package com;

import org.json.JSONException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CurrencyConverterTest {

    private static final String TEST_API_KEY = "test-key-123";

    // ── buildUrl ─────────────────────────────────────────────────────────────

    @Test
    void buildUrl_uppercasesInput() {
        String url = CurrencyConverter.buildUrl(TEST_API_KEY, "usd");
        assertTrue(url.endsWith("/USD"), "URL must end with the uppercased currency code");
    }

    @Test
    void buildUrl_alreadyUppercase() {
        String url = CurrencyConverter.buildUrl(TEST_API_KEY, "EUR");
        assertTrue(url.endsWith("/EUR"));
    }

    @Test
    void buildUrl_containsBaseUrl() {
        String url = CurrencyConverter.buildUrl(TEST_API_KEY, "gbp");
        assertTrue(url.contains("exchangerate-api.com"));
        assertTrue(url.contains(TEST_API_KEY));
        assertTrue(url.contains("/latest/GBP"));
    }

    // ── convert ──────────────────────────────────────────────────────────────

    @Test
    void convert_normalConversion() {
        BigDecimal rate = new BigDecimal("1.25");
        BigDecimal input = new BigDecimal("100");
        BigDecimal result = CurrencyConverter.convert(rate, input);
        assertEquals(new BigDecimal("125.00"), result);
    }

    @Test
    void convert_roundsHalfUp() {
        BigDecimal rate = new BigDecimal("1.005");
        BigDecimal input = new BigDecimal("100");
        // 1.005 * 100 = 100.500 → rounds to 100.50
        BigDecimal result = CurrencyConverter.convert(rate, input);
        assertEquals(new BigDecimal("100.50"), result);
    }

    @Test
    void convert_zeroValue() {
        BigDecimal result = CurrencyConverter.convert(new BigDecimal("1.5"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void convert_largeValue() {
        BigDecimal rate = new BigDecimal("0.85");
        BigDecimal input = new BigDecimal("1000000");
        BigDecimal result = CurrencyConverter.convert(rate, input);
        assertEquals(new BigDecimal("850000.00"), result);
    }

    @Test
    void convert_scaleIsAlwaysTwo() {
        BigDecimal result = CurrencyConverter.convert(new BigDecimal("1"), new BigDecimal("5"));
        assertEquals(2, result.scale());
    }

    // ── formatResult ─────────────────────────────────────────────────────────

    @Test
    void formatResult_includesCurrencyCode() {
        String output = CurrencyConverter.formatResult("eur", new BigDecimal("100.00"));
        assertTrue(output.startsWith("EUR "), "Output must start with uppercased currency code");
    }

    @Test
    void formatResult_hasTwoDecimalPlaces() {
        String output = CurrencyConverter.formatResult("USD", new BigDecimal("1234.50"));
        assertEquals("USD 1,234.50", output);
    }

    @Test
    void formatResult_uppercasesCurrency() {
        String output = CurrencyConverter.formatResult("jpy", new BigDecimal("500.00"));
        assertTrue(output.startsWith("JPY "));
    }

    // ── extractRate ──────────────────────────────────────────────────────────

    @Test
    void extractRate_parsesRateCorrectly() {
        String json = "{\"result\":\"success\",\"conversion_rates\":{\"EUR\":1.08,\"GBP\":0.79}}";
        BigDecimal rate = CurrencyConverter.extractRate(json, "EUR");
        assertEquals(0, new BigDecimal("1.08").compareTo(rate));
    }

    @Test
    void extractRate_uppercasesTargetCurrency() {
        String json = "{\"result\":\"success\",\"conversion_rates\":{\"EUR\":1.08}}";
        BigDecimal rate = CurrencyConverter.extractRate(json, "eur");
        assertEquals(0, new BigDecimal("1.08").compareTo(rate));
    }

    @Test
    void extractRate_throwsForMissingCurrency() {
        String json = "{\"result\":\"success\",\"conversion_rates\":{\"EUR\":1.08}}";
        assertThrows(JSONException.class,
                () -> CurrencyConverter.extractRate(json, "XYZ"));
    }

    @Test
    void extractRate_throwsForMalformedJson() {
        assertThrows(JSONException.class,
                () -> CurrencyConverter.extractRate("not-json", "USD"));
    }

    @Test
    void extractRate_throwsWhenConversionRatesKeyMissing() {
        String json = "{\"other_key\":{\"USD\":1.0}}";
        assertThrows(JSONException.class,
                () -> CurrencyConverter.extractRate(json, "USD"));
    }

    // ── isSupportedCurrency ───────────────────────────────────────────────────

    private static final Set<String> CODES = Set.of("USD", "EUR", "GBP");

    @Test
    void isSupportedCurrency_validCode() {
        assertTrue(CurrencyConverter.isSupportedCurrency("USD", CODES));
    }

    @Test
    void isSupportedCurrency_validLowercase() {
        assertTrue(CurrencyConverter.isSupportedCurrency("usd", CODES));
    }

    @Test
    void isSupportedCurrency_unknownCode() {
        assertFalse(CurrencyConverter.isSupportedCurrency("XYZ", CODES));
    }

    @Test
    void isSupportedCurrency_nullInput() {
        assertFalse(CurrencyConverter.isSupportedCurrency(null, CODES));
    }

    @Test
    void isSupportedCurrency_emptyString() {
        assertFalse(CurrencyConverter.isSupportedCurrency("", CODES));
    }

    // ── isValidCurrency ───────────────────────────────────────────────────────

    @Test
    void isValidCurrency_validUppercase() {
        assertTrue(CurrencyConverter.isValidCurrency("USD"));
    }

    @Test
    void isValidCurrency_validLowercase() {
        assertTrue(CurrencyConverter.isValidCurrency("eur"));
    }

    @Test
    void isValidCurrency_validMixedCase() {
        assertTrue(CurrencyConverter.isValidCurrency("gBp"));
    }

    @Test
    void isValidCurrency_tooShort() {
        assertFalse(CurrencyConverter.isValidCurrency("US"));
    }

    @Test
    void isValidCurrency_tooLong() {
        assertFalse(CurrencyConverter.isValidCurrency("USDD"));
    }

    @Test
    void isValidCurrency_emptyString() {
        assertFalse(CurrencyConverter.isValidCurrency(""));
    }

    @Test
    void isValidCurrency_nullInput() {
        assertFalse(CurrencyConverter.isValidCurrency(null));
    }

    @Test
    void isValidCurrency_containsDigit() {
        assertFalse(CurrencyConverter.isValidCurrency("US1"));
    }

    @Test
    void isValidCurrency_containsSpace() {
        assertFalse(CurrencyConverter.isValidCurrency("U D"));
    }

    @Test
    void isValidCurrency_containsSpecialChar() {
        assertFalse(CurrencyConverter.isValidCurrency("U$D"));
    }

    // ── isValidAmount ─────────────────────────────────────────────────────────

    @Test
    void isValidAmount_wholeNumber() {
        assertTrue(CurrencyConverter.isValidAmount("100"));
    }

    @Test
    void isValidAmount_decimal() {
        assertTrue(CurrencyConverter.isValidAmount("99.99"));
    }

    @Test
    void isValidAmount_smallPositive() {
        assertTrue(CurrencyConverter.isValidAmount("0.01"));
    }

    @Test
    void isValidAmount_zero() {
        assertFalse(CurrencyConverter.isValidAmount("0"));
    }

    @Test
    void isValidAmount_zeroDecimal() {
        assertFalse(CurrencyConverter.isValidAmount("0.00"));
    }

    @Test
    void isValidAmount_negative() {
        assertFalse(CurrencyConverter.isValidAmount("-50"));
    }

    @Test
    void isValidAmount_alphabetic() {
        assertFalse(CurrencyConverter.isValidAmount("abc"));
    }

    @Test
    void isValidAmount_empty() {
        assertFalse(CurrencyConverter.isValidAmount(""));
    }

    @Test
    void isValidAmount_nullInput() {
        assertFalse(CurrencyConverter.isValidAmount(null));
    }

    @Test
    void isValidAmount_whitespaceOnly() {
        assertFalse(CurrencyConverter.isValidAmount("   "));
    }

    @Test
    void isValidAmount_leadingTrailingWhitespace() {
        assertTrue(CurrencyConverter.isValidAmount("  100  "));
    }
}
