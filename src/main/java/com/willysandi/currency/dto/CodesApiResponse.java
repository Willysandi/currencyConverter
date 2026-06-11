package com.willysandi.currency.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CodesApiResponse(String result,
                               @JsonProperty("supported_codes") List<List<String>> supportedCodes,
                               @JsonProperty("error-type") String errorType) {
}