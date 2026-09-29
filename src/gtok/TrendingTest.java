/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gtok;

/**
 *
 * @author gdessin
 */
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.http.HttpResponse.BodyHandlers;

public class TrendingTest {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("SCRAPE_CREATORS_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable SCRAPE_CREATORS_API_KEY is missing."
            );
        }

        HttpClient client = HttpClient.newHttpClient();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.scrapecreators.com/v1/tiktok/get-trending-feed?"))
            .header("x-api-key", apiKey)
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        
        System.out.println(response.body());
    }
}