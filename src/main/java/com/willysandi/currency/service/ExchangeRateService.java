package com.willysandi.currency.service;

import com.willysandi.currency.client.ExchangeRateApiClient;
import com.willysandi.currency.dto.ConversionResponse;
import com.willysandi.currency.exception.UnknownCurrencyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ExchangeRateService {

    private final ExchangeRateApiClient client;

    public ExchangeRateService(ExchangeRateApiClient client) {
        this.client = client;
    }

    public ConversionResponse convert(String from, String to, BigDecimal amount) {
        String fromCode = requireSupported(from);
        String toCode = requireSupported(to);

        BigDecimal rate = client.getRates(fromCode).get(toCode);
        if (rate == null) {
            throw new UnknownCurrencyException(toCode);
        }
        BigDecimal result = rate.multiply(amount).setScale(2, RoundingMode.HALF_UP);
        return new ConversionResponse(fromCode, toCode, amount, rate, result);
    }

    public Set<String> getSupportedCurrencies() {
        return client.getSupportedCodes();
    }

    public Map<String, BigDecimal> getRates(String base) {
        return client.getRates(requireSupported(base));
    }

    private String requireSupported(String code) {
        String upper = code.trim().toUpperCase(Locale.ROOT);
        if (!client.getSupportedCodes().contains(upper)) {
            throw new UnknownCurrencyException(upper);
        }
        return upper;
    }
}