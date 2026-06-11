package com.willysandi.currency.client;

import com.willysandi.currency.dto.CodesApiResponse;
import com.willysandi.currency.dto.RatesApiResponse;
import com.willysandi.currency.exception.UpstreamApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ExchangeRateApiClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateApiClient.class);

    private final RestClient restClient;

    public ExchangeRateApiClient(@Value("${exchange.api.base-url}") String baseUrl,
                                 @Value("${exchange.api.key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + "/" + apiKey)
                .build();
    }

    @Cacheable("rates")
    public Map<String, BigDecimal> getRates(String base) {
        log.info("Fetching exchange rates for {} from upstream API", base);
        RatesApiResponse response = get("/latest/" + base, RatesApiResponse.class);
        requireSuccess(response.result(), response.errorType());
        return response.conversionRates();
    }

    @Cacheable("codes")
    public Set<String> getSupportedCodes() {
        log.info("Fetching supported currency codes from upstream API");
        CodesApiResponse response = get("/codes", CodesApiResponse.class);
        requireSuccess(response.result(), response.errorType());
        Set<String> codes = new HashSet<>();
        for (List<String> pair : response.supportedCodes()) {
            codes.add(pair.get(0));
        }
        return codes;
    }

    private <T> T get(String path, Class<T> type) {
        T body;
        try {
            body = restClient.get().uri(path).retrieve().body(type);
        } catch (RestClientException e) {
            throw new UpstreamApiException("Failed to reach exchange rate API", e);
        }
        if (body == null) {
            throw new UpstreamApiException("Empty response from exchange rate API");
        }
        return body;
    }

    private void requireSuccess(String result, String errorType) {
        if (!"success".equals(result)) {
            throw new UpstreamApiException("Exchange rate API error: "
                    + (errorType != null ? errorType : "unknown"));
        }
    }
}