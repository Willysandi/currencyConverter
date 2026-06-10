package com;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Scanner;

public class CurrencyConverter {

    private static final String API_BASE_URL = "https://v6.exchangerate-api.com/v6/";

    public static void main(String[] args) throws IOException {

        Dotenv dotenv = Dotenv.load();
        String apiKey = dotenv.get("EXCHANGE_RATE_API_KEY");

        Scanner scan = new Scanner(System.in);
        System.out.println("Type Currency");
        String currencyType = scan.nextLine();
        System.out.println("Convert To");
        String convertTo = scan.nextLine();
        System.out.println("Input Value");
        BigDecimal inputValue = scan.nextBigDecimal();

        String urlString = buildUrl(apiKey, currencyType);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(urlString)
                .get()
                .build();

        Response response = client.newCall(request).execute();
        String stringResponse = response.body().string();

        BigDecimal rate = extractRate(stringResponse, convertTo);
        BigDecimal result = convert(rate, inputValue);
        System.out.println(formatResult(convertTo, result));
    }


    static String buildUrl(String apiKey, String currencyType) {
        return API_BASE_URL + apiKey + "/latest/" + currencyType.toUpperCase();
    }

    static BigDecimal convert(BigDecimal rate, BigDecimal inputValue) {
        return rate.multiply(inputValue).setScale(2, RoundingMode.HALF_UP);
    }


    static String formatResult(String convertTo, BigDecimal result) {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return convertTo.toUpperCase() + " " + formatter.format(result);
    }


    static BigDecimal extractRate(String jsonResponse, String convertTo) {
        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONObject ratesObject = jsonObject.getJSONObject("conversion_rates");
        return ratesObject.getBigDecimal(convertTo.toUpperCase());
    }
}
