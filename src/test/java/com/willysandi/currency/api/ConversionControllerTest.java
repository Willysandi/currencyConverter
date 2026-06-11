package com.willysandi.currency.api;

import com.willysandi.currency.dto.ConversionResponse;
import com.willysandi.currency.exception.UnknownCurrencyException;
import com.willysandi.currency.exception.UpstreamApiException;
import com.willysandi.currency.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversionController.class)
class ConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeRateService service;

    @Test
    void convert_returnsConversionJson() throws Exception {
        when(service.convert(eq("USD"), eq("EUR"), any()))
                .thenReturn(new ConversionResponse("USD", "EUR",
                        new BigDecimal("100"), new BigDecimal("0.92"), new BigDecimal("92.00")));

        mockMvc.perform(get("/api/convert")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("USD"))
                .andExpect(jsonPath("$.to").value("EUR"))
                .andExpect(jsonPath("$.rate").value(0.92))
                .andExpect(jsonPath("$.result").value(92.00));
    }

    @Test
    void convert_invalidCurrencyFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/convert")
                        .param("from", "XX")
                        .param("to", "EUR")
                        .param("amount", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("'from' must be a 3-letter currency code"));
    }

    @Test
    void convert_negativeAmount_returns400() throws Exception {
        mockMvc.perform(get("/api/convert")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("amount", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("'amount' must be a positive number"));
    }

    @Test
    void convert_missingAmount_returns400() throws Exception {
        mockMvc.perform(get("/api/convert")
                        .param("from", "USD")
                        .param("to", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required parameter 'amount'"));
    }

    @Test
    void convert_unknownCurrency_returns400() throws Exception {
        when(service.convert(eq("ZZZ"), eq("EUR"), any()))
                .thenThrow(new UnknownCurrencyException("ZZZ"));

        mockMvc.perform(get("/api/convert")
                        .param("from", "ZZZ")
                        .param("to", "EUR")
                        .param("amount", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported currency code: ZZZ"));
    }

    @Test
    void convert_upstreamApiDown_returns502() throws Exception {
        when(service.convert(any(), any(), any()))
                .thenThrow(new UpstreamApiException("connection refused"));

        mockMvc.perform(get("/api/convert")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("amount", "10"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Exchange rate provider is currently unavailable"));
    }
}