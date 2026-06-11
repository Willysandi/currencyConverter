package com.willysandi.currency.api;

import com.willysandi.currency.dto.ConversionResponse;
import com.willysandi.currency.service.ExchangeRateService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api")
public class ConversionController {

    private static final String CURRENCY_CODE = "[A-Za-z]{3}";

    private final ExchangeRateService service;

    public ConversionController(ExchangeRateService service) {
        this.service = service;
    }

    @GetMapping("/convert")
    public ConversionResponse convert(
            @RequestParam @Pattern(regexp = CURRENCY_CODE, message = "'from' must be a 3-letter currency code") String from,
            @RequestParam @Pattern(regexp = CURRENCY_CODE, message = "'to' must be a 3-letter currency code") String to,
            @RequestParam @NotNull @Positive(message = "'amount' must be a positive number") BigDecimal amount) {
        return service.convert(from, to, amount);
    }

    @GetMapping("/currencies")
    public Set<String> currencies() {
        return new TreeSet<>(service.getSupportedCurrencies());
    }

    @GetMapping("/rates/{base}")
    public Map<String, BigDecimal> rates(
            @PathVariable @Pattern(regexp = CURRENCY_CODE, message = "base must be a 3-letter currency code") String base) {
        return service.getRates(base);
    }
}