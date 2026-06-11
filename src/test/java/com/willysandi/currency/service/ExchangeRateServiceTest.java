package com.willysandi.currency.service;

import com.willysandi.currency.client.ExchangeRateApiClient;
import com.willysandi.currency.dto.ConversionResponse;
import com.willysandi.currency.exception.UnknownCurrencyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateApiClient client;

    @InjectMocks
    private ExchangeRateService service;

    private void stubUsdRates(Map<String, BigDecimal> rates) {
        when(client.getSupportedCodes()).thenReturn(Set.of("USD", "EUR", "GBP"));
        when(client.getRates("USD")).thenReturn(rates);
    }

    @Test
    void convert_multipliesAmountByRate() {
        stubUsdRates(Map.of("EUR", new BigDecimal("1.25")));
        ConversionResponse response = service.convert("USD", "EUR", new BigDecimal("100"));
        assertEquals(new BigDecimal("125.00"), response.result());
    }

    @Test
    void convert_roundsHalfUp() {
        stubUsdRates(Map.of("EUR", new BigDecimal("1.005")));
        // 1.005 * 100 = 100.500 → rounds to 100.50
        ConversionResponse response = service.convert("USD", "EUR", new BigDecimal("100"));
        assertEquals(new BigDecimal("100.50"), response.result());
    }

    @Test
    void convert_largeValue() {
        stubUsdRates(Map.of("EUR", new BigDecimal("0.85")));
        ConversionResponse response = service.convert("USD", "EUR", new BigDecimal("1000000"));
        assertEquals(new BigDecimal("850000.00"), response.result());
    }

    @Test
    void convert_scaleIsAlwaysTwo() {
        stubUsdRates(Map.of("EUR", BigDecimal.ONE));
        ConversionResponse response = service.convert("USD", "EUR", new BigDecimal("5"));
        assertEquals(2, response.result().scale());
    }

    @Test
    void convert_uppercasesAndTrimsInput() {
        stubUsdRates(Map.of("EUR", new BigDecimal("2")));
        ConversionResponse response = service.convert(" usd ", "eur", BigDecimal.ONE);
        assertEquals("USD", response.from());
        assertEquals("EUR", response.to());
    }

    @Test
    void convert_unknownFromCurrency_throws() {
        when(client.getSupportedCodes()).thenReturn(Set.of("USD", "EUR"));
        assertThrows(UnknownCurrencyException.class,
                () -> service.convert("ZZZ", "EUR", BigDecimal.ONE));
    }

    @Test
    void convert_unknownToCurrency_throws() {
        when(client.getSupportedCodes()).thenReturn(Set.of("USD", "EUR"));
        assertThrows(UnknownCurrencyException.class,
                () -> service.convert("USD", "ZZZ", BigDecimal.ONE));
    }

    @Test
    void convert_rateMissingFromResponse_throws() {
        when(client.getSupportedCodes()).thenReturn(Set.of("USD", "EUR"));
        when(client.getRates("USD")).thenReturn(Map.of());
        assertThrows(UnknownCurrencyException.class,
                () -> service.convert("USD", "EUR", BigDecimal.ONE));
    }
}