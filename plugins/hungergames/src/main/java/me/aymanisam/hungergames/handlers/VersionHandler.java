package me.aymanisam.hungergames.handlers;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class VersionHandler {
    private static final String SPIGET_API_URL = "https://api.spiget.org/v2/resources/%d/versions/latest";

    public static String getLatestPluginVersion(int pluginId) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(SPIGET_API_URL, pluginId)))
		        .timeout(Duration.ofSeconds(5))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONParser parser = new JSONParser();
                Object parsed = parser.parse(response.body());
                if (parsed instanceof JSONObject jsonResponse && jsonResponse.get("name") instanceof String version
                        && !version.isBlank()) return version;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | ParseException e) {
            return null;
        }
        return null;
    }
}
