package com;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

public class CurrencyConverter {

    private static final String API_BASE_URL = "https://v6.exchangerate-api.com/v6/";

    public static void main(String[] args) throws IOException {

        Dotenv dotenv = Dotenv.load();
        String apiKey = dotenv.get("EXCHANGE_RATE_API_KEY");

        Set<String> supportedCodes = fetchSupportedCodes(apiKey);

        String currencyType;
        String convertTo;
        BigDecimal inputValue;

        try (Scanner scan = new Scanner(System.in)) {
            do {
                System.out.println("Type Currency");
                currencyType = scan.nextLine().trim();
                if (!isValidCurrency(currencyType))
                    System.out.println("Invalid currency. Enter exactly 3 letters (e.g. USD).");
                else if (!isSupportedCurrency(currencyType, supportedCodes))
                    System.out.println("Currency not supported. Try a valid code (e.g. USD, EUR, GBP).");
            } while (!isValidCurrency(currencyType) || !isSupportedCurrency(currencyType, supportedCodes));

            do {
                System.out.println("Convert To");
                convertTo = scan.nextLine().trim();
                if (!isValidCurrency(convertTo))
                    System.out.println("Invalid currency. Enter exactly 3 letters (e.g. EUR).");
                else if (!isSupportedCurrency(convertTo, supportedCodes))
                    System.out.println("Currency not supported. Try a valid code (e.g. USD, EUR, GBP).");
            } while (!isValidCurrency(convertTo) || !isSupportedCurrency(convertTo, supportedCodes));

            String rawAmount;
            do {
                System.out.println("Input Value");
                rawAmount = scan.nextLine().trim();
                if (!isValidAmount(rawAmount))
                    System.out.println("Invalid amount. Enter a positive number (e.g. 100.50).");
            } while (!isValidAmount(rawAmount));
            inputValue = new BigDecimal(rawAmount);
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(buildUrl(apiKey, currencyType))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful())
                throw new IOException("Unexpected HTTP status: " + response.code());

            ResponseBody body = response.body();
            if (body == null)
                throw new IOException("Empty response body");

            BigDecimal rate = extractRate(body.string(), convertTo);
            BigDecimal result = convert(rate, inputValue);
            System.out.println(formatResult(convertTo, result));
        }
    }

    // ── Input validation ──────────────────────────────────────────────────────

    static boolean isValidCurrency(String input) {
        return input != null && input.length() == 3 && input.chars().allMatch(Character::isLetter);
    }

    static boolean isSupportedCurrency(String input, Set<String> codes) {
        return input != null && codes.contains(input.toUpperCase());
    }

    static boolean isValidAmount(String input) {
        if (input == null || input.isBlank()) return false;
        try {
            return new BigDecimal(input.trim()).compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    static String buildUrl(String apiKey, String currencyType) {
        return API_BASE_URL + apiKey + "/latest/" + currencyType.toUpperCase();
    }

    static Set<String> fetchSupportedCodes(String apiKey) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(API_BASE_URL + apiKey + "/codes")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            JSONArray codes = getObjects(response);
            Set<String> result = new HashSet<>();
            for (int i = 0; i < codes.length(); i++)
                result.add(codes.getJSONArray(i).getString(0));
            return result;
        }
    }

    private static JSONArray getObjects(Response response) throws IOException {
        if (!response.isSuccessful())
            throw new IOException("Unexpected HTTP status: " + response.code());
        ResponseBody body = response.body();
        if (body == null)
            throw new IOException("Empty response body");
        JSONObject json = new JSONObject(body.string());
        if (!"success".equals(json.getString("result")))
            throw new IllegalStateException("API error: " + json.optString("error-type", "unknown"));
        return json.getJSONArray("supported_codes");
    }

    static BigDecimal extractRate(String jsonResponse, String convertTo) {
        JSONObject jsonObject = new JSONObject(jsonResponse);
        if (!"success".equals(jsonObject.getString("result")))
            throw new IllegalStateException(
                    "API error: " + jsonObject.optString("error-type", "unknown"));
        JSONObject ratesObject = jsonObject.getJSONObject("conversion_rates");
        return ratesObject.getBigDecimal(convertTo.toUpperCase());
    }

    // ── Math / formatting ─────────────────────────────────────────────────────

    static BigDecimal convert(BigDecimal rate, BigDecimal inputValue) {
        return rate.multiply(inputValue).setScale(2, RoundingMode.HALF_UP);
    }

    static String formatResult(String convertTo, BigDecimal result) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return convertTo.toUpperCase() + " " + formatter.format(result);
    }
}
