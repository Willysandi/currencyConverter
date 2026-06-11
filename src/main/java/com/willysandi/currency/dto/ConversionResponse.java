package com.willysandi.currency.dto;

import java.math.BigDecimal;

public record ConversionResponse(String from, String to, BigDecimal amount,
                                 BigDecimal rate, BigDecimal result) {
}