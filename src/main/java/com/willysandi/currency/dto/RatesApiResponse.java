package com.willysandi.currency.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

public record RatesApiResponse(String result,
                               @JsonProperty("conversion_rates") Map<String, BigDecimal> conversionRates,
                               @JsonProperty("error-type") String errorType) {
}