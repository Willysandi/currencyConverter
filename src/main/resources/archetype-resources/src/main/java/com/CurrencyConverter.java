package com;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Scanner;

public class CurrencyConverter {

    public static void main(String[] args) throws IOException {

        Scanner scan = new Scanner(System.in);
        System.out.println("Type Currency");
        String currencyType = scan.nextLine();
        System.out.println("Convert To");
        String convertTo = scan.nextLine();
        System.out.println("Input Value");
        BigDecimal inputValue = scan.nextBigDecimal();

        String urlString = "https://v6.exchangerate-api.com/v6/d937454a7bb1f0cf8cc0fa68/latest/" + currencyType.toUpperCase();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(urlString)
                .get()
                .build();

        Response response = client.newCall(request).execute();
        String stringResponse = response.body().string();
        JSONObject jsonObject = new JSONObject(stringResponse);
        JSONObject ratesObject = jsonObject.getJSONObject("conversion_rates");
        BigDecimal rate = ratesObject.getBigDecimal(convertTo.toUpperCase());

        BigDecimal result = rate.multiply(inputValue);
        System.out.println(result);
    }

}